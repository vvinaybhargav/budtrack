package com.vinay.fintrack

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.vinay.fintrack.data.Account
import com.vinay.fintrack.data.Card
import com.vinay.fintrack.data.ChatMessage
import com.vinay.fintrack.data.Entry
import com.vinay.fintrack.data.FirestoreSync
import com.vinay.fintrack.data.INVEST_CATEGORIES
import com.vinay.fintrack.data.Loan
import com.vinay.fintrack.data.PersistedState
import com.vinay.fintrack.data.SAVINGS_CATEGORIES
import com.vinay.fintrack.data.SmsImporter
import com.vinay.fintrack.data.Store
import com.vinay.fintrack.data.SyncStatus
import com.vinay.fintrack.data.parseFirebaseConfig
import com.vinay.fintrack.data.Txn
import com.vinay.fintrack.data.currentPeriod
import com.vinay.fintrack.data.inr
import com.vinay.fintrack.data.isoFromDayFirst
import com.vinay.fintrack.data.newId
import com.vinay.fintrack.data.today
import com.vinay.fintrack.data.todayDayFirst
import com.vinay.fintrack.data.ownerLabel
import com.vinay.fintrack.data.parseSmartAdd

enum class Tab { HOME, ENTRIES, ADD, SETTINGS }

data class Draft(
    val person: String = "Me",
    val type: String = "EXPENSE",
    val category: String = "Other",
    val amountText: String = "",
    val frequency: String = "MONTHLY",
    val note: String = "",
    val accountId: String = "",
    /** Months between payments, 1–12. Twelve is the old "annual". */
    val periodMonths: Int = 1
) {
    /**
     * Derived, never stored. As its own field it defaulted to JOINT and stayed
     * there while the form showed a profile name, so a payment marked "Me" was
     * filed against the joint account. It cannot drift from [person] now.
     */
    val bucket: String get() = if (person == "Joint") "JOINT" else "PERSONAL"
}

data class NewLoanDraft(
    val name: String = "", val person: String = "Me", val emiText: String = "",
    val totalMonthsText: String = "", val remainingMonthsText: String = "",
    /** Account the EMI is debited from — asked once here, never at confirm time. */
    val accountId: String = ""
)

data class NewAccountDraft(
    val name: String = "", val owner: String = "Me", val balanceText: String = "",
    /** Last digits as the bank's SMS writes them, for matching imports. */
    val numberTail: String = ""
)

data class NewCardDraft(
    val name: String = "", val owner: String = "Me", val limitText: String = "",
    val balanceText: String = "", val minDueText: String = "", val due: String = ""
)

class FinTrackViewModel(app: Application) : AndroidViewModel(app) {

    private val appContext = app.applicationContext
    private val store = Store(app)
    private var persisted by mutableStateOf(store.load())

    private val sync = FirestoreSync(app.applicationContext)

    var syncStatus by mutableStateOf(SyncStatus.OFF); private set
    var syncError by mutableStateOf(""); private set

    /** Revision this ViewModel last wrote, so its own saves don't look like
     *  someone else's and cause a pointless reload. */
    private var ownRevision = store.revision()

    private val storeWatcher = store.observe {
        // The SMS receiver writes on its own thread while the app may be open.
        // Without this its import is lost the next time the ViewModel saves.
        if (store.revision() != ownRevision) {
            android.os.Handler(android.os.Looper.getMainLooper()).post { refreshFromDisk() }
        }
    }

    init {
        sync.onStatusChange = { s, e -> syncStatus = s; syncError = e }
        sync.onTxns = { remote ->
            // Anything local the server hasn't seen — recorded by the SMS
            // receiver while offline, say — is pushed rather than dropped.
            val remoteIds = remote.map { it.id }.toSet()
            val localOnly = persisted.txns.filterNot { it.id in remoteIds }
            persisted = persisted.copy(txns = remote + localOnly).also { ownRevision = store.save(it) }
            localOnly.forEach { sync.upsertTxn(it) }
            syncedAt = System.currentTimeMillis()
        }
        sync.onTxnsMissing = { sync.pushAllTxns(persisted.txns) }
        // Nothing that touches state is started here: the session properties
        // below aren't initialised yet, and update() reads activeProfile.
        // See the init block at the end of the class.
    }

    /**
     * One-off payments used to be saved as entries. They are transactions now,
     * and since they no longer belong in the commitments list, leaving them as
     * entries would make them invisible and impossible to delete — so convert
     * them once, keeping the money rather than dropping it.
     */
    private fun migrateOneTimeEntries() {
        val stale = persisted.entries.filter { it.frequency == "ONE_TIME" }
        if (stale.isEmpty()) return
        val converted = stale.map { e ->
            val credit = e.type == "INCOME"
            // Follow the entry's own bucket: a Personal one-off belongs on that
            // person's account, not on the joint default.
            val account = defaultAccountFor(e.person, e.bucket)
            Txn(
                id = newId("t"),
                date = today(),
                kind = if (credit) "INCOME" else "EXPENSE",
                amount = e.amount,
                category = e.category,
                fromAccountId = if (credit) "" else account,
                toAccountId = if (credit) account else "",
                period = currentPeriod(),
                note = e.note.ifEmpty { e.category }
            )
        }
        update { s ->
            s.copy(
                entries = s.entries.filterNot { it.frequency == "ONE_TIME" },
                txns = s.txns + converted
            )
        }
        converted.forEach { sync.upsertTxn(it) }
    }

    private fun connectSync() {
        sync.connect(
            persisted.firebaseConfigText,
            onRemote = { remote, remoteUpdatedAt ->
                if (remoteUpdatedAt < persisted.localUpdatedAt) {
                    // This device has newer edits that never reached the server —
                    // added while offline, say. Send them rather than losing them.
                    sync.push(sharable(persisted), activeProfile ?: "", force = true)
                } else {
                    persisted = mergeRemote(remote).also { ownRevision = store.save(it) }
                }
                syncedAt = System.currentTimeMillis()
            },
            // Nothing up there yet — seed it from this device so the console
            // isn't empty after a first connect.
            onEmptyRemote = { pushNow() }
        )
    }

    var syncedAt by mutableStateOf(0L); private set

    /** Anonymous user id of this device, for the members list in the rules. */
    val syncDeviceId: String get() = sync.uid

    /** Why anonymous sign-in didn't happen — informational, not a failure. */
    val syncAuthNote: String get() = sync.authNote

    // ── SMS import ─────────────────────────────────────────────────────
    private val smsImporter = SmsImporter(app.applicationContext)

    var scanning by mutableStateOf(false); private set
    var scanNote by mutableStateOf(""); private set

    val smsImportOn: Boolean get() = persisted.smsImportOn
    val importedCount: Int get() = persisted.txns.count { it.source.startsWith("sms") }

    /** Newest first: what the importer did with each recent message. */
    val smsLog: List<String> get() = persisted.smsLog

    fun setSmsImport(on: Boolean) {
        update { it.copy(smsImportOn = on) }
        scanNote = if (on) {
            "On. Bank alerts will be recorded as they arrive."
        } else {
            "SMS import off."
        }
    }

    /**
     * Reads the inbox for older messages. Runs off the main thread, then picks
     * up whatever the importer wrote and sends the new transactions up.
     */
    fun backfillSms(days: Int = 60) {
        if (scanning) return
        scanning = true
        scanNote = "Reading bank messages…"
        Thread {
            val count = runCatching { smsImporter.backfill(days) }.getOrNull()
            val reloaded = store.load()
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                adoptFromDisk(reloaded)
                scanning = false
                scanNote = when {
                    count == null -> "Couldn't read messages — check the SMS permission."
                    count == 0 -> "No new bank messages found."
                    else -> "Added $count transaction(s)."
                }
            }
        }.start()
    }

    /**
     * Takes on state written behind the app's back — by the SMS receiver while
     * closed, or a backfill on another thread — and syncs anything new.
     */
    private fun adoptFromDisk(disk: PersistedState) {
        val known = persisted.txns.map { it.id }.toSet()
        persisted = disk.copy(
            firebaseConfigText = persisted.firebaseConfigText,
            openaiKeyText = persisted.openaiKeyText
        )
        disk.txns.filterNot { it.id in known }.forEach { sync.upsertTxn(it) }
    }

    /** Called when the app returns to the foreground, since the receiver may
     *  have recorded payments while it was closed. */
    fun refreshFromDisk() = adoptFromDisk(store.load())

    fun pushNow() {
        sync.push(sharable(persisted), activeProfile ?: "")
        sync.pushAllTxns(persisted.txns)
        syncedAt = System.currentTimeMillis()
    }

    /**
     * What belongs in the shared household document.
     *
     * Emptied rather than omitted: writes use SetOptions.merge(), so a field
     * left out keeps whatever is already up there — sending empties actively
     * clears anything an earlier build uploaded.
     *
     * PINs must never leave the device, and the SMS log holds excerpts of
     * messages including OTPs. Neither is household data, and the household
     * document is readable by anyone with the project id.
     */
    private fun sharable(s: PersistedState) = s.copy(
        txns = emptyList(),
        firebaseConfigText = "",
        openaiKeyText = "",
        profiles = emptyMap(),
        smsLog = emptyList(),
        importedRefs = emptySet(),
        lastSmsScan = 0L,
        lastProfile = ""
    )

    /** Remote state with this device's own fields kept — the mirror of
     *  [sharable], so nothing it withholds gets wiped when a snapshot lands. */
    private fun mergeRemote(remote: PersistedState) = remote.copy(
        txns = persisted.txns,
        localUpdatedAt = persisted.localUpdatedAt,
        firebaseConfigText = persisted.firebaseConfigText,
        openaiKeyText = persisted.openaiKeyText,
        profiles = persisted.profiles,
        smsLog = persisted.smsLog,
        importedRefs = persisted.importedRefs,
        lastSmsScan = persisted.lastSmsScan,
        smsImportOn = persisted.smsImportOn,
        lastProfile = persisted.lastProfile
    )

    override fun onCleared() {
        store.stopObserving(storeWatcher)
        sync.disconnect()
        super.onCleared()
    }

    private fun update(block: (PersistedState) -> PersistedState) {
        // Stamped on every change so an offline edit can be told apart from a
        // stale server copy when the two meet.
        persisted = block(persisted)
            .copy(localUpdatedAt = System.currentTimeMillis())
            .also { ownRevision = store.save(it) }
        sync.push(sharable(persisted), activeProfile ?: "")
    }

    // ── persisted views ────────────────────────────────────────────────
    val entries: List<Entry> get() = persisted.entries
    val accounts: List<Account> get() = persisted.accounts
    val loans: List<Loan> get() = persisted.loans
    val cards: List<Card> get() = persisted.cards
    val categories: List<String> get() = persisted.categories
    val defaultAccount: String get() = persisted.defaultAccount
    val firebaseConfigText: String get() = persisted.firebaseConfigText
    val openaiKeyText: String get() = persisted.openaiKeyText

    // ── session UI state ───────────────────────────────────────────────
    /**
     * The profile this phone signs in as. Set in the declarations rather than
     * an init block, which would run before these delegates exist.
     *
     * Only the picker is skipped — the PIN still stands. Repeating the choice
     * every launch on a phone that belongs to one person was the pointless part.
     */
    private val rememberedProfile: String? =
        persisted.lastProfile.takeIf { it.isNotEmpty() && it in persisted.profiles.keys }

    // Straight in when this phone already knows whose it is, unless the PIN has
    // been asked for explicitly in Settings.
    var isLocked by mutableStateOf(rememberedProfile == null || persisted.askPinOnLaunch); private set
    var pinStep by mutableStateOf(if (rememberedProfile != null) "enter" else "pick"); private set
    var activeProfile by mutableStateOf(rememberedProfile); private set
    var pinInput by mutableStateOf(""); private set
    var pinError by mutableStateOf(false); private set

    var tab by mutableStateOf(Tab.HOME)
    var bucketView by mutableStateOf("JOINT")
    var balanceHidden by mutableStateOf(false); private set
    var expandedLoan by mutableStateOf<String?>(null); private set

    var homeQuickText by mutableStateOf("")
    var homeQuickConfirm by mutableStateOf("")

    var entriesSearch by mutableStateOf("")
    var entriesCategoryFilter by mutableStateOf<String?>(null)

    var editingEntryId by mutableStateOf<String?>(null); private set
    var draft by mutableStateOf(Draft())
    var addKind by mutableStateOf("ONE_TIME"); private set
    var smartText by mutableStateOf("")
    var chatMessages by mutableStateOf(
        listOf(ChatMessage("assistant", "Hi! Tell me what to add — e.g. \"22k EMI\" or \"4500 wife music class\"."))
    ); private set

    var newLoanDraft by mutableStateOf(NewLoanDraft())
    var newAccountDraft by mutableStateOf(NewAccountDraft())
    var newCardDraft by mutableStateOf(NewCardDraft())

    var editingAccountId by mutableStateOf<String?>(null); private set
    var accountDraft by mutableStateOf(NewAccountDraft())
    var editingLoanId by mutableStateOf<String?>(null); private set
    var loanDraft by mutableStateOf(NewLoanDraft())
    var editingCardId by mutableStateOf<String?>(null); private set
    var cardDraft by mutableStateOf(NewCardDraft())

    var newCategoryText by mutableStateOf("")
    var editingCategory by mutableStateOf<String?>(null); private set
    var categoryDraftText by mutableStateOf("")

    var pinNew by mutableStateOf("")
    var pinConfirm by mutableStateOf("")
    var pinMsg by mutableStateOf(""); private set
    var pinMsgIsError by mutableStateOf(false); private set

    // ── lock screen ────────────────────────────────────────────────────
    val profileNames: List<String> get() = persisted.profiles.keys.toList()

    fun pickProfile(name: String) {
        activeProfile = name; pinStep = "enter"; pinInput = ""; pinError = false
    }

    fun backToPick() {
        pinStep = "pick"; pinInput = ""; pinError = false
    }

    fun pressDigit(d: String) {
        if (pinInput.length >= 4) return
        pinInput += d
        pinError = false
        if (pinInput.length == 4) {
            if (pinInput == persisted.profiles[activeProfile]) {
                isLocked = false
                draft = Draft(person = activeProfile ?: "Me")
                // Remembered only after a correct PIN, so a mistaken pick on the
                // shared picker doesn't stick.
                activeProfile?.let { p -> update { it.copy(lastProfile = p) } }
            } else {
                pinInput = ""; pinError = true
            }
        }
    }

    fun pressBackspace() {
        pinInput = pinInput.dropLast(1); pinError = false
    }

    fun switchProfile() {
        isLocked = true; pinStep = "pick"; activeProfile = null; tab = Tab.HOME
        // Deliberately switching means the picker should come back next launch.
        update { it.copy(lastProfile = "") }
    }

    fun savePin() {
        if (pinNew.length != 4) { pinMsg = "PIN must be 4 digits."; pinMsgIsError = true; return }
        if (pinNew != pinConfirm) { pinMsg = "PINs don't match."; pinMsgIsError = true; return }
        val p = activeProfile ?: return
        update { it.copy(profiles = it.profiles + (p to pinNew)) }
        pinMsg = "PIN updated."; pinMsgIsError = false; pinNew = ""; pinConfirm = ""
    }

    // ── profiles ───────────────────────────────────────────────────────
    // Profiles hold PINs, which are deliberately never synced, so this list is
    // per device: a new profile has to be added on each phone that will use it.
    var newProfileName by mutableStateOf("")
    var newProfilePin by mutableStateOf("")
    var profileMsg by mutableStateOf(""); private set

    fun setNewProfileName(v: String) { newProfileName = v.take(20); profileMsg = "" }
    fun setNewProfilePin(v: String) { newProfilePin = v.filter { it.isDigit() }.take(4); profileMsg = "" }

    fun addProfile() {
        val name = newProfileName.trim()
        when {
            name.isEmpty() -> profileMsg = "Give the profile a name."
            name in persisted.profiles.keys -> profileMsg = "That profile already exists."
            newProfilePin.length != 4 -> profileMsg = "PIN must be 4 digits."
            else -> {
                update { it.copy(profiles = it.profiles + (name to newProfilePin)) }
                newProfileName = ""; newProfilePin = ""
                profileMsg = "Added $name. Add it on the other phone too — PINs never sync."
            }
        }
    }

    fun removeProfile(name: String) {
        when {
            name == activeProfile -> profileMsg = "Can't remove the profile you're using."
            // Shared accounts and commitments are filed against it.
            name == "Joint" -> profileMsg = "Joint is shared and can't be removed."
            persisted.profiles.size <= 1 -> profileMsg = "Keep at least one profile."
            else -> {
                update { it.copy(profiles = it.profiles - name) }
                profileMsg = "Removed $name. Their entries and accounts are untouched."
            }
        }
    }

    fun setPinField(isNew: Boolean, v: String) {
        val clean = v.filter { it.isDigit() }.take(4)
        if (isNew) pinNew = clean else pinConfirm = clean
        pinMsg = ""
    }

    // ── home ───────────────────────────────────────────────────────────
    fun toggleBalanceVisible() { balanceHidden = !balanceHidden }
    fun toggleLoanDetail(id: String) { expandedLoan = if (expandedLoan == id) null else id }
    /**
     * Paying a card is a real payment: it asks which account, records the
     * transaction and clears the card's outstanding balance. Previously it only
     * set a flag, so the money never moved and the card never un-paid.
     */
    fun requestCardPayment(c: Card) {
        if (c.paid) {
            // Undo: drop the payment and restore what it cleared.
            val paidTxn = persisted.txns.firstOrNull { it.cardId == c.id }
            removeTxns { it.cardId == c.id }
            update { s ->
                s.copy(cards = s.cards.map {
                    if (it.id == c.id) it.copy(paid = false, balance = paidTxn?.amount ?: it.balance)
                    else it
                })
            }
            return
        }
        if (c.balance <= 0) return
        pendingConfirm = PendingConfirm(
            title = c.name,
            amount = c.balance,
            kind = "EXPENSE",
            category = "Credit Card",
            cardId = c.id,
            fromAccountId = accountIdByName(defaultAccount)
        )
    }

    // ── confirming a commitment moves real money ───────────────────────
    /**
     * A confirm that still needs an account picked. Loan EMIs never produce one
     * — the loan already knows its account — while annual set-asides need both
     * sides, since the money is only moving between your own accounts.
     */
    data class PendingConfirm(
        val title: String,
        val amount: Double,
        val kind: String,              // EXPENSE | INCOME | TRANSFER
        val category: String,
        val entryId: String = "",
        val cardId: String = "",
        val fromAccountId: String = "",
        val toAccountId: String = ""
    ) {
        val needsFrom: Boolean get() = kind == "EXPENSE" || kind == "TRANSFER"
        val needsTo: Boolean get() = kind == "INCOME" || kind == "TRANSFER"
        val isReady: Boolean
            get() = (!needsFrom || fromAccountId.isNotEmpty()) &&
                (!needsTo || toAccountId.isNotEmpty()) &&
                (kind != "TRANSFER" || fromAccountId != toAccountId)
    }

    var pendingConfirm by mutableStateOf<PendingConfirm?>(null); private set

    fun isConfirmed(entryId: String): Boolean =
        persisted.txns.any { it.entryId == entryId && it.period == currentPeriod() }

    fun isLoanConfirmed(loanId: String): Boolean =
        persisted.txns.any { it.loanId == loanId && it.period == currentPeriod() }

    private fun accountIdByName(name: String): String =
        accounts.firstOrNull { it.name == name }?.id ?: accounts.firstOrNull()?.id.orEmpty()

    /**
     * The account a payment should land on given who it's for. Falling back to
     * the default account regardless put everything on the joint account, so a
     * payment marked Personal still showed up under Joint.
     */
    fun defaultAccountFor(person: String, bucket: String): String {
        if (bucket == "PERSONAL") {
            accounts.firstOrNull { it.person == person }?.let { return it.id }
        }
        accounts.firstOrNull { it.person == "Joint" }?.let { return it.id }
        return accountIdByName(defaultAccount)
    }

    private fun confirmKindFor(e: Entry): String = when {
        e.type == "INCOME" -> "INCOME"
        // Annual provisions and savings stay your money — they move, they aren't spent.
        // Any set-aside, not just yearly ones: a quarterly bill is put by a
        // month at a time exactly the same way.
        e.isSetAside || e.type == "SAVINGS" -> "TRANSFER"
        else -> "EXPENSE"
    }

    /** Tapping confirm: undo if already confirmed this month, otherwise open the sheet. */
    fun requestConfirm(e: Entry) {
        if (isConfirmed(e.id)) {
            removeTxns { it.entryId == e.id && it.period == currentPeriod() }
            return
        }
        val kind = confirmKindFor(e)
        pendingConfirm = PendingConfirm(
            title = e.note.ifEmpty { e.category },
            amount = e.monthly,
            kind = kind,
            category = e.category,
            entryId = e.id,
            // Starts from the account chosen when the entry was created, so a
            // personal expense doesn't default to the joint account.
            fromAccountId = if (kind == "INCOME") ""
            else e.accountId.ifEmpty { defaultAccountFor(e.person, e.bucket) },
            toAccountId = if (kind == "INCOME") e.accountId.ifEmpty { defaultAccountFor(e.person, e.bucket) } else ""
        )
    }

    /** Loan EMIs are paid from the account stored on the loan, so no sheet appears. */
    fun confirmLoan(l: Loan) {
        if (isLoanConfirmed(l.id)) {
            removeTxns { it.loanId == l.id && it.period == currentPeriod() }
            // Paying advanced the tenure, so undoing has to put the month back.
            update { s ->
                s.copy(loans = s.loans.map {
                    if (it.id == l.id) it.copy(
                        remainingMonths = minOf(it.totalMonths, it.remainingMonths + 1)
                    ) else it
                })
            }
            return
        }
        // EMIs aren't asked for an account, so fall back to the one implied by
        // whose loan it is.
        val from = l.accountId.ifEmpty { defaultAccountFor(l.person, "JOINT") }
        addTxn { seq ->
            Txn(
                id = seq, date = today(), kind = "EXPENSE", amount = l.monthlyEmi,
                category = "EMI", fromAccountId = from, loanId = l.id,
                period = currentPeriod(), note = l.name
            )
        }
        // Otherwise "42 of 84 months paid" never moved however often you paid.
        update { s ->
            s.copy(loans = s.loans.map {
                if (it.id == l.id) it.copy(remainingMonths = maxOf(0, it.remainingMonths - 1)) else it
            })
        }
    }

    /** Adds a transaction locally and as its own Firestore document. */
    private fun addTxn(build: (id: String) -> Txn) {
        val txn = build(newId("t"))
        update { s -> s.copy(txns = s.txns + txn) }
        sync.upsertTxn(txn)
    }

    private fun removeTxns(match: (Txn) -> Boolean) {
        val doomed = persisted.txns.filter(match)
        if (doomed.isEmpty()) return
        update { s -> s.copy(txns = s.txns.filterNot(match)) }
        doomed.forEach { sync.deleteTxn(it.id) }
    }

    fun setConfirmFrom(id: String) { pendingConfirm = pendingConfirm?.copy(fromAccountId = id) }
    fun setConfirmTo(id: String) { pendingConfirm = pendingConfirm?.copy(toAccountId = id) }
    fun cancelConfirm() { pendingConfirm = null }

    fun commitConfirm() {
        val p = pendingConfirm ?: return
        if (!p.isReady) return
        addTxn { seq ->
            Txn(
                id = seq, date = today(), kind = p.kind, amount = p.amount,
                category = p.category,
                fromAccountId = if (p.needsFrom) p.fromAccountId else "",
                toAccountId = if (p.needsTo) p.toAccountId else "",
                entryId = p.entryId, cardId = p.cardId,
                period = currentPeriod(), note = p.title
            )
        }
        if (p.cardId.isNotEmpty()) {
            update { s ->
                s.copy(cards = s.cards.map {
                    if (it.id == p.cardId) it.copy(paid = true, balance = 0.0) else it
                })
            }
        }
        pendingConfirm = null
    }

    fun quickAddFromHome() {
        val text = homeQuickText.trim()
        if (text.isEmpty()) return
        val parsed = parseSmartAdd(text, categories)
        if (parsed.amount <= 0) {
            homeQuickConfirm = "Couldn't find an amount — try e.g. '22k EMI'."
            return
        }
        update { s ->
            s.copy(
                entries = s.entries + Entry(
                    newId("e"), parsed.person, parsed.type, parsed.bucket,
                    parsed.category, parsed.amount, parsed.frequency, parsed.note,
                    defaultAccountFor(parsed.person, parsed.bucket)
                )
            )
        }
        homeQuickText = ""
        val per = if (parsed.frequency == "ANNUAL") "a year" else "a month"
        homeQuickConfirm =
            "Added ${inr(parsed.amount)} $per · ${parsed.category} · ${parsed.person}"
    }

    // ── entries ────────────────────────────────────────────────────────
    fun deleteEntry(id: String) = update { s -> s.copy(entries = s.entries.filterNot { it.id == id }) }

    fun openEditEntry(e: Entry) {
        editingEntryId = e.id
        tab = Tab.ADD
        draft = Draft(
            e.person, e.type, e.category,
            e.amount.toLong().toString(), e.frequency, e.note, e.accountId, e.everyMonths
        )
    }

    fun cancelEdit() {
        editingEntryId = null
        draft = Draft(person = activeProfile ?: "Me")
        addKind = "ONE_TIME"
    }

    fun setCategoryFilter(c: String?) {
        entriesCategoryFilter = if (entriesCategoryFilter == c) null else c
    }

    // ── add ────────────────────────────────────────────────────────────
    fun selectAddKind(k: String) {
        addKind = k
        val p = activeProfile ?: "Me"
        when (k) {
            "RECURRING" -> draft = Draft(person = p, type = "EXPENSE", frequency = "MONTHLY")
            "INVESTMENT" -> draft = Draft(person = p, type = "SAVINGS", category = "LIC", frequency = "MONTHLY")
            "ONE_TIME" -> draft = Draft(person = p, type = "EXPENSE", frequency = "ONE_TIME")
            "EMI_LOAN" -> newLoanDraft = NewLoanDraft(person = p)
            "BANK_ACCOUNT" -> newAccountDraft = NewAccountDraft(owner = p)
            "CREDIT_CARD" -> newCardDraft = NewCardDraft(owner = p)
        }
    }

    fun parseSmart() {
        val text = smartText
        if (text.isEmpty()) return
        val parsed = parseSmartAdd(text, categories)
        draft = Draft(
            parsed.person, parsed.type, parsed.bucket, parsed.category,
            if (parsed.amount > 0) parsed.amount.toLong().toString() else "",
            parsed.frequency, parsed.note
        )
        smartText = ""
        chatMessages = chatMessages + ChatMessage("user", text) + ChatMessage(
            "assistant",
            "Got it — ${inr(parsed.amount)} for ${parsed.category}, ${parsed.frequency.lowercase()}, " +
                "under ${parsed.person}. Check the form below and tap Save."
        )
    }

    var oneOffAccountId by mutableStateOf("")
    var oneOffIsCredit by mutableStateOf(false)

    /** Day-first as written here. Defaults to today so the common case is a
     *  field you never touch, but a payment from last week can be recorded. */
    var oneOffDateText by mutableStateOf(todayDayFirst())

    val oneOffDateValid: Boolean get() = isoFromDayFirst(oneOffDateText) != null

    val todayDayFirstText: String get() = todayDayFirst()

    fun setOneOffAccount(id: String) { oneOffAccountId = id }

    /**
     * A one-off is a payment that already happened, so it becomes a transaction
     * rather than an entry. As an entry it sat on Home asking to be confirmed
     * every month, and never appeared in Transactions at all.
     */
    /** Never blank: falls back to the account implied by who it's for. */
    val draftAccountName: String
        get() = accounts.firstOrNull {
            it.id == draft.accountId.ifEmpty { defaultAccountFor(draft.person, draft.bucket) }
        }?.name.orEmpty()

    /** Resolved account for the one-off form, so the picker is never blank and
     *  the fallback follows the Personal/Joint choice. */
    val resolvedOneOffAccount: String
        get() = oneOffAccountId.ifEmpty { defaultAccountFor(draft.person, draft.bucket) }

    val oneOffAccountName: String
        get() = accounts.firstOrNull { it.id == resolvedOneOffAccount }?.name.orEmpty()

    private fun saveOneOff(amount: Double) {
        val account = resolvedOneOffAccount
        val date = isoFromDayFirst(oneOffDateText) ?: today()
        addTxn { id ->
            Txn(
                id = id,
                date = date,
                kind = if (oneOffIsCredit) "INCOME" else "EXPENSE",
                amount = amount,
                category = draft.category,
                fromAccountId = if (oneOffIsCredit) "" else account,
                toAccountId = if (oneOffIsCredit) account else "",
                // The month it happened in, not the month you typed it in, so a
                // backdated payment counts against the right budget.
                period = date.take(7),
                note = draft.note.ifEmpty { draft.category }
            )
        }
        draft = Draft(person = activeProfile ?: "Me")
        oneOffAccountId = ""
        oneOffIsCredit = false
        oneOffDateText = todayDayFirst()
        smartText = ""
        tab = Tab.ENTRIES
    }

    fun saveDraft() {
        val amount = draft.amountText.toDoubleOrNull() ?: return
        if (amount <= 0) return
        if (editingEntryId == null && addKind == "ONE_TIME") {
            saveOneOff(amount)
            return
        }
        val editingId = editingEntryId
        update { s ->
            val entry = Entry(
                editingId ?: newId("e"), draft.person, draft.type, draft.bucket,
                draft.category, amount,
                // Kept for older readers; the period is what actually counts.
                if (draft.periodMonths >= 12) "ANNUAL" else "MONTHLY",
                draft.note,
                draft.accountId.ifEmpty { defaultAccountFor(draft.person, draft.bucket) },
                draft.periodMonths
            )
            if (editingId != null) {
                s.copy(entries = s.entries.map { if (it.id == entry.id) entry else it })
            } else {
                s.copy(entries = s.entries + entry)
            }
        }
        editingEntryId = null
        smartText = ""
        draft = Draft(person = activeProfile ?: "Me")
        // Home, not Transactions: an entry is the plan, and Transactions now
        // lists recorded movements only — landing there looked like a failed save.
        tab = Tab.HOME
    }

    fun addNewLoan() {
        val emi = newLoanDraft.emiText.toDoubleOrNull() ?: return
        val total = newLoanDraft.totalMonthsText.toIntOrNull() ?: return
        if (newLoanDraft.name.isBlank() || emi <= 0 || total <= 0) return
        val remaining = newLoanDraft.remainingMonthsText.toIntOrNull() ?: total
        update { s ->
            s.copy(
                loans = s.loans + Loan(
                    newId("l"), newLoanDraft.name, newLoanDraft.person, emi, total, remaining,
                    newLoanDraft.accountId.ifEmpty { accountIdByName(defaultAccount) }
                )
            )
        }
        newLoanDraft = NewLoanDraft(person = activeProfile ?: "Me")
        tab = Tab.HOME
    }

    fun addNewAccount() {
        if (newAccountDraft.name.isBlank()) return
        update { s ->
            s.copy(
                accounts = s.accounts + Account(
                    newId("a"), newAccountDraft.name, ownerLabel(newAccountDraft.owner),
                    newAccountDraft.owner, newAccountDraft.balanceText.toDoubleOrNull() ?: 0.0,
                    newAccountDraft.numberTail
                )
            )
        }
        newAccountDraft = NewAccountDraft(owner = activeProfile ?: "Me")
        tab = Tab.HOME
    }

    fun addNewCard() {
        val limit = newCardDraft.limitText.toDoubleOrNull() ?: return
        if (newCardDraft.name.isBlank() || limit <= 0) return
        update { s ->
            s.copy(
                cards = s.cards + Card(
                    newId("cc"), newCardDraft.name, newCardDraft.owner, limit,
                    newCardDraft.balanceText.toDoubleOrNull() ?: 0.0,
                    newCardDraft.minDueText.toDoubleOrNull() ?: 0.0,
                    newCardDraft.due
                )
            )
        }
        newCardDraft = NewCardDraft(owner = activeProfile ?: "Me")
        tab = Tab.HOME
    }

    // ── inline editors ─────────────────────────────────────────────────
    fun startEditAccount(a: Account) {
        editingAccountId = a.id
        accountDraft = NewAccountDraft(
            a.name, a.owner, a.openingBalance.toLong().toString(), a.numberTail
        )
    }

    fun cancelEditAccount() { editingAccountId = null }

    fun saveAccount() {
        val id = editingAccountId ?: return
        update { s ->
            s.copy(accounts = s.accounts.map {
                if (it.id == id) it.copy(
                    name = accountDraft.name, owner = accountDraft.owner,
                    openingBalance = accountDraft.balanceText.toDoubleOrNull() ?: 0.0,
                    numberTail = accountDraft.numberTail
                ) else it
            })
        }
        editingAccountId = null
    }

    /**
     * Transactions are moved to another account rather than left pointing at a
     * deleted one: an orphan drops out of every balance and shows under neither
     * bucket, so the money silently disappears while the record remains.
     */
    fun deleteAccount(id: String) {
        val fallback = accounts.firstOrNull { it.id != id && it.name == defaultAccount }?.id
            ?: accounts.firstOrNull { it.id != id }?.id.orEmpty()
        val moved = persisted.txns
            .filter { it.fromAccountId == id || it.toAccountId == id }
            .map {
                it.copy(
                    fromAccountId = if (it.fromAccountId == id) fallback else it.fromAccountId,
                    toAccountId = if (it.toAccountId == id) fallback else it.toAccountId
                )
            }
        val movedById = moved.associateBy { it.id }
        update { s ->
            s.copy(
                accounts = s.accounts.filterNot { it.id == id },
                txns = s.txns.map { movedById[it.id] ?: it },
                loans = s.loans.map { if (it.accountId == id) it.copy(accountId = fallback) else it }
            )
        }
        moved.forEach { sync.upsertTxn(it) }
        editingAccountId = null
    }

    fun startEditLoan(l: Loan) {
        editingLoanId = l.id
        loanDraft = NewLoanDraft(
            l.name, l.person, l.monthlyEmi.toLong().toString(),
            l.totalMonths.toString(), l.remainingMonths.toString(), l.accountId
        )
    }

    fun cancelEditLoan() { editingLoanId = null }

    fun saveLoan() {
        val id = editingLoanId ?: return
        update { s ->
            s.copy(loans = s.loans.map {
                if (it.id == id) it.copy(
                    name = loanDraft.name, person = loanDraft.person,
                    monthlyEmi = loanDraft.emiText.toDoubleOrNull() ?: 0.0,
                    totalMonths = loanDraft.totalMonthsText.toIntOrNull() ?: 1,
                    remainingMonths = loanDraft.remainingMonthsText.toIntOrNull() ?: 0,
                    accountId = loanDraft.accountId
                ) else it
            })
        }
        editingLoanId = null
    }

    fun deleteLoan(id: String) {
        update { s -> s.copy(loans = s.loans.filterNot { it.id == id }) }
        editingLoanId = null
    }

    fun startEditCard(c: Card) {
        editingCardId = c.id
        cardDraft = NewCardDraft(
            c.name, c.owner, c.limit.toLong().toString(), c.balance.toLong().toString(),
            c.minDue.toLong().toString(), c.due
        )
    }

    fun cancelEditCard() { editingCardId = null }

    fun saveCard() {
        val id = editingCardId ?: return
        update { s ->
            s.copy(cards = s.cards.map {
                if (it.id == id) it.copy(
                    name = cardDraft.name, owner = cardDraft.owner,
                    limit = cardDraft.limitText.toDoubleOrNull() ?: 0.0,
                    balance = cardDraft.balanceText.toDoubleOrNull() ?: 0.0,
                    minDue = cardDraft.minDueText.toDoubleOrNull() ?: 0.0,
                    due = cardDraft.due
                ) else it
            })
        }
        editingCardId = null
    }

    fun deleteCard(id: String) {
        update { s -> s.copy(cards = s.cards.filterNot { it.id == id }) }
        editingCardId = null
    }

    // ── settings ───────────────────────────────────────────────────────
    fun addCategory() {
        val t = newCategoryText.trim()
        if (t.isEmpty() || t in categories) return
        update { s -> s.copy(categories = s.categories + t) }
        newCategoryText = ""
    }

    fun removeCategory(cat: String) = update { s -> s.copy(categories = s.categories.filterNot { it == cat }) }

    fun moveCategory(index: Int, dir: Int) {
        val j = index + dir
        if (j < 0 || j >= categories.size) return
        update { s ->
            val arr = s.categories.toMutableList()
            val tmp = arr[index]; arr[index] = arr[j]; arr[j] = tmp
            s.copy(categories = arr)
        }
    }

    fun startEditCategory(cat: String) { editingCategory = cat; categoryDraftText = cat }

    fun saveCategory() {
        val old = editingCategory ?: return
        val t = categoryDraftText.trim()
        if (t.isEmpty()) return
        update { s ->
            s.copy(
                categories = s.categories.map { if (it == old) t else it },
                entries = s.entries.map { if (it.category == old) it.copy(category = t) else it }
            )
        }
        editingCategory = null; categoryDraftText = ""
    }

    val budgets: Map<String, Double> get() = persisted.budgets

    var budgetDraftCategory by mutableStateOf("")
    var budgetDraftAmount by mutableStateOf("")

    fun setBudget(cat: String, amount: Double) =
        update { s -> s.copy(budgets = s.budgets + (cat to amount)) }

    fun removeBudget(cat: String) = update { s -> s.copy(budgets = s.budgets - cat) }

    fun addBudgetFromDraft() {
        val cat = budgetDraftCategory.ifBlank { return }
        val amt = budgetDraftAmount.toDoubleOrNull() ?: return
        if (amt <= 0) return
        setBudget(cat, amt)
        budgetDraftCategory = ""; budgetDraftAmount = ""
    }

    /** Categories that don't have a budget yet — the only ones worth offering. */
    val budgetableCategories: List<String> get() = categories.filterNot { it in budgets }

    fun setDefaultAccount(v: String) = update { it.copy(defaultAccount = v) }
    fun setFirebaseConfig(v: String) = update { it.copy(firebaseConfigText = v) }

    /** Explicit, so typing the config doesn't reconnect on every keystroke. */
    fun applyFirebaseConfig() {
        if (persisted.firebaseConfigText.isBlank()) sync.disconnect() else connectSync()
    }

    fun disconnectSync() = sync.disconnect()

    val syncConfigLooksValid: Boolean
        get() = parseFirebaseConfig(persisted.firebaseConfigText) != null

    /** How many comma-separated values are actually there — the fastest way to
     *  see a paste that lost a line or gained a stray comma. */
    val syncConfigPartCount: Int
        get() = persisted.firebaseConfigText
            .replace("{", "").replace("}", "")
            .split(",").count { it.isNotBlank() }

    /** Echoes back the project it parsed, so a wrong paste is obvious. */
    val syncConfigSummary: String
        get() = parseFirebaseConfig(persisted.firebaseConfigText)
            ?.let { "project ${it.projectId}" } ?: ""

    val syncedAtClock: String
        get() = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale("en", "IN"))
            .format(java.util.Date(syncedAt))
    fun setOpenaiKey(v: String) = update { it.copy(openaiKeyText = v) }

    // ── derived ────────────────────────────────────────────────────────
    private fun visible(person: String) = person == activeProfile || person == "Joint"

    val visibleEntries: List<Entry> get() = entries.filter { visible(it.person) }
    val visibleAccounts: List<Account> get() = accounts.filter { visible(it.person) }
    val visibleLoans: List<Loan> get() = loans.filter { visible(it.person) }
    val visibleCards: List<Card> get() = cards.filter { visible(it.owner) }

    /**
     * Balances for every account in one pass, cached against the transaction
     * list it was built from. Recomputing per account per recomposition was
     * accounts × transactions of work on every frame.
     */
    // Keyed on the accounts too: editing an opening balance leaves the
    // transaction list untouched, and keying on that alone showed a stale number.
    private var balanceCache: Triple<List<Txn>, List<Account>, Map<String, Double>>? = null

    private fun balances(): Map<String, Double> {
        val txns = persisted.txns
        val accounts = persisted.accounts
        balanceCache?.let { (t, a, cached) -> if (t === txns && a === accounts) return cached }
        val map = HashMap<String, Double>(persisted.accounts.size)
        persisted.accounts.forEach { map[it.id] = it.openingBalance }
        for (t in txns) {
            if (t.fromAccountId.isNotEmpty()) map[t.fromAccountId]?.let { map[t.fromAccountId] = it - t.amount }
            if (t.toAccountId.isNotEmpty()) map[t.toAccountId]?.let { map[t.toAccountId] = it + t.amount }
        }
        balanceCache = Triple(txns, accounts, map)
        return map
    }

    /** Opening balance plus every movement in or out — so undoing a confirm
     *  restores the old number without any inverse bookkeeping. */
    fun balanceOf(a: Account): Double = balances()[a.id] ?: a.openingBalance

    val totalBalance: Double get() = visibleAccounts.sumOf { balanceOf(it) }

    val txns: List<Txn> get() = persisted.txns
    val recentTxns: List<Txn> get() = persisted.txns.sortedByDescending { it.date + it.id }.take(30)

    /** "ICICI Joint → Sinking Fund" for a transfer, a single account name otherwise. */
    fun txnAccountLabel(t: Txn): String {
        val name = { id: String -> accounts.firstOrNull { it.id == id }?.name.orEmpty() }
        return when (t.kind) {
            "TRANSFER" -> "${name(t.fromAccountId)} → ${name(t.toAccountId)}"
            "INCOME" -> name(t.toAccountId)
            else -> name(t.fromAccountId)
        }
    }

    val monthlyIncome: Double get() = visibleEntries.filter { it.type == "INCOME" }.sumOf { it.monthly }
    val monthlyExpense: Double get() = visibleEntries.filter { it.type == "EXPENSE" }.sumOf { it.monthly }
    val monthlyInvestment: Double
        get() = visibleEntries.filter { it.type == "SAVINGS" && it.category in INVEST_CATEGORIES }.sumOf { it.monthly }
    val monthlySavings: Double
        get() = visibleEntries.filter { it.type == "SAVINGS" && it.category !in INVEST_CATEGORIES }.sumOf { it.monthly }

    /** Actual money out for this category this month — confirmed transactions only,
     *  not the plan. A budget bar you can't move by planning is the point. */
    // Keyed on the profile as well as the transactions: which accounts count as
    // "mine" changes when you switch profile, and the totals change with it.
    private var spendCache: Triple<List<Txn>, String?, Map<String, Double>>? = null

    fun spendFor(category: String): Double {
        val txns = persisted.txns
        spendCache?.let { (source, profile, cached) ->
            if (source === txns && profile == activeProfile) return cached[category] ?: 0.0
        }
        val period = currentPeriod()
        val mine = visibleAccounts.map { it.id }.toSet()
        val map = HashMap<String, Double>()
        for (t in txns) {
            // A transfer isn't spending — moving money to the set-aside account
            // for car insurance was inflating the car insurance budget.
            if (t.kind == "TRANSFER") continue
            if (t.month == period && t.fromAccountId in mine) {
                map[t.category] = (map[t.category] ?: 0.0) + t.amount
            }
        }
        spendCache = Triple(txns, activeProfile, map)
        return map[category] ?: 0.0
    }

    /** Regular monthly outgoings — everything except EMIs (own section) and annuals. */
    val commitments: List<Entry>
        get() = visibleEntries.filter {
            // ONE_TIME excluded: older builds saved one-off payments as entries,
            // which then asked to be confirmed again every month.
            !it.isSetAside && it.frequency != "ONE_TIME" &&
                ((it.type == "EXPENSE" && it.category != "EMI") || it.type == "SAVINGS")
        }

    /**
     * Annual items shown at their monthly-equivalent (amount / 12). Confirming one
     * doesn't spend the money — it transfers it to a set-aside account, so the cash
     * is waiting when the yearly bill actually lands.
     */
    val annualSetAsides: List<Entry>
        get() = visibleEntries.filter { it.isSetAside && it.type != "INCOME" }

    val annualSetAsideMonthly: Double get() = annualSetAsides.sumOf { it.monthly }

    val annualSetAsideDone: Double
        get() = annualSetAsides.filter { isConfirmed(it.id) }.sumOf { it.monthly }

    fun commitmentKind(e: Entry): String = when (e.category) {
        in INVEST_CATEGORIES -> "Investment"
        in SAVINGS_CATEGORIES -> "Savings"
        else -> "Recurring"
    }

    val filteredEntries: List<Entry>
        get() {
            val bucketEntries = entries.filter {
                if (bucketView == "PERSONAL") it.bucket == "PERSONAL" && it.person == activeProfile
                else it.bucket == "JOINT"
            }
            return bucketEntries.filter { e ->
                (entriesCategoryFilter == null || e.category == entriesCategoryFilter) &&
                    (entriesSearch.isEmpty() ||
                        e.category.contains(entriesSearch, true) ||
                        e.note.contains(entriesSearch, true))
            }
        }

    /** Which side of the household a transaction belongs to, taken from the
     *  account it moved through — transactions have no bucket of their own. */
    private fun txnPerson(t: Txn): String {
        val id = t.fromAccountId.ifEmpty { t.toAccountId }
        return accounts.firstOrNull { it.id == id }?.person.orEmpty()
    }

    /**
     * Personal is mine, Joint is everything else — deliberately not
     * `person == "Joint"`. Requiring an exact match hid anything paid from a
     * personal account while the toggle sat on Joint, and made a transaction on
     * the other profile's account invisible in both buckets.
     */
    private fun inBucket(t: Txn): Boolean {
        val person = txnPerson(t)
        // Signed in as Joint, everything visible is shared — the toggle is
        // hidden and both sides would otherwise show the same list.
        if (activeProfile == "Joint") return person == "Joint" || person.isEmpty()
        return if (bucketView == "PERSONAL") person == activeProfile
        // Unknown owners land here rather than nowhere: an orphaned transaction
        // must stay reachable, even if its account has been deleted.
        else person == "Joint" || person.isEmpty()
    }

    /** In the other tab — so an empty list can say where things went instead of
     *  looking like nothing was recorded. */
    val otherBucketCount: Int
        get() = txns.count { t ->
            val p = txnPerson(t)
            if (bucketView == "PERSONAL") p == "Joint" || p.isEmpty() else p == activeProfile
        }

    /** On the other profile's own account, so it belongs on their phone. */
    val otherProfileTxnCount: Int
        get() = txns.count { t ->
            val p = txnPerson(t)
            p.isNotEmpty() && p != "Joint" && p != activeProfile
        }

    /** The Transactions screen shows these and nothing else: real recorded
     *  movements, not the recurring plan. */
    val filteredTxns: List<Txn>
        get() = txns
            .filter { inBucket(it) }
            .filter { t ->
                (entriesCategoryFilter == null || t.category == entriesCategoryFilter) &&
                    (entriesSearch.isEmpty() ||
                        t.category.contains(entriesSearch, true) ||
                        t.note.contains(entriesSearch, true) ||
                        t.ref.contains(entriesSearch, true))
            }
            .sortedByDescending { it.date + it.id }

    val txnChips: List<String>
        get() = txns.filter { inBucket(it) }.map { it.category }.distinct()

    /** Removes it here and in Firestore, and the balance follows. */
    fun deleteTxn(id: String) = removeTxns { it.id == id }

    // ── editing a recorded transaction ─────────────────────────────────
    // Without this, anything that landed on the wrong account — an import
    // matched by the wrong number, a one-off that took the default — was stuck
    // there, and stuck in whichever bucket that account belongs to.
    var editingTxnId by mutableStateOf<String?>(null); private set

    val editingTxn: Txn? get() = txns.firstOrNull { it.id == editingTxnId }

    fun startEditTxn(id: String) { editingTxnId = id }
    fun cancelEditTxn() { editingTxnId = null }

    private fun replaceTxn(updated: Txn) {
        update { s -> s.copy(txns = s.txns.map { if (it.id == updated.id) updated else it }) }
        sync.upsertTxn(updated)
    }

    fun setTxnAccount(accountId: String) {
        val t = editingTxn ?: return
        replaceTxn(
            if (t.kind == "INCOME") t.copy(toAccountId = accountId)
            else t.copy(fromAccountId = accountId)
        )
    }

    fun setTxnCategory(category: String) {
        editingTxn?.let { replaceTxn(it.copy(category = category)) }
    }

    val availableChips: List<String>
        get() = entries.filter {
            if (bucketView == "PERSONAL") it.bucket == "PERSONAL" && it.person == activeProfile
            else it.bucket == "JOINT"
        }.map { it.category }.distinct()

    /**
     * Runs last, once every property above exists. Both of these reach code
     * that reads session state — migration saves, which reads activeProfile —
     * so starting them from the first init block threw before the delegates
     * were created.
     */
    init {
        // Existing installs were seeded before Joint was a profile you could
        // sign in to, so it won't be in their list.
        if ("Joint" !in persisted.profiles.keys) {
            update { it.copy(profiles = it.profiles + ("Joint" to "1234")) }
        }
        // Unlocked without the keypad, so the draft never got its owner.
        if (!isLocked) draft = Draft(person = activeProfile ?: "Me")
        migrateOneTimeEntries()
        if (persisted.firebaseConfigText.isNotBlank()) connectSync()
    }

    val askPinOnLaunch: Boolean get() = persisted.askPinOnLaunch

    fun setAskPinOnLaunch(on: Boolean) = update { it.copy(askPinOnLaunch = on) }

    /** Lock by hand — the only way back to the keypad when it's skipped. */
    fun lockNow() {
        isLocked = true
        pinInput = ""
        pinError = false
        pinStep = if (activeProfile != null) "enter" else "pick"
    }

    // distinct(): signed in as Joint, both entries are "Joint" and the dropdown
    // would offer the same option twice.
    val draftPersonOptions: List<String>
        get() = listOfNotNull(activeProfile, "Joint").distinct()

    /** The whole of who an entry is for: shared, or this profile's own. */
    val forOptions: List<String> get() = listOfNotNull("Joint", activeProfile).distinct()

    /** Signed in as Joint there is no personal side, so the split is hidden. */
    val showsBuckets: Boolean get() = activeProfile != "Joint"

    /** Header label — a stale "Personal" would otherwise show for the Joint
     *  profile, which has no personal side at all. */
    val bucketLabel: String
        get() = if (!showsBuckets) "Joint"
        else bucketView.lowercase().replaceFirstChar { it.uppercase() }

    /** Who it's for. The bucket follows from this, and so does the account
     *  unless one was picked deliberately. */
    fun setDraftFor(who: String) {
        draft = draft.copy(person = who)
        // A joint account can't stay selected on a personal payment.
        if (oneOffAccountId.isNotEmpty() &&
            accounts.firstOrNull { it.id == oneOffAccountId }?.person != who &&
            who != "Joint"
        ) {
            oneOffAccountId = ""
        }
    }
}
