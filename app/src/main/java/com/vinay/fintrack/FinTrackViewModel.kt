package com.vinay.fintrack

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.vinay.fintrack.data.Account
import com.vinay.fintrack.data.Card
import com.vinay.fintrack.data.AssistantTools
import com.vinay.fintrack.data.ChatMessage
import com.vinay.fintrack.data.Entry
import com.vinay.fintrack.data.FirestoreSync
import com.vinay.fintrack.data.INVEST_CATEGORIES
import com.vinay.fintrack.data.Ledger
import com.vinay.fintrack.data.Loan
import com.vinay.fintrack.data.PersistedState
import com.vinay.fintrack.data.SAVINGS_CATEGORIES
import com.vinay.fintrack.data.hashPin
import com.vinay.fintrack.data.looksLikePlainPin
import com.vinay.fintrack.data.Seed
import com.vinay.fintrack.data.AccountMatch
import com.vinay.fintrack.data.SmsImporter
import com.vinay.fintrack.data.matchAccountByTail
import com.vinay.fintrack.data.matchAccountByBank
import com.vinay.fintrack.data.matchCardByBank
import com.vinay.fintrack.data.matchCardByTail
import com.vinay.fintrack.data.parseBankSms
import com.vinay.fintrack.data.categoryForParty
import com.vinay.fintrack.data.Store
import com.vinay.fintrack.data.SyncStatus
import com.vinay.fintrack.data.parseFirebaseConfig
import com.vinay.fintrack.data.Txn
import com.vinay.fintrack.data.inr
import com.vinay.fintrack.data.dayFirstOf
import com.vinay.fintrack.data.isoFromDayFirst
import com.vinay.fintrack.data.millisOfDate
import com.vinay.fintrack.data.newId
import com.vinay.fintrack.data.today
import com.vinay.fintrack.data.todayDayFirst
import com.vinay.fintrack.data.ownerLabel
import com.vinay.fintrack.data.prettyDate
// The String overload of put is an extension; without it the member overload
// takes over and only accepts a JsonElement.
import kotlinx.serialization.json.put

enum class Tab { HOME, ENTRIES, ADD, CHAT, SETTINGS }

/** Marks the transaction that settles a card bill, as opposed to the spends
 *  imported onto that same card. */
const val CARD_PAYMENT = Ledger.CARD_PAYMENT

data class Draft(
    val person: String = "Me",
    val type: String = "EXPENSE",
    val category: String = "",
    val amountText: String = "",
    val frequency: String = "MONTHLY",
    val note: String = "",
    val accountId: String = "",
    /** Months between payments, 1–12. Twelve is the old "annual". */
    val periodMonths: Int = 1,
    /** When the bill is due, as the form takes it (dd-mm-yyyy). Optional. */
    val dueText: String = ""
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
    val accountId: String = "",
    /** Set instead of [accountId] when the EMI is billed to a credit card. */
    val cardId: String = "",
    /** The day the EMI comes out, as the form takes it (dd-mm-yyyy). */
    val dueText: String = ""
)

data class NewAccountDraft(
    val name: String = "", val owner: String = "Me", val balanceText: String = "",
    /** Last digits as the bank's SMS writes them, for matching imports. */
    val numberTail: String = ""
)

data class NewCardDraft(
    val name: String = "", val owner: String = "Me", val limitText: String = "",
    val balanceText: String = "", val minDueText: String = "", val due: String = "",
    /** Last digits as the bank's SMS shows them, for matching card spends. */
    val numberTail: String = "",
    /** Bill date as the form takes it (dd-mm-yyyy), so it can be reminded about. */
    val dueText: String = ""
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
                period = cycle(),
                at = System.currentTimeMillis(),
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

    /** Survives leaving the screen, so a permanently denied permission is still
     *  recognised as such rather than offering a button that does nothing. */
    val smsAsked: Boolean get() = persisted.smsAsked

    fun markSmsAsked() {
        if (!persisted.smsAsked) update { it.copy(smsAsked = true) }
    }
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
    /**
     * Re-files imported transactions against the account digits as they stand
     * now.
     *
     * A backfill run before the digits were filled in put everything on the
     * shared account, and there was no way to correct sixty days of it except
     * one at a time. The stored message is re-read for its account number and
     * the transaction moved — nothing else about it changes.
     */
    fun rematchImports() {
        val moved = mutableListOf<Txn>()
        persisted.txns.forEach { t ->
            if (t.source.isEmpty()) return@forEach
            val body = persisted.smsBodies[t.id].orEmpty()
            if (body.isEmpty()) return@forEach
            val tail = parseBankSms(body)?.accountTail.orEmpty()

            // Digits first, then the bank's name — the same order the importer
            // uses, so re-checking agrees with what a fresh message would do.
            val card = (matchCardByTail(cards, tail) as? AccountMatch.One)
                ?: (matchCardByBank(cards, body) as? AccountMatch.One)
            if (card != null) {
                if (t.cardId != card.accountId) {
                    moved += t.copy(cardId = card.accountId, fromAccountId = "", toAccountId = "")
                }
                return@forEach
            }
            val account = (matchAccountByTail(accounts, tail) as? AccountMatch.One)
                ?: (matchAccountByBank(accounts, body) as? AccountMatch.One)
                ?: return@forEach
            val credit = t.kind == "INCOME"
            val current = if (credit) t.toAccountId else t.fromAccountId
            if (current == account.accountId) return@forEach
            moved += if (credit) t.copy(toAccountId = account.accountId)
            else t.copy(fromAccountId = account.accountId, cardId = "")
        }

        if (moved.isEmpty()) {
            scanNote = "Nothing to move — the imports already match, or those " +
                "accounts have no digits set."
            return
        }
        val byId = moved.associateBy { it.id }
        update { s -> s.copy(txns = s.txns.map { byId[it.id] ?: it }) }
        moved.forEach { sync.upsertTxn(it) }
        scanNote = "Moved ${moved.size} import(s) onto the account their message names."
    }

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
        smsLog = emptyList(),
        smsBodies = emptyMap(),
        importedRefs = emptySet(),
        lastSmsScan = 0L,
        lastProfile = "",
        smsAsked = false
    )

    /** Remote state with this device's own fields kept — the mirror of
     *  [sharable], so nothing it withholds gets wiped when a snapshot lands. */
    private fun mergeRemote(remote: PersistedState) = remote.copy(
        txns = persisted.txns,
        localUpdatedAt = persisted.localUpdatedAt,
        firebaseConfigText = persisted.firebaseConfigText,
        openaiKeyText = persisted.openaiKeyText,
        smsLog = persisted.smsLog,
        smsBodies = persisted.smsBodies,
        importedRefs = persisted.importedRefs,
        lastSmsScan = persisted.lastSmsScan,
        smsImportOn = persisted.smsImportOn,
        lastProfile = persisted.lastProfile,
        smsAsked = persisted.smsAsked
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
    var isLocked by mutableStateOf(rememberedProfile == null); private set
    var pinStep by mutableStateOf(if (rememberedProfile != null) "enter" else "pick"); private set
    var activeProfile by mutableStateOf(rememberedProfile); private set
    var pinInput by mutableStateOf(""); private set
    var pinError by mutableStateOf(false); private set

    var tab by mutableStateOf(Tab.HOME)
    var bucketView by mutableStateOf("PERSONAL")
    var balanceHidden by mutableStateOf(false); private set
    var expandedLoan by mutableStateOf<String?>(null); private set


    var entriesSearch by mutableStateOf("")
    var entriesCategoryFilter by mutableStateOf<String?>(null)

    var editingEntryId by mutableStateOf<String?>(null); private set
    var draft by mutableStateOf(Draft())
    var addKind by mutableStateOf("ONE_TIME"); private set
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

    /** Accepts a stored hash, and a plain PIN from before hashing existed. */
    private fun pinMatches(profile: String, pin: String): Boolean {
        val stored = persisted.profiles[profile] ?: return false
        return stored == hashPin(pin) || stored == pin
    }

    /** Turns any plain PIN into a hash, once, so nothing readable is synced. */
    private fun migratePlainPins() {
        val plain = persisted.profiles.filterValues { looksLikePlainPin(it) }
        if (plain.isEmpty()) return
        update { s ->
            s.copy(profiles = s.profiles.mapValues { (_, value) ->
                if (looksLikePlainPin(value)) hashPin(value) else value
            })
        }
    }

    fun pressDigit(d: String) {
        if (pinInput.length >= 4) return
        pinInput += d
        pinError = false
        if (pinInput.length == 4) {
            if (pinMatches(activeProfile.orEmpty(), pinInput)) {
                isLocked = false
                draft = Draft(person = activeProfile ?: "Me")
                // Only if asked for, and only after a correct PIN so a mistaken
                // pick on the shared picker cannot stick.
                activeProfile?.let { p ->
                    update { it.copy(lastProfile = if (rememberMe) p else "") }
                }
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
        // The conversation is one person's, and it is never stored or synced —
        // it lives only as long as this session.
        clearChat()
        // Deliberately switching means the picker should come back next launch.
        update { it.copy(lastProfile = "") }
    }

    fun savePin() {
        if (pinNew.length != 4) { pinMsg = "PIN must be 4 digits."; pinMsgIsError = true; return }
        if (pinNew != pinConfirm) { pinMsg = "PINs don't match."; pinMsgIsError = true; return }
        val p = activeProfile ?: return
        update { it.copy(profiles = it.profiles + (p to hashPin(pinNew))) }
        pinMsg = "PIN updated."; pinMsgIsError = false; pinNew = ""; pinConfirm = ""
    }

    // ── profiles ───────────────────────────────────────────────────────
    // Profiles hold PINs, which are deliberately never synced, so this list is
    // per device: a new profile has to be added on each phone that will use it.
    // Not named setNewProfileName: a var already generates a setter with that
    // JVM signature, and the two collide.
    var profileMsg by mutableStateOf(""); private set

    var renamingProfile by mutableStateOf<String?>(null); private set
    var renameText by mutableStateOf(""); private set

    fun startRenameProfile(name: String) { renamingProfile = name; renameText = name; profileMsg = "" }
    fun editRenameText(v: String) { renameText = v.take(20); profileMsg = "" }
    fun cancelRenameProfile() { renamingProfile = null; renameText = "" }

    /**
     * A profile's name is its identity everywhere — entries, accounts, loans
     * and cards all carry it as a plain string — so renaming has to move all of
     * them together or the person's data would be orphaned.
     */
    fun saveRenameProfile() {
        val old = renamingProfile ?: return
        val new = renameText.trim()
        when {
            new.isEmpty() -> { profileMsg = "Give the profile a name."; return }
            new == old -> { cancelRenameProfile(); return }
            new.equals("Joint", true) -> { profileMsg = "Joint is the shared side."; return }
            new in persisted.profiles.keys -> { profileMsg = "That name is taken."; return }
        }
        update { s ->
            s.copy(
                profiles = s.profiles - old + (new to (s.profiles[old] ?: "1234")),
                entries = s.entries.map { if (it.person == old) it.copy(person = new) else it },
                accounts = s.accounts.map {
                    if (it.person == old) it.copy(person = new, owner = ownerLabel(new)) else it
                },
                loans = s.loans.map { if (it.person == old) it.copy(person = new) else it },
                cards = s.cards.map { if (it.owner == old) it.copy(owner = new) else it },
                lastProfile = if (s.lastProfile == old) new else s.lastProfile
            )
        }
        if (activeProfile == old) activeProfile = new
        if (draft.person == old) draft = draft.copy(person = new)
        cancelRenameProfile()
        profileMsg = "Renamed to $new."
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
            // Only the payment, matched on its source. Matching on the card
            // alone deleted every spend imported onto it, and restored the
            // balance from whichever of those happened to come first.
            val paidTxn = persisted.txns.firstOrNull {
                it.cardId == c.id && it.source == CARD_PAYMENT
            }
            removeTxns { it.cardId == c.id && it.source == CARD_PAYMENT }
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
        val toAccountId: String = "",
        /** Editable, because a set-aside can be paid in parts — put by ₹1,000
         *  now and the rest later — and the amount left is only a suggestion. */
        val amountText: String = "",
        /** The salary month this counts against — the owner's, not the
         *  viewer's, so a joint bill is not owed twice on two cycles. */
        val period: String = ""
    ) {
        val enteredAmount: Double get() = amountText.toDoubleOrNull() ?: amount
        val needsFrom: Boolean get() = kind == "EXPENSE" || kind == "TRANSFER"
        val needsTo: Boolean get() = kind == "INCOME" || kind == "TRANSFER"
        val isReady: Boolean
            get() = enteredAmount > 0 &&
                (!needsFrom || fromAccountId.isNotEmpty()) &&
                (!needsTo || toAccountId.isNotEmpty()) &&
                (kind != "TRANSFER" || fromAccountId != toAccountId)
    }

    var pendingConfirm by mutableStateOf<PendingConfirm?>(null); private set

    /** The cycle we're in, which is the calendar month unless pay arrives on
     *  some other day and the reset day has been moved. */
    fun cycle(): String = Ledger.cycleOf(today(), persisted.cycleResetDay)

    /**
     * The day this profile's month turns over — their salary date.
     *
     * Per profile, because two people are rarely paid on the same day, and the
     * cycle is what decides when a commitment becomes payable again. The old
     * household-wide value is the fallback, so nothing shifts for anyone who
     * hasn't set their own.
     */
    val cycleResetDay: Int
        get() = persisted.salaryDays[activeProfile.orEmpty()] ?: persisted.cycleResetDay

    fun setCycleResetDay(day: Int) {
        val profile = activeProfile
        val safe = day.coerceIn(1, 28)
        update { s ->
            if (profile.isNullOrEmpty()) s.copy(cycleResetDay = safe)
            else s.copy(salaryDays = s.salaryDays + (profile to safe))
        }
    }

    /** Sets a named profile's salary day, or the current one's. Returns whose
     *  it changed, or null if that profile doesn't exist. */
    fun setSalaryDate(profile: String, day: Int): String? {
        val who = profileNames.firstOrNull { it.equals(profile, true) }
            ?: profile.takeIf { it.isBlank() }?.let { activeProfile }
            ?: return null
        update { s -> s.copy(salaryDays = s.salaryDays + (who to day.coerceIn(1, 28))) }
        return who
    }

    /** Everyone's salary day, for the line under the field in Settings. */
    val salaryDaysByProfile: List<Pair<String, Int>>
        get() = profileNames.map { it to (persisted.salaryDays[it] ?: persisted.cycleResetDay) }

    /**
     * The cycle a given person is on. Two salaries rarely land on the same
     * day, so whether something is still owed this month depends on whose it
     * is — not on who happens to be holding the phone.
     */
    fun cycleFor(person: String): String =
        Ledger.cycleOf(today(), persisted.salaryDays[person] ?: persisted.cycleResetDay)

    fun isConfirmed(entryId: String): Boolean {
        val owner = entryById(entryId)?.person ?: activeProfile.orEmpty()
        return persisted.txns.any { it.entryId == entryId && it.month == cycleFor(owner) }
    }

    /** How much of a set-aside has been put by this cycle — it can be paid in
     *  parts rather than all at once. */
    fun setAsideDone(e: Entry): Double =
        Ledger.setAsideDone(persisted.txns, e.id, cycleFor(e.person))

    fun setAsideLeft(e: Entry): Double = (e.monthly - setAsideDone(e)).coerceAtLeast(0.0)

    /** Everything put by for this so far, across all months, less anything paid
     *  out of it. */
    fun setAsidePot(e: Entry): Double = Ledger.setAsidePot(persisted.txns, e.id)

    /**
     * Whether the bill can be paid out of what has been saved for it.
     *
     * Offered from the month it falls due, and whenever the pot already covers
     * it — paying early is your business, and a bill that arrives sooner than
     * expected shouldn't be unpayable.
     */
    fun canPaySetAside(e: Entry): Boolean {
        if (e.closed || !e.isSetAside) return false
        val dueThisCycleOrPast = e.nextDue.isNotEmpty() &&
            Ledger.instalmentsUntil(today(), e.nextDue) <= 1
        return dueThisCycleOrPast || setAsidePot(e) >= e.amount - 0.5
    }

    /**
     * Pays the bill the set-aside was for.
     *
     * The whole amount leaves the account the money was put by into, tagged with
     * the entry so the pot empties by the same arithmetic that filled it. Then
     * either the due date moves on a period — a yearly premium comes round again
     * — or, with no repeat, the entry is closed.
     *
     * Rolling the date is what makes the next twelve months' shares recalculate
     * from scratch instead of the entry sitting there permanently overdue.
     */
    fun paySetAside(e: Entry, fromAccountId: String = "") {
        if (e.closed) return
        val account = fromAccountId.ifEmpty {
            savedIntoAccount(e) ?: e.accountId.ifEmpty { defaultAccountFor(e.person, e.bucket) }
        }
        addTxn { id ->
            Txn(
                id = id,
                date = today(),
                kind = "EXPENSE",
                amount = e.amount,
                category = e.category,
                fromAccountId = account,
                entryId = e.id,
                period = cycleFor(e.person),
                note = "${e.note.ifEmpty { e.category }} — paid",
                at = System.currentTimeMillis()
            )
        }
        val repeats = e.everyMonths > 1
        update { s ->
            s.copy(entries = s.entries.map {
                if (it.id != e.id) it
                else if (repeats && it.dueDate.isNotEmpty())
                    it.copy(dueDate = Ledger.addMonths(it.nextDue, it.everyMonths))
                else if (repeats) it
                else it.copy(closed = true)
            })
        }
    }

    /** Paid off: the last EMI has gone and there is nothing left to confirm. */
    fun isLoanCleared(l: Loan): Boolean = l.remainingMonths <= 0

    /** Where the money was actually put by, so the bill leaves the same place
     *  rather than the account the entry nominally belongs to. */
    private fun savedIntoAccount(e: Entry): String? =
        persisted.txns.lastOrNull { it.entryId == e.id && it.kind == "TRANSFER" }
            ?.toAccountId?.takeIf { it.isNotEmpty() }

    /** Finished with, but kept for its history. */
    fun closeEntry(id: String, closed: Boolean = true) = update { s ->
        s.copy(entries = s.entries.map { if (it.id == id) it.copy(closed = closed) else it })
    }

    val closedEntries: List<Entry> get() = entries.filter { it.closed && visible(it.person) }

    fun isLoanConfirmed(loanId: String): Boolean {
        val owner = loans.firstOrNull { it.id == loanId }?.person ?: activeProfile.orEmpty()
        return persisted.txns.any { it.loanId == loanId && it.month == cycleFor(owner) }
    }

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
        val kind = confirmKindFor(e)
        // A set-aside can be part-paid, so tapping it again tops it up rather
        // than undoing what has already been put by.
        val partial = kind == "TRANSFER" && e.isSetAside
        if (isConfirmed(e.id) && !partial) {
            removeTxns { it.entryId == e.id && it.month == cycleFor(e.person) }
            return
        }
        val left = if (partial) setAsideLeft(e) else e.monthly
        if (partial && left <= 0.0) {
            removeTxns { it.entryId == e.id && it.month == cycleFor(e.person) }
            return
        }
        pendingConfirm = PendingConfirm(
            title = e.note.ifEmpty { e.category },
            amount = left,
            amountText = left.toLong().toString(),
            kind = kind,
            category = e.category,
            entryId = e.id,
            // Starts from the account chosen when the entry was created, so a
            // personal expense doesn't default to the joint account.
            fromAccountId = if (kind == "INCOME") ""
            else e.accountId.ifEmpty { defaultAccountFor(e.person, e.bucket) },
            toAccountId = if (kind == "INCOME") e.accountId.ifEmpty { defaultAccountFor(e.person, e.bucket) } else "",
            period = cycleFor(e.person)
        )
    }

    /** Loan EMIs are paid from the account stored on the loan, so no sheet appears. */
    fun confirmLoan(l: Loan) {
        if (isLoanConfirmed(l.id)) {
            removeTxns { it.loanId == l.id && it.month == cycleFor(l.person) }
            // Paying advanced the tenure, so undoing has to put the month back —
            // and take the instalment back off the card if it went there.
            update { s ->
                s.copy(
                    loans = s.loans.map {
                        if (it.id == l.id) it.copy(
                            remainingMonths = minOf(it.totalMonths, it.remainingMonths + 1)
                        ) else it
                    },
                    cards = s.cards.map {
                        if (it.id == l.cardId)
                            it.copy(balance = (it.balance - l.monthlyEmi).coerceAtLeast(0.0))
                        else it
                    }
                )
            }
            return
        }
        // A card EMI touches no bank account: the instalment is billed to the
        // card and leaves your balance only when the card bill is paid. Debiting
        // an account as well would take the money twice.
        val from = if (l.onCard) "" else l.accountId.ifEmpty { defaultAccountFor(l.person, "JOINT") }
        addTxn { seq ->
            Txn(
                id = seq, date = today(), kind = "EXPENSE", amount = l.monthlyEmi,
                category = "EMI", fromAccountId = from, cardId = l.cardId, loanId = l.id,
                // The owner's cycle, matching what isLoanConfirmed asks for.
                period = cycleFor(l.person), note = l.name,
                at = System.currentTimeMillis()
            )
        }
        // Otherwise "42 of 84 months paid" never moved however often you paid.
        update { s ->
            s.copy(
                loans = s.loans.map {
                    if (it.id == l.id) it.copy(remainingMonths = maxOf(0, it.remainingMonths - 1)) else it
                },
                // A card instalment adds to what the card owes, the same as any
                // other spend on it, and unmarks a bill that was settled.
                cards = s.cards.map {
                    if (it.id == l.cardId) it.copy(balance = it.balance + l.monthlyEmi, paid = false)
                    else it
                }
            )
        }
    }

    /** Adds a transaction locally and as its own Firestore document. */
    /**
     * The one place a transaction is added in the app.
     *
     * Nothing added here is announced: you are already looking at the screen
     * you added it on, and being notified about something you just typed is
     * noise. Only bank messages notify, since those arrive with the app closed.
     */
    private fun addTxn(build: (id: String) -> Txn): Txn {
        val txn = build(newId("t"))
        update { s -> s.copy(txns = s.txns + txn) }
        sync.upsertTxn(txn)
        return txn
    }

    private fun removeTxns(match: (Txn) -> Boolean) {
        val doomed = persisted.txns.filter(match)
        if (doomed.isEmpty()) return
        update { s -> s.copy(txns = s.txns.filterNot(match)) }
        doomed.forEach { sync.deleteTxn(it.id) }
    }

    fun setConfirmFrom(id: String) { pendingConfirm = pendingConfirm?.copy(fromAccountId = id) }

    fun setConfirmAmount(v: String) {
        pendingConfirm = pendingConfirm?.copy(amountText = v.filter { it.isDigit() || it == '.' })
    }
    fun setConfirmTo(id: String) { pendingConfirm = pendingConfirm?.copy(toAccountId = id) }
    fun cancelConfirm() { pendingConfirm = null }

    fun commitConfirm() {
        val p = pendingConfirm ?: return
        if (!p.isReady) return
        addTxn { seq ->
            Txn(
                id = seq, date = today(), kind = p.kind, amount = p.enteredAmount,
                category = p.category,
                fromAccountId = if (p.needsFrom) p.fromAccountId else "",
                toAccountId = if (p.needsTo) p.toAccountId else "",
                entryId = p.entryId, cardId = p.cardId,
                // Tagged so undoing a card payment can find it among the
                // spends imported onto the same card.
                source = if (p.cardId.isNotEmpty()) CARD_PAYMENT else "",
                // The cycle of whoever the commitment belongs to, so it counts
                // as done against their salary month rather than the viewer's.
                period = p.period.ifEmpty { cycle() }, note = p.title,
                at = System.currentTimeMillis()
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

    // ── entries ────────────────────────────────────────────────────────
    fun deleteEntry(id: String) = update { s -> s.copy(entries = s.entries.filterNot { it.id == id }) }

    fun openEditEntry(e: Entry) {
        editingEntryId = e.id
        tab = Tab.ADD
        // Named, because a positional list this long is how the bucket field
        // silently took the wrong value once already.
        draft = Draft(
            person = e.person,
            type = e.type,
            category = e.category,
            amountText = e.amount.toLong().toString(),
            frequency = e.frequency,
            note = e.note,
            accountId = e.accountId,
            periodMonths = e.everyMonths,
            dueText = if (e.dueDate.isEmpty()) "" else dayFirstOf(e.dueDate)
        )
    }

    fun cancelEdit() {
        editingEntryId = null
        draft = Draft(person = scopePerson)
        addKind = "ONE_TIME"
    }

    fun setCategoryFilter(c: String?) {
        entriesCategoryFilter = if (entriesCategoryFilter == c) null else c
    }

    // ── add ────────────────────────────────────────────────────────────
    fun selectAddKind(k: String) {
        addKind = k
        val p = scopePerson
        when (k) {
            "RECURRING" -> draft = Draft(person = p, type = "EXPENSE", frequency = "MONTHLY", periodMonths = 1)
            "SET_ASIDE" -> draft = Draft(person = p, type = "EXPENSE", frequency = "ANNUAL", periodMonths = 12)
            "INVESTMENT" -> draft = Draft(person = p, type = "SAVINGS", category = "LIC", frequency = "MONTHLY")
            "ONE_TIME" -> draft = Draft(person = p, type = "EXPENSE", frequency = "ONE_TIME")
            "EMI_LOAN" -> newLoanDraft = NewLoanDraft(person = p)
            "BANK_ACCOUNT" -> newAccountDraft = NewAccountDraft(owner = p)
            "CREDIT_CARD" -> newCardDraft = NewCardDraft(owner = p)
        }
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
    /** The due date as the form has it so far, or empty if it isn't a date yet. */
    val draftDueIso: String get() = isoFromDayFirst(draft.dueText).orEmpty()

    /** "5 months, 17 days" for the line under the field. */
    val draftDueIn: String
        get() = Ledger.untilText(
            today(),
            Ledger.nextDue(draftDueIso, draft.periodMonths.coerceAtLeast(1), today())
        )

    /**
     * How many months the form will actually spread the amount over, so the
     * line under the field shows the real figure rather than a twelfth that
     * only holds if you started a full year early.
     */
    val draftInstalments: Int
        get() = if (draftDueIso.isNotEmpty()) Ledger.instalmentsUntil(today(), draftDueIso)
        else draft.periodMonths.coerceAtLeast(1)

    /** Never blank: falls back to the account implied by who it's for. */
    val draftAccountName: String
        get() = accounts.firstOrNull {
            it.id == draft.accountId.ifEmpty { defaultAccountFor(draft.person, draft.bucket) }
        }?.name.orEmpty()

    /** Resolved account for the one-off form, so the picker is never blank and
     *  the fallback follows the Personal/Joint choice. */
    val resolvedOneOffAccount: String
        get() = oneOffAccountId.ifEmpty { defaultAccountFor(draft.person, draft.bucket) }

    /** Accounts belonging to the side the For choice is on. Offering all of
     *  them let the account contradict that choice, and the account is what
     *  actually decides where a transaction lands. */
    val oneOffAccountOptions: List<Account>
        get() = visibleAccounts.filter {
            if (draft.person == "Joint") it.person == "Joint" else it.person == draft.person
        }.ifEmpty { visibleAccounts }

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
                period = Ledger.cycleOf(date, cycleResetDay),
                note = draft.note.ifEmpty { draft.category },
                // Today's keeps the current time. A back-dated one records no
                // time at all rather than a midnight nobody chose — the row
                // then shows just its date, which is all that is known.
                at = if (date == today()) System.currentTimeMillis() else 0L
            )
        }
        draft = Draft(person = scopePerson)
        oneOffAccountId = ""
        oneOffIsCredit = false
        oneOffDateText = todayDayFirst()
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
                id = editingId ?: newId("e"),
                person = draft.person,
                type = draft.type,
                bucket = draft.bucket,
                category = draft.category,
                amount = amount,
                // Kept for older readers; the period is what actually counts.
                frequency = if (draft.periodMonths >= 12) "ANNUAL" else "MONTHLY",
                note = draft.note,
                accountId = draft.accountId.ifEmpty { defaultAccountFor(draft.person, draft.bucket) },
                periodMonths = draft.periodMonths,
                dueDate = draftDueIso
            )
            if (editingId != null) {
                s.copy(entries = s.entries.map { if (it.id == entry.id) entry else it })
            } else {
                s.copy(entries = s.entries + entry)
            }
        }
        editingEntryId = null
        draft = Draft(person = scopePerson)
        // Home, not Transactions: an entry is the plan, and Transactions now
        // lists recorded movements only — landing there looked like a failed save.
        tab = Tab.HOME
    }

    /**
     * Where an EMI is charged, accounts and credit cards in one list.
     *
     * A card EMI — a purchase split into instalments — is a loan whose payments
     * land on the card, so the two belong in the same picker rather than as a
     * separate kind of thing to add.
     */
    val emiSourceOptions: List<String>
        get() = visibleAccounts.map { "${it.name} · ${it.person}" } +
            visibleCards.map { "${it.name} · card" }

    /** Where a loan's EMI is charged, for the row on Home. */
    fun emiSourceLabel(l: Loan): String =
        if (l.onCard) "Billed to ${cards.firstOrNull { it.id == l.cardId }?.name ?: "a card"}"
        else "From ${accounts.firstOrNull { it.id == l.accountId }?.name ?: defaultAccount}"

    private fun sourceNameOf(draft: NewLoanDraft): String =
        cards.firstOrNull { it.id == draft.cardId }?.let { "${it.name} · card" }
            ?: accounts.firstOrNull {
                it.id == draft.accountId.ifEmpty { accountIdByName(defaultAccount) }
            }?.let { "${it.name} · ${it.person}" }.orEmpty()

    /** One choice sets one of the two ids and clears the other, so a loan can
     *  never be charged to an account and a card at once. */
    private fun withSource(draft: NewLoanDraft, label: String): NewLoanDraft {
        val card = cards.firstOrNull { "${it.name} · card" == label }
        if (card != null) return draft.copy(cardId = card.id, accountId = "")
        val account = accounts.firstOrNull { "${it.name} · ${it.person}" == label }
        return draft.copy(accountId = account?.id.orEmpty(), cardId = "")
    }

    val newLoanSourceName: String get() = sourceNameOf(newLoanDraft)
    fun setLoanSource(label: String) { newLoanDraft = withSource(newLoanDraft, label) }

    /** The same pair for the inline editor on Home, which uses its own draft. */
    val editLoanSourceName: String get() = sourceNameOf(loanDraft)
    fun setEditLoanSource(label: String) { loanDraft = withSource(loanDraft, label) }

    fun addNewLoan() {
        val emi = newLoanDraft.emiText.toDoubleOrNull() ?: return
        val total = newLoanDraft.totalMonthsText.toIntOrNull() ?: return
        if (newLoanDraft.name.isBlank() || emi <= 0 || total <= 0) return
        val remaining = newLoanDraft.remainingMonthsText.toIntOrNull() ?: total
        val onCard = newLoanDraft.cardId.isNotEmpty()
        update { s ->
            s.copy(
                loans = s.loans + Loan(
                    id = newId("l"),
                    name = newLoanDraft.name,
                    person = newLoanDraft.person,
                    monthlyEmi = emi,
                    totalMonths = total,
                    remainingMonths = remaining,
                    accountId = if (onCard) "" else
                        newLoanDraft.accountId.ifEmpty { accountIdByName(defaultAccount) },
                    cardId = newLoanDraft.cardId,
                    dueDate = isoFromDayFirst(newLoanDraft.dueText).orEmpty()
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
                    id = newId("cc"),
                    name = newCardDraft.name,
                    owner = newCardDraft.owner,
                    limit = limit,
                    balance = newCardDraft.balanceText.toDoubleOrNull() ?: 0.0,
                    minDue = newCardDraft.minDueText.toDoubleOrNull() ?: 0.0,
                    due = newCardDraft.due,
                    numberTail = newCardDraft.numberTail,
                    dueDate = isoFromDayFirst(newCardDraft.dueText).orEmpty()
                )
            )
        }
        newCardDraft = NewCardDraft(owner = activeProfile ?: "Me")
        tab = Tab.HOME
    }

    // ── inline editors ─────────────────────────────────────────────────
    fun startEditAccount(a: Account) {
        editingAccountId = a.id
        // a.person, not a.owner: owner is the display label ("Me · personal").
        accountDraft = NewAccountDraft(
            a.name, a.person, a.openingBalance.toLong().toString(), a.numberTail
        )
    }

    fun cancelEditAccount() { editingAccountId = null }

    fun saveAccount() {
        val id = editingAccountId ?: return
        update { s ->
            s.copy(accounts = s.accounts.map {
                if (it.id == id) it.copy(
                    name = accountDraft.name,
                    // person is what decides the bucket and where a bank
                    // message lands. It was never written here, so an account
                    // could not be moved between profiles at all.
                    person = accountDraft.owner,
                    owner = ownerLabel(accountDraft.owner),
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
            name = l.name,
            person = l.person,
            emiText = l.monthlyEmi.toLong().toString(),
            totalMonthsText = l.totalMonths.toString(),
            remainingMonthsText = l.remainingMonths.toString(),
            accountId = l.accountId,
            cardId = l.cardId,
            dueText = if (l.dueDate.isEmpty()) "" else dayFirstOf(l.dueDate)
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
                    accountId = if (loanDraft.cardId.isNotEmpty()) "" else loanDraft.accountId,
                    cardId = loanDraft.cardId,
                    dueDate = isoFromDayFirst(loanDraft.dueText).orEmpty()
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
            name = c.name,
            owner = c.owner,
            limitText = c.limit.toLong().toString(),
            balanceText = c.balance.toLong().toString(),
            minDueText = c.minDue.toLong().toString(),
            due = c.due,
            numberTail = c.numberTail,
            dueText = if (c.dueDate.isEmpty()) "" else dayFirstOf(c.dueDate)
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
                    due = cardDraft.due,
                    numberTail = cardDraft.numberTail,
                    dueDate = isoFromDayFirst(cardDraft.dueText).orEmpty()
                ) else it
            })
        }
        editingCardId = null
    }

    /**
     * A card's transactions go with it. They reference no account, so leaving
     * them would strand records that belong to nobody and show up under Joint —
     * and unlike an account's, they cannot be moved somewhere sensible, since
     * that would alter a real balance.
     */
    fun deleteCard(id: String) {
        removeTxns { it.cardId == id }
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

    val budgetRollover: Boolean get() = persisted.budgetRollover

    fun setBudgetRollover(on: Boolean) = update { s -> s.copy(budgetRollover = on) }

    /**
     * What this category may spend this month — the budget, plus last month's
     * leftover when rollover is on.
     *
     * Overspending carries too. Carrying only the underspend would make the
     * figure flattering rather than honest, and a budget you cannot fall behind
     * on is not a budget.
     */
    fun allowanceFor(category: String): Double {
        val budget = budgets[category] ?: 0.0
        if (!budgetRollover) return budget
        return Ledger.allowance(budget, spentIn(Ledger.cycleBefore(cycle(), 1), category))
    }

    /** Carried in from last month: positive if you underspent, negative if not. */
    fun rolloverFor(category: String): Double =
        if (!budgetRollover) 0.0 else allowanceFor(category) - (budgets[category] ?: 0.0)

    /** What the last three months spent on this category, oldest first. */
    fun spendTrendFor(category: String): List<Double> = Ledger.spendTrend(
        persisted.txns, cycle(), category,
        scopedAccounts.map { it.id }.toSet(),
        scopedCards.map { it.id }.toSet()
    )

    private fun spentIn(period: String, category: String): Double = Ledger.spendByCategory(
        persisted.txns, period,
        scopedAccounts.map { it.id }.toSet(),
        scopedCards.map { it.id }.toSet()
    )[category] ?: 0.0

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
    fun setOpenaiKey(v: String) = update { it.copy(openaiKeyText = v) }

    /** Explicit, so typing the config doesn't reconnect on every keystroke. */
    fun applyFirebaseConfig() {
        if (persisted.firebaseConfigText.isBlank()) sync.disconnect() else connectSync()
    }

    fun disconnectSync() = sync.disconnect()

    val syncConfigLooksValid: Boolean
        get() = parseFirebaseConfig(persisted.firebaseConfigText) != null

    /** Echoes back the project it parsed, so a wrong paste is obvious. */
    val syncConfigSummary: String
        get() = parseFirebaseConfig(persisted.firebaseConfigText)
            ?.let { "project ${it.projectId}" } ?: ""

    // ── derived ────────────────────────────────────────────────────────
    private fun visible(person: String) = person == activeProfile || person == "Joint"

    // visible* is everything this profile may touch — mine plus joint — and is
    // what account pickers offer, so a joint account stays choosable from the
    // personal side.
    val visibleEntries: List<Entry> get() = entries.filter { visible(it.person) }
    val visibleAccounts: List<Account> get() = accounts.filter { visible(it.person) }
    val visibleLoans: List<Loan> get() = loans.filter { visible(it.person) }
    val visibleCards: List<Card> get() = cards.filter { visible(it.owner) }

    /**
     * scoped* is the narrower thing: only the side the Home switch is on, so
     * flipping to Joint shows the shared accounts and commitments alone rather
     * than mixing them with personal ones.
     */
    private fun inScope(person: String) =
        if (bucketView == "JOINT") person == "Joint" else person == activeProfile

    /** Closed ones are excluded here rather than at each use: a finished bill
     *  must leave Home, the plan and the budgets together, not one at a time. */
    val scopedEntries: List<Entry>
        get() = visibleEntries.filter { inScope(it.person) && !it.closed }
    val scopedAccounts: List<Account> get() = visibleAccounts.filter { inScope(it.person) }
    val scopedLoans: List<Loan> get() = visibleLoans.filter { inScope(it.person) }
    val scopedCards: List<Card> get() = visibleCards.filter { inScope(it.owner) }

    /**
     * Every account, for moving money between them.
     *
     * Wider than [visibleAccounts] on purpose: a transfer to the other
     * profile's account is a real thing households do, and both ends now show
     * it, so there is no reason to hide the destination.
     */
    val transferAccounts: List<Account> get() = accounts

    /** Who a new entry is for, following the switch. */
    val scopePerson: String get() = if (bucketView == "JOINT") "Joint" else activeProfile ?: "Me"

    fun setScope(joint: Boolean) {
        bucketView = if (joint) "JOINT" else "PERSONAL"
        // So the Add form opens on the side you're looking at.
        draft = draft.copy(person = scopePerson)
    }

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
        val map = Ledger.balances(accounts, txns)
        balanceCache = Triple(txns, accounts, map)
        return map
    }

    /** Opening balance plus every movement in or out — so undoing a confirm
     *  restores the old number without any inverse bookkeeping. */
    fun balanceOf(a: Account): Double = balances()[a.id] ?: a.openingBalance

    val totalBalance: Double get() = scopedAccounts.sumOf { balanceOf(it) }

    val txns: List<Txn> get() = persisted.txns
    val recentTxns: List<Txn> get() = persisted.txns.sortedByDescending { it.sortKey }.take(30)

    /** "ICICI Joint → Sinking Fund" for a transfer, a single account name otherwise. */
    fun txnAccountLabel(t: Txn): String {
        val name = { id: String -> accounts.firstOrNull { it.id == id }?.name.orEmpty() }
        return when (t.kind) {
            "TRANSFER" -> "${name(t.fromAccountId)} → ${name(t.toAccountId)}"
            "INCOME" -> name(t.toAccountId)
            else -> name(t.fromAccountId)
        }
    }

    // planned* is what the entries say should happen each month; actual* is what
    // did. The two were conflated, so the month's figures never moved however
    // many transactions were added or deleted.
    val plannedIncome: Double get() = scopedEntries.filter { it.type == "INCOME" }.sumOf { it.monthly }

    /**
     * Everything expected out each month on this side: ordinary expenses, the
     * monthly share of each set-aside, and the loans' EMIs.
     *
     * EMI-category entries are left out because the loans already account for
     * them — the seed carries both, and counting each would double the figure.
     */
    val plannedExpense: Double
        get() = scopedEntries
            .filter { it.type == "EXPENSE" && !coveredByLoan(it) }
            .sumOf { it.monthly } + scopedLoans.sumOf { it.monthlyEmi }

    // Keyed on the side as well: the figures are for whichever the switch is on,
    // and using the visible accounts mixed personal and joint together whatever
    // it said.
    private var totalsCache: Triple<List<Txn>, String, Ledger.MonthTotals>? = null

    private fun monthTotals(): Ledger.MonthTotals {
        val t = persisted.txns
        val key = "$bucketView/${activeProfile.orEmpty()}"
        totalsCache?.let { (source, k, cached) -> if (source === t && k == key) return cached }
        val totals = Ledger.monthTotals(
            t,
            cycle(),
            scopedAccounts.map { it.id }.toSet(),
            scopedCards.map { it.id }.toSet(),
            INVEST_CATEGORIES
        )
        totalsCache = Triple(t, key, totals)
        return totals
    }

    val actualIncome: Double get() = monthTotals().income
    val actualSpent: Double get() = monthTotals().spent
    val actualSaved: Double get() = monthTotals().saved
    val actualInvested: Double get() = monthTotals().invested

    val monthlyIncome: Double get() = plannedIncome
    val monthlyExpense: Double get() = plannedExpense
    val monthlyInvestment: Double
        get() = scopedEntries.filter { it.type == "SAVINGS" && it.category in INVEST_CATEGORIES }.sumOf { it.monthly }
    val monthlySavings: Double
        get() = scopedEntries.filter { it.type == "SAVINGS" && it.category !in INVEST_CATEGORIES }.sumOf { it.monthly }

    /** Actual money out for this category this month — confirmed transactions only,
     *  not the plan. A budget bar you can't move by planning is the point. */
    private class SpendCache(
        val txns: List<Txn>,
        val accounts: List<Account>,
        val cards: List<Card>,
        /** Side and profile together — both change which accounts count. */
        val profile: String,
        val byCategory: Map<String, Double>
    )

    // Keyed on the profile as well as the transactions: which accounts count as
    // "mine" changes when you switch profile, and the totals change with it.
    private var spendCache: SpendCache? = null

    fun spendFor(category: String): Double {
        val txns = persisted.txns
        val key = "$bucketView/${activeProfile.orEmpty()}"
        spendCache?.let {
            if (it.txns === txns && it.accounts === persisted.accounts &&
                it.cards === persisted.cards && it.profile == key
            ) return it.byCategory[category] ?: 0.0
        }
        // Scoped, like everything else on Home: viewing the personal side used
        // to count joint spending against the same bar.
        val map = Ledger.spendByCategory(
            txns,
            cycle(),
            scopedAccounts.map { it.id }.toSet(),
            scopedCards.map { it.id }.toSet()
        )
        spendCache = SpendCache(txns, persisted.accounts, persisted.cards, key, map)
        return map[category] ?: 0.0
    }

    /** Regular monthly outgoings — everything except EMIs (own section) and annuals. */
    /** True when a loan already accounts for this entry, so showing or counting
     *  it as well would be the same debt twice. */
    private fun coveredByLoan(e: Entry): Boolean =
        e.category == "EMI" && loans.any {
            it.person == e.person && kotlin.math.abs(it.monthlyEmi - e.amount) < 0.5
        }

    val commitments: List<Entry>
        get() = scopedEntries.filter {
            // ONE_TIME excluded: older builds saved one-off payments as entries,
            // which then asked to be confirmed again every month. EMI entries
            // are excluded only when a loan covers them — excluding the whole
            // category left a hand-made one showing on no list at all.
            !it.isSetAside && it.frequency != "ONE_TIME" && !coveredByLoan(it) &&
                (it.type == "EXPENSE" || it.type == "SAVINGS")
        }

    /**
     * Annual items shown at their monthly-equivalent (amount / 12). Confirming one
     * doesn't spend the money — it transfers it to a set-aside account, so the cash
     * is waiting when the yearly bill actually lands.
     */
    val annualSetAsides: List<Entry>
        get() = scopedEntries.filter { it.isSetAside && it.type != "INCOME" }

    val annualSetAsideMonthly: Double get() = annualSetAsides.sumOf { it.monthly }

    /** What has actually been put by, part-payments included, rather than a
     *  count of the ones fully met. */
    val annualSetAsideDone: Double
        get() = annualSetAsides.sumOf { setAsideDone(it).coerceAtMost(it.monthly) }

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
    private fun txnPersons(t: Txn): Set<String> = Ledger.personsOf(t, accounts, cards)

    private fun txnPerson(t: Txn): String = Ledger.personOf(t, accounts, cards)

    /**
     * Personal is mine, Joint is everything else — deliberately not
     * `person == "Joint"`. Requiring an exact match hid anything paid from a
     * personal account while the toggle sat on Joint, and made a transaction on
     * the other profile's account invisible in both buckets.
     */
    private fun inBucket(t: Txn): Boolean =
        Ledger.inBucket(txnPersons(t), activeProfile, bucketView == "PERSONAL")

    /** In the other tab — so an empty list can say where things went instead of
     *  looking like nothing was recorded. */
    val otherBucketCount: Int
        get() = txns.count { t ->
            !inBucket(t) && Ledger.inBucket(
                txnPersons(t), activeProfile, bucketView != "PERSONAL"
            )
        }

    /** Concerning neither you nor the household, so it belongs on their phone. */
    val otherProfileTxnCount: Int
        get() = txns.count { t ->
            val p = txnPersons(t)
            p.isNotEmpty() && "Joint" !in p && activeProfile !in p
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
            .sortedByDescending { it.sortKey }

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

    /** The bank message an imported transaction came from, if it's still kept. */
    fun smsBodyFor(id: String): String = persisted.smsBodies[id].orEmpty()

    fun startEditTxn(id: String) { editingTxnId = id }

    /**
     * Opens an imported transaction from its notification.
     *
     * The transaction may sit in the other profile's bucket, so the list behind
     * the sheet can be empty — the sheet is still shown. Being unable to reach
     * a payment you were just told about would defeat the notification.
     */
    fun openImportedTxn(id: String) {
        if (txns.none { it.id == id }) return
        tab = Tab.ENTRIES
        editingTxnId = id
    }
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

    /** The description. An imported one is named after whatever the bank
     *  called the payee, which is rarely what you would have called it. */
    fun setTxnNote(note: String) {
        editingTxn?.let { replaceTxn(it.copy(note = note.trim())) }
    }

    /** For when the bank's figure was read wrongly, or a cash amount changed. */
    fun setTxnAmount(text: String) {
        val amount = text.toDoubleOrNull() ?: return
        if (amount <= 0) return
        editingTxn?.let { replaceTxn(it.copy(amount = amount)) }
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
    /**
     * The seed carried each EMI twice — as an expense entry and as a loan — and
     * such an entry appears on no list: the commitments section excludes the
     * EMI category because loans handle it, and it isn't a set-aside. So it sat
     * invisible while inflating the month's planned total.
     *
     * Only entries matching a loan of the same person and amount are removed;
     * an EMI entry with no loan behind it is somebody's own record and stays.
     */
    private fun migrateDuplicateEmiEntries() {
        val duplicates = persisted.entries.filter { e ->
            e.category == "EMI" && persisted.loans.any {
                it.person == e.person && kotlin.math.abs(it.monthlyEmi - e.amount) < 0.5
            }
        }
        if (duplicates.isEmpty()) return
        val doomed = duplicates.map { it.id }.toSet()
        update { s -> s.copy(entries = s.entries.filterNot { it.id in doomed }) }
    }

    init {
        // Joint briefly was a sign-in profile; it's a view now, so take it back
        // out of the list rather than leaving a login nobody should use.
        if ("Joint" in persisted.profiles.keys) {
            update { it.copy(profiles = it.profiles - "Joint") }
        }
        // Unlocked without the keypad, so the draft never got its owner.
        if (!isLocked) draft = Draft(person = activeProfile ?: "Me")
        migrateOneTimeEntries()
        migrateDuplicateEmiEntries()
        migratePlainPins()
        if (persisted.firebaseConfigText.isNotBlank()) connectSync()
    }

    /** Ticked on the lock screen, applied when the PIN is accepted. Replaces a
     *  setting nobody would have gone looking for. */
    var rememberMe by mutableStateOf(true); private set

    fun toggleRememberMe() { rememberMe = !rememberMe }

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

    /**
     * Who an account or card can belong to: every profile, plus Joint.
     *
     * Wider than [forOptions] deliberately — you set up both phones' accounts
     * from one, and an account has to be assignable to whoever owns it.
     */
    val ownerOptions: List<String> get() = (listOf("Joint") + profileNames).distinct()

    /** The whole of who an entry is for: shared, or this profile's own. */
    val forOptions: List<String> get() = listOfNotNull("Joint", activeProfile).distinct()

    /** Header label for whichever side the Home switch is on. */
    val bucketLabel: String
        get() = if (bucketView == "JOINT") "Joint" else activeProfile.orEmpty()

    /** Who it's for. The bucket follows from this, and so does the account
     *  unless one was picked deliberately. */
    fun setDraftFor(who: String) {
        draft = draft.copy(person = who)
        // Whatever was picked belongs to the other side now, so let it resolve
        // again rather than leaving a contradiction on screen.
        if (accounts.firstOrNull { it.id == oneOffAccountId }?.person != who) {
            oneOffAccountId = ""
        }
    }

    // ── the chat ───────────────────────────────────────────────────────

    /** How much of the conversation is resent each turn. Every request carries
     *  the whole history, so this bounds both the wait and the cost. */
    private val KEPT_TURNS = 16

    private val assistant = Assistant(this)

    /** What the user sees. The model's own transcript, including tool calls
     *  and their results, is kept separately in [assistantHistory]. */
    var chat by mutableStateOf(listOf<ChatMessage>()); private set
    var chatBusy by mutableStateOf(false); private set
    // Assigned directly by the screen. A setChatInput function would collide
    // with the setter this var already generates.
    var chatInput by mutableStateOf("")

    /** The model's transcript, without the system message — that is rebuilt
     *  each turn so its figures are current. */
    private var assistantHistory: List<kotlinx.serialization.json.JsonElement>? = null

    /**
     * Keeps the recent conversation, cut only where it is safe to cut.
     *
     * A tool reply is only valid directly after the assistant message that
     * asked for it. Trimming by count alone could keep the reply and drop the
     * request, and OpenAI rejects the whole call: "messages with role 'tool'
     * must be a response to a preceding message with tool_calls".
     *
     * So the window is moved back to the nearest user message, which is always
     * a clean boundary.
     */
    private fun trimHistory(
        history: List<kotlinx.serialization.json.JsonElement>
    ): List<kotlinx.serialization.json.JsonElement> {
        if (history.size <= KEPT_TURNS) return history
        var start = history.size - KEPT_TURNS
        while (start < history.size && roleOf(history[start]) != "user") start++
        // No user message in the window: keep the lot rather than send something
        // malformed.
        return if (start >= history.size) history else history.drop(start)
    }

    private fun roleOf(message: kotlinx.serialization.json.JsonElement): String =
        ((message as? kotlinx.serialization.json.JsonObject)
            ?.get("role") as? kotlinx.serialization.json.JsonPrimitive)
            ?.content.orEmpty()

    /**
     * Everything the assistant would otherwise spend a round trip fetching.
     * Deliberately excludes individual transactions, which are unbounded and
     * are what list_transactions is for.
     */
    private fun liveContext(): String = buildString {
        val side = if (bucketView == "JOINT") "joint" else "personal"
        appendLine("Figures below are for the $side side; the accounts listed are " +
            "everything this profile can use.")
        appendLine("Balance on this side ${inr(totalBalance)}.")
        appendLine("Recorded this month: received ${inr(actualIncome)}, spent ${inr(actualSpent)}, " +
            "set aside ${inr(actualSaved)}, invested ${inr(actualInvested)}.")
        appendLine("Planned each month: ${inr(plannedIncome)} in, ${inr(plannedExpense)} out " +
            "(expenses, set-asides and EMIs).")
        appendLine("Accounts:")
        visibleAccounts.forEach {
            appendLine("  ${it.name} = ${inr(balanceOf(it))} (${it.person}" +
                (if (it.numberTail.isNotBlank()) ", ends ${it.numberTail}" else "") + ")")
        }
        if (visibleCards.isNotEmpty()) {
            appendLine("Cards:")
            visibleCards.forEach {
                appendLine("  ${it.name} owes ${inr(it.balance)} of ${inr(it.limit)}, due ${it.due}" +
                    if (it.paid) " (paid)" else "")
            }
        }
        if (commitments.isNotEmpty()) {
            appendLine("Monthly commitments:")
            commitments.forEach {
                appendLine("  [${it.id}] ${it.category} ${inr(it.amount)}" +
                    (if (it.note.isNotBlank()) " (${it.note})" else "") +
                    if (isConfirmed(it.id)) " — done" else "")
            }
        }
        if (annualSetAsides.isNotEmpty()) {
            appendLine("Set-asides (need ${inr(annualSetAsideMonthly)}/mo, " +
                "${inr(annualSetAsideDone)} done):")
            annualSetAsides.forEach {
                appendLine("  [${it.id}] ${it.category} ${inr(it.amount)} every " +
                    "${it.everyMonths} months = ${inr(it.monthly)}/mo" +
                    if (isConfirmed(it.id)) " — done" else "")
            }
        }
        if (visibleLoans.isNotEmpty()) {
            appendLine("Loans:")
            visibleLoans.forEach {
                appendLine("  [${it.id}] ${it.name} ${inr(it.monthlyEmi)}/mo, " +
                    "${it.remainingMonths}/${it.totalMonths} months left" +
                    if (isLoanConfirmed(it.id)) " — paid" else "")
            }
        }
        if (budgets.isNotEmpty()) {
            appendLine("Budgets (spent of limit):")
            budgets.forEach { (cat, limit) -> appendLine("  $cat ${inr(spendFor(cat))}/${inr(limit)}") }
        }
        appendLine("Categories: ${categories.joinToString(", ")}")
    }

    val chatReady: Boolean get() = persisted.openaiKeyText.isNotBlank()

    fun clearChat() {
        chat = emptyList()
        assistantHistory = null
    }

    /**
     * Sends a message and applies whatever the assistant decides to do. Runs off
     * the main thread; the tools hop back onto it themselves.
     */
    fun sendChat() {
        val text = chatInput.trim()
        if (text.isEmpty() || chatBusy) return
        if (!chatReady) {
            chat = chat + ChatMessage("assistant", "Add an OpenAI key in Settings first.")
            return
        }
        chatInput = ""
        chat = chat + ChatMessage("user", text)
        chatBusy = true
        markUndoPoint()

        val key = persisted.openaiKeyText

        // Rebuilt every turn rather than kept in the history, so the figures are
        // never stale — and so most questions are answered in one round trip
        // instead of two, since the model doesn't have to fetch what's here.
        val system = kotlinx.serialization.json.buildJsonObject {
            put("role", "system")
            put(
                "content",
                AssistantTools.systemPrompt(
                    activeProfile.orEmpty(),
                    if (bucketView == "JOINT") "joint" else "personal",
                    prettyDate(today()),
                    liveContext()
                )
            )
        }

        // Older turns are dropped: every request resends the whole conversation,
        // so an unbounded one gets slower and dearer with each message.
        val kept = trimHistory(assistantHistory.orEmpty())
        val withUser = kotlinx.serialization.json.buildJsonArray {
            add(system)
            kept.forEach { add(it) }
            add(
                kotlinx.serialization.json.buildJsonObject {
                    put("role", "user"); put("content", text)
                }
            )
        }

        Thread {
            val outcome = runCatching { assistant.run(withUser, key) }
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                outcome
                    .onSuccess {
                        // Without the system message, which is rebuilt each turn.
                        assistantHistory = it.history.drop(1)
                        chat = chat + ChatMessage("assistant", it.reply)
                        if (!it.changed) undoPoint = null
                    }
                    .onFailure { e ->
                        undoPoint = null
                        chat = chat + ChatMessage(
                            "assistant",
                            e.message ?: "Something went wrong talking to OpenAI."
                        )
                    }
                chatBusy = false
            }
        }.start()
    }

    // ── what the assistant drives ──────────────────────────────────────
    // The same paths the screens use, so a change made in chat syncs, moves
    // balances and respects profiles exactly as a tap would.

    /** State before the assistant's last change, so a mistake is recoverable. */
    private var undoPoint: PersistedState? = null

    val canUndoAssistant: Boolean get() = undoPoint != null

    fun markUndoPoint() { undoPoint = persisted }

    fun undoAssistant(): Boolean {
        val prev = undoPoint ?: return false
        persisted = prev.also { ownRevision = store.save(it) }
        undoPoint = null
        pushNow()
        return true
    }

    fun accountNamed(name: String): Account? =
        visibleAccounts.firstOrNull { it.name.equals(name, true) }
            ?: visibleAccounts.firstOrNull { it.name.contains(name, true) }

    fun entryById(id: String): Entry? = entries.firstOrNull { it.id == id }
    fun loanById(id: String): Loan? = loans.firstOrNull { it.id == id }
    fun txnById(id: String): Txn? = txns.firstOrNull { it.id == id }

    /**
     * Finds the category, or makes one. Never falls back to a catch-all: an
     * "Other" that swallows everything unrecognised makes budgets meaningless.
     */
    fun categoryNamed(name: String): String {
        categories.firstOrNull { it.equals(name, true) }?.let { return it }
        categories.firstOrNull { name.contains(it, true) }?.let { return it }
        val invented = categoryForParty(name, categories)
        addCategoryNamed(invented)
        return invented
    }

    /** Records money that has already moved, as the one-time form does. */
    fun addTransactionDirect(
        amount: Double,
        category: String,
        credit: Boolean,
        accountId: String,
        note: String,
        dateIso: String
    ): Txn {
        return addTxn { id ->
            Txn(
                id = id,
                date = dateIso,
                kind = if (credit) "INCOME" else "EXPENSE",
                amount = amount,
                category = category,
                fromAccountId = if (credit) "" else accountId,
                toAccountId = if (credit) accountId else "",
                period = Ledger.cycleOf(dateIso, cycleResetDay),
                note = note,
                // A time only when it happened today. Stamping a back-dated
                // payment with midnight would show an hour nobody recorded.
                at = if (dateIso == today()) System.currentTimeMillis() else 0L
            )
        }
    }

    /** Changes a recorded transaction. Nulls leave a field alone. */
    fun updateTransaction(
        id: String,
        amount: Double? = null,
        category: String? = null,
        accountId: String? = null,
        note: String? = null,
        dateIso: String? = null
    ): Txn? {
        val t = txnById(id) ?: return null
        val credit = t.kind == "INCOME"
        val updated = t.copy(
            amount = amount ?: t.amount,
            category = category ?: t.category,
            note = note ?: t.note,
            date = dateIso ?: t.date,
            period = (dateIso ?: t.date).take(7),
            at = if (dateIso != null) millisOfDate(dateIso) else t.at,
            fromAccountId = if (accountId != null && !credit) accountId else t.fromAccountId,
            toAccountId = if (accountId != null && credit) accountId else t.toAccountId
        )
        update { s -> s.copy(txns = s.txns.map { if (it.id == id) updated else it }) }
        sync.upsertTxn(updated)
        return updated
    }

    /** Adds a recurring entry — the plan, not a payment. */
    fun addCommitmentDirect(
        amount: Double,
        category: String,
        everyMonths: Int,
        type: String,
        joint: Boolean,
        note: String,
        dueDate: String = ""
    ): Entry {
        val person = if (joint) "Joint" else activeProfile ?: "Me"
        val bucket = if (joint) "JOINT" else "PERSONAL"
        val entry = Entry(
            id = newId("e"),
            person = person,
            type = type,
            bucket = bucket,
            category = category,
            amount = amount,
            frequency = if (everyMonths >= 12) "ANNUAL" else "MONTHLY",
            note = note,
            accountId = defaultAccountFor(person, bucket),
            periodMonths = everyMonths.coerceIn(1, 12),
            dueDate = dueDate
        )
        update { s -> s.copy(entries = s.entries + entry) }
        return entry
    }

    fun updateCommitment(
        id: String,
        amount: Double? = null,
        category: String? = null,
        everyMonths: Int? = null,
        note: String? = null,
        dueDate: String? = null
    ): Entry? {
        val e = entryById(id) ?: return null
        val months = everyMonths?.coerceIn(1, 12) ?: e.everyMonths
        val updated = e.copy(
            amount = amount ?: e.amount,
            category = category ?: e.category,
            note = note ?: e.note,
            periodMonths = months,
            dueDate = dueDate ?: e.dueDate,
            frequency = if (months >= 12) "ANNUAL" else "MONTHLY"
        )
        update { s -> s.copy(entries = s.entries.map { if (it.id == id) updated else it }) }
        return updated
    }

    /**
     * Confirms this month's payment without the sheet, the assistant having
     * already established which accounts are involved.
     */
    fun confirmDirect(id: String, fromId: String?, toId: String?): String {
        loanById(id)?.let { l ->
            if (isLoanConfirmed(l.id)) return "${l.name} was already paid this month."
            confirmLoan(l)
            return "Paid ${inr(l.monthlyEmi)} for ${l.name}."
        }
        val e = entryById(id) ?: return "No commitment with that id."
        if (isConfirmed(e.id)) return "${e.note.ifEmpty { e.category }} was already done this month."

        val kind = confirmKindFor(e)
        val from = fromId ?: e.accountId.ifEmpty { defaultAccountFor(e.person, e.bucket) }
        val to = toId ?: accounts.firstOrNull { it.person == "Joint" && it.id != from }?.id.orEmpty()
        if (kind == "TRANSFER" && to.isEmpty()) {
            return "Tell me which account the set-aside should go to."
        }
        addTxn { generated ->
            Txn(
                id = generated, date = today(), kind = kind, amount = e.monthly,
                category = e.category,
                fromAccountId = if (kind == "INCOME") "" else from,
                toAccountId = when (kind) {
                    "INCOME" -> from
                    "TRANSFER" -> to
                    else -> ""
                },
                entryId = e.id, period = cycleFor(e.person),
                note = e.note.ifEmpty { e.category },
                at = System.currentTimeMillis()
            )
        }
        return if (kind == "TRANSFER") "Set aside ${inr(e.monthly)} for ${e.category}."
        else "Confirmed ${inr(e.monthly)} for ${e.category}."
    }

    fun addAccountDirect(name: String, balance: Double, tail: String, joint: Boolean): Account {
        val person = if (joint) "Joint" else activeProfile ?: "Me"
        val a = Account(newId("a"), name, ownerLabel(person), person, balance, tail)
        update { s -> s.copy(accounts = s.accounts + a) }
        return a
    }

    fun addCardDirect(
        name: String, limit: Double, balance: Double, minDue: Double, due: String, tail: String
    ): Card {
        val c = Card(
            id = newId("cc"), name = name, owner = activeProfile ?: "Me", limit = limit,
            balance = balance, minDue = minDue, due = due, numberTail = tail
        )
        update { s -> s.copy(cards = s.cards + c) }
        return c
    }

    fun addLoanDirect(
        name: String,
        emi: Double,
        total: Int,
        remaining: Int,
        cardName: String = "",
        accountName: String = "",
        dueDate: String = ""
    ): Loan {
        val person = activeProfile ?: "Me"
        // A named card wins: it is the more specific thing to have said, and a
        // card EMI must not also be debited from an account.
        val card = cards.firstOrNull { it.name.equals(cardName, true) }
            ?: cards.firstOrNull { cardName.isNotBlank() && it.name.contains(cardName, true) }
        val account = accounts.firstOrNull { it.name.equals(accountName, true) }
            ?: accounts.firstOrNull { accountName.isNotBlank() && it.name.contains(accountName, true) }
        val l = Loan(
            id = newId("l"),
            name = name,
            person = person,
            monthlyEmi = emi,
            totalMonths = total,
            remainingMonths = remaining.coerceIn(0, total),
            accountId = if (card != null) "" else (account?.id ?: defaultAccountFor(person, "JOINT")),
            cardId = card?.id.orEmpty(),
            dueDate = dueDate
        )
        update { s -> s.copy(loans = s.loans + l) }
        return l
    }

    fun updateAccountDirect(id: String, newName: String?, balance: Double?, tail: String?): Account? {
        val a = accounts.firstOrNull { it.id == id } ?: return null
        val updated = a.copy(
            name = newName ?: a.name,
            openingBalance = balance ?: a.openingBalance,
            numberTail = tail ?: a.numberTail
        )
        update { s -> s.copy(accounts = s.accounts.map { if (it.id == id) updated else it }) }
        return updated
    }

    // ── clearing the sample data ───────────────────────────────────────
    // The app ships with fabricated salaries, accounts, cards and loans so the
    // screens aren't empty on first run. Left in place they inflate every
    // planned figure and every balance once real numbers arrive.

    private val sampleEntryIds = Seed.entries.map { it.id }.toSet()
    private val sampleAccountIds = Seed.accounts.map { it.id }.toSet()
    private val sampleLoanIds = Seed.loans.map { it.id }.toSet()
    private val sampleCardIds = Seed.cards.map { it.id }.toSet()

    var sampleNote by mutableStateOf(""); private set

    fun clearSamples() { sampleNote = removeSampleData() }

    /** How much of the sample data is still here. */
    val sampleDataCount: Int
        get() = entries.count { it.id in sampleEntryIds } +
            accounts.count { it.id in sampleAccountIds } +
            loans.count { it.id in sampleLoanIds } +
            cards.count { it.id in sampleCardIds }

    /**
     * Removes what the app invented, keeping everything you added.
     *
     * A sample account carrying real transactions is kept rather than deleted:
     * removing it would either strand those records or silently move the money
     * somewhere else. Renaming it is the right move, and the message says so.
     */
    fun removeSampleData(): String {
        val usedAccounts = persisted.txns.flatMap { listOf(it.fromAccountId, it.toAccountId) }
            .filter { it.isNotEmpty() }.toSet()
        val usedCards = persisted.txns.map { it.cardId }.filter { it.isNotEmpty() }.toSet()

        val accountsToGo = sampleAccountIds - usedAccounts
        val cardsToGo = sampleCardIds - usedCards
        val kept = (sampleAccountIds intersect usedAccounts).size +
            (sampleCardIds intersect usedCards).size

        val removed = entries.count { it.id in sampleEntryIds } +
            loans.count { it.id in sampleLoanIds } +
            accounts.count { it.id in accountsToGo } +
            cards.count { it.id in cardsToGo }

        if (removed == 0 && kept == 0) return "There's no sample data left."

        update { s ->
            s.copy(
                entries = s.entries.filterNot { it.id in sampleEntryIds },
                loans = s.loans.filterNot { it.id in sampleLoanIds },
                accounts = s.accounts.filterNot { it.id in accountsToGo },
                cards = s.cards.filterNot { it.id in cardsToGo }
            )
        }
        pushNow()

        return buildString {
            append("Removed $removed sample record(s).")
            if (kept > 0) {
                append(" Kept $kept that your transactions refer to — rename ")
                append("them on Home rather than deleting, so nothing is stranded.")
            }
        }
    }

    fun addCategoryNamed(name: String) {
        if (name.isBlank() || categories.any { it.equals(name, true) }) return
        update { s -> s.copy(categories = s.categories + name.trim()) }
    }
}

