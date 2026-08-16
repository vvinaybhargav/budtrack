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
import com.vinay.fintrack.data.OpenAi
import com.vinay.fintrack.data.PersistedState
import com.vinay.fintrack.data.SalaryOverride
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
import com.vinay.fintrack.data.STANDARD_CATEGORIES
import com.vinay.fintrack.data.UNCATEGORISED
import com.vinay.fintrack.data.payeeKey
import com.vinay.fintrack.data.inr
import com.vinay.fintrack.data.dayFirstOf
import com.vinay.fintrack.data.isoFromDayFirst
import com.vinay.fintrack.data.millisOfDate
import com.vinay.fintrack.data.newId
import com.vinay.fintrack.data.today
import com.vinay.fintrack.data.todayDayFirst
import com.vinay.fintrack.data.ownerLabel
import com.vinay.fintrack.data.prettyDate
import com.vinay.fintrack.data.resolveNextDueDate
import com.vinay.fintrack.data.calculateSixMonthOutlook
import com.vinay.fintrack.data.SixMonthOutlook
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
    val dueText: String = "",
    val statementDayText: String = "20",
    val statementAmountText: String = ""
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

    val smsSuggestions: Map<String, String> get() = persisted.smsSuggestions
    val smsRules: Map<String, String> get() = persisted.smsRules

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
    /**
     * Re-files imports onto the account their message names.
     *
     * [onlyUnfiled] limits it to rows that have no account at all. The button
     * in Settings re-checks everything, which is what it is for; running after
     * you set one account must not quietly move rows you had corrected by hand
     * back to what the digits say.
     */
    fun rematchImports(onlyUnfiled: Boolean = false) {
        val moved = mutableListOf<Txn>()
        persisted.txns.forEach { t ->
            if (t.source.isEmpty()) return@forEach
            if (onlyUnfiled && !needsAccount(t)) return@forEach
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
            // The remembered digits go with it: the row is filed, so the hint
            // about which account the bank named has done its job.
            moved += if (credit) t.copy(toAccountId = account.accountId, accountTail = "")
            else t.copy(fromAccountId = account.accountId, cardId = "", accountTail = "")
        }

        if (moved.isEmpty()) {
            // Silent when it ran by itself: a note nobody asked for, saying
            // nothing happened, is just noise on the screen.
            if (!onlyUnfiled) {
                scanNote = "Nothing to move — the imports already match, or those " +
                    "accounts have no digits set."
            }
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
        smsAsked = false,
        // The conversation is yours and quotes balances and payees; it has no
        // business in a document the household shares.
        chats = emptyMap()
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
        smsAsked = persisted.smsAsked,
        chats = persisted.chats
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
                // Keyed by name, so they have to move with it. Leaving the salary
                // day behind was the costly one: the cycle silently fell back to
                // the 1st, which changes when every commitment becomes payable
                // again — a rename quietly rewriting the month.
                salaryDays = s.salaryDays[old]?.let { s.salaryDays - old + (new to it) }
                    ?: s.salaryDays,
                chats = s.chats[old]?.let { s.chats - old + (new to it) } ?: s.chats,
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
                update {
                    it.copy(
                        profiles = it.profiles - name,
                        // Nothing left keyed to a profile that no longer exists.
                        salaryDays = it.salaryDays - name,
                        chats = it.chats - name
                    )
                }
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

    val salaryAmount: Double
        get() = persisted.salaries[activeProfile.orEmpty()] ?: 0.0

    fun setSalaryAmount(amount: Double) {
        update { s -> s.copy(salaries = s.salaries + (activeProfile.orEmpty() to amount)) }
    }

    fun getSalaryOverride(profile: String, yearMonth: String): SalaryOverride? {
        return persisted.salaryOverrides["${profile}_$yearMonth"]
    }

    fun setSalaryOverride(profile: String, yearMonth: String, amount: Double?, resetDay: Int?) {
        update { s ->
            val key = "${profile}_$yearMonth"
            val current = s.salaryOverrides[key]
            val defaultAmt = s.salaries[profile] ?: 0.0
            val defaultDay = s.salaryDays[profile] ?: s.cycleResetDay
            
            if (amount == null && resetDay == null) {
                s.copy(salaryOverrides = s.salaryOverrides - key)
            } else {
                val nextOverride = SalaryOverride(
                    amount = amount ?: current?.amount ?: defaultAmt,
                    resetDay = resetDay ?: current?.resetDay ?: defaultDay
                )
                s.copy(salaryOverrides = s.salaryOverrides + (key to nextOverride))
            }
        }
    }

    fun salaryResetDayFor(person: String, onDate: String = ""): Int {
        if (onDate.isNotEmpty()) {
            val yearMonth = onDate.substring(0, 7)
            val override = persisted.salaryOverrides["${person}_$yearMonth"]
            if (override != null) return override.resetDay
        }
        return persisted.salaryDays[person] ?: persisted.cycleResetDay
    }

    /**
     * The cycle a given person is on. Two salaries rarely land on the same
     * day, so whether something is still owed this month depends on whose it
     * is — not on who happens to be holding the phone.
     */
    fun cycleFor(person: String): String =
        Ledger.cycleOf(today(), salaryResetDayFor(person))

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
            Ledger.instalmentsUntil(today(), e.nextDue, salaryResetDayFor(e.person)) <= 1
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
            dueText = if (e.dueDate.isEmpty()) "" else e.dueDate.split("-").getOrNull(2)?.toIntOrNull()?.toString() ?: ""
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
    val draftDueIso: String get() {
        val parsed = isoFromDayFirst(draft.dueText)
        if (parsed != null) return parsed
        val day = draft.dueText.toIntOrNull()
        if (day != null && day in 1..31) return resolveNextDueDate(day, today())
        return ""
    }

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
        get() = if (draftDueIso.isNotEmpty()) Ledger.instalmentsUntil(today(), draftDueIso, salaryResetDayFor(draft.person))
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
        val dueDay = newLoanDraft.dueText.toIntOrNull() ?: 0
        val resolvedDueDate = if (dueDay in 1..31) resolveNextDueDate(dueDay, today()) else ""
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
                    dueDate = resolvedDueDate,
                    dueDay = dueDay,
                    lastProcessedMonth = ""
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
        val dueDay = newCardDraft.dueText.toIntOrNull() ?: 0
        val resolvedDueDate = if (dueDay in 1..31) resolveNextDueDate(dueDay, today()) else ""
        val statementDay = newCardDraft.statementDayText.toIntOrNull() ?: 20
        val statementAmount = newCardDraft.statementAmountText.toDoubleOrNull() ?: 0.0
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
                    dueDate = resolvedDueDate,
                    statementDay = statementDay,
                    statementAmount = statementAmount
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
            dueText = if (l.dueDay > 0) l.dueDay.toString() else ""
        )
    }

    fun cancelEditLoan() { editingLoanId = null }

    fun saveLoan() {
        val id = editingLoanId ?: return
        val dueDay = loanDraft.dueText.toIntOrNull() ?: 0
        val resolvedDueDate = if (dueDay in 1..31) resolveNextDueDate(dueDay, today()) else ""
        update { s ->
            s.copy(loans = s.loans.map {
                if (it.id == id) it.copy(
                    name = loanDraft.name, person = loanDraft.person,
                    monthlyEmi = loanDraft.emiText.toDoubleOrNull() ?: 0.0,
                    totalMonths = loanDraft.totalMonthsText.toIntOrNull() ?: 1,
                    remainingMonths = loanDraft.remainingMonthsText.toIntOrNull() ?: 0,
                    accountId = if (loanDraft.cardId.isNotEmpty()) "" else loanDraft.accountId,
                    cardId = loanDraft.cardId,
                    dueDate = resolvedDueDate,
                    dueDay = dueDay
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
            dueText = if (c.dueDate.isEmpty()) "" else c.dueDate.split("-").getOrNull(2)?.toIntOrNull()?.toString() ?: "",
            statementDayText = c.statementDay.toString(),
            statementAmountText = if (c.statementAmount > 0.0) c.statementAmount.toLong().toString() else ""
        )
    }

    fun cancelEditCard() { editingCardId = null }

    fun saveCard() {
        val id = editingCardId ?: return
        val dueDay = cardDraft.dueText.toIntOrNull() ?: 0
        val resolvedDueDate = if (dueDay in 1..31) resolveNextDueDate(dueDay, today()) else ""
        val statementDay = cardDraft.statementDayText.toIntOrNull() ?: 20
        val statementAmount = cardDraft.statementAmountText.toDoubleOrNull() ?: 0.0
        update { s ->
            s.copy(cards = s.cards.map {
                if (it.id == id) it.copy(
                    name = cardDraft.name, owner = cardDraft.owner,
                    limit = cardDraft.limitText.toDoubleOrNull() ?: 0.0,
                    balance = cardDraft.balanceText.toDoubleOrNull() ?: 0.0,
                    minDue = cardDraft.minDueText.toDoubleOrNull() ?: 0.0,
                    due = cardDraft.due,
                    numberTail = cardDraft.numberTail,
                    dueDate = resolvedDueDate,
                    statementDay = statementDay,
                    statementAmount = statementAmount
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
    private fun inScopeFor(person: String, view: String) =
        if (view == "JOINT") person == "Joint" else person == activeProfile

    fun scopedEntriesFor(view: String): List<Entry> =
        visibleEntries.filter { inScopeFor(it.person, view) && !it.closed }

    fun scopedAccountsFor(view: String): List<Account> =
        visibleAccounts.filter { inScopeFor(it.person, view) }

    fun scopedLoansFor(view: String): List<Loan> =
        visibleLoans.filter { inScopeFor(it.person, view) }

    fun scopedCardsFor(view: String): List<Card> =
        visibleCards.filter { inScopeFor(it.owner, view) }

    private fun inScope(person: String) = inScopeFor(person, bucketView)

    val scopedEntries: List<Entry> get() = scopedEntriesFor(bucketView)
    val scopedAccounts: List<Account> get() = scopedAccountsFor(bucketView)
    val scopedLoans: List<Loan> get() = scopedLoansFor(bucketView)
    val scopedCards: List<Card> get() = scopedCardsFor(bucketView)

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
    fun plannedIncomeFor(view: String, onDate: String = ""): Double {
        if (onDate.isNotEmpty()) {
            val yearMonth = onDate.substring(0, 7)
            return if (view == "JOINT") {
                persisted.profiles.keys.sumOf { p ->
                    val override = persisted.salaryOverrides["${p}_$yearMonth"]
                    override?.amount ?: (persisted.salaries[p] ?: 0.0)
                }
            } else {
                val activeP = activeProfile.orEmpty()
                val override = persisted.salaryOverrides["${activeP}_$yearMonth"]
                override?.amount ?: (persisted.salaries[activeP] ?: 0.0)
            }
        }
        return if (view == "JOINT") {
            persisted.salaries.values.sum()
        } else {
            persisted.salaries[activeProfile.orEmpty()] ?: 0.0
        }
    }

    fun plannedExpenseFor(view: String): Double =
        plannedRecurringFor(view) + plannedSetAsideFor(view) + plannedLoansFor(view)

    fun plannedRecurringFor(view: String): Double =
        scopedEntriesFor(view)
            .filter { it.type == "EXPENSE" && !it.isSetAside && !coveredByLoan(it) }
            .sumOf { it.monthly }

    fun plannedSetAsideFor(view: String): Double =
        annualSetAsidesFor(view).sumOf { it.monthly }

    fun plannedLoansFor(view: String): Double =
        scopedLoansFor(view).sumOf { it.monthlyEmi }

    fun annualSetAsidesFor(view: String): List<Entry> =
        scopedEntriesFor(view).filter { it.isSetAside && it.type != "INCOME" }

    fun annualSetAsideDoneFor(view: String): Double =
        annualSetAsidesFor(view).sumOf { setAsideDone(it).coerceAtMost(it.monthly) }

    fun totalBalanceFor(view: String): Double =
        scopedAccountsFor(view).sumOf { balanceOf(it) }

    fun monthTotalsFor(view: String): Ledger.MonthTotals {
        return Ledger.monthTotals(
            persisted.txns,
            cycle(),
            scopedAccountsFor(view).map { it.id }.toSet(),
            scopedCardsFor(view).map { it.id }.toSet(),
            INVEST_CATEGORIES
        )
    }

    val plannedIncome: Double get() = plannedIncomeFor(bucketView)
    val plannedExpense: Double get() = plannedExpenseFor(bucketView)
    val plannedRecurring: Double get() = plannedRecurringFor(bucketView)
    val plannedSetAside: Double get() = plannedSetAsideFor(bucketView)
    val plannedLoans: Double get() = plannedLoansFor(bucketView)

    /**
     * Real spending this cycle that no commitment or loan accounts for — the
     * groceries and the coffees, as opposed to the bills you already knew about.
     */
    val unplannedSpent: Double
        get() {
            val ids = scopedAccounts.map { it.id }.toSet()
            val cardIds = scopedCards.map { it.id }.toSet()
            return Ledger.paise(
                persisted.txns.filter {
                    it.month == cycle() && it.entryId.isEmpty() && it.loanId.isEmpty() &&
                        it.source != Ledger.CARD_PAYMENT &&
                        (it.kind == "EXPENSE" || it.kind == "REFUND")
                }.sumOf {
                    val counts = if (it.kind == "REFUND") {
                        it.toAccountId in ids || (it.cardId.isNotEmpty() && it.cardId in cardIds)
                    } else {
                        it.fromAccountId in ids || (it.cardId.isNotEmpty() && it.cardId in cardIds)
                    }
                    if (!counts) 0.0 else if (it.kind == "REFUND") -it.amount else it.amount
                }
            )
        }

    /** Everything expected out this month, planned and unplanned alike. */
    val monthOut: Double
        get() = Ledger.paise(
            plannedRecurring + plannedSetAside + plannedLoans + unplannedSpent
        )

    /** What is left of the month's expected income once all of it is met. */
    val monthLeft: Double get() = Ledger.paise(plannedIncome - monthOut)

    /**
     * What next month asks for, which is rarely the same.
     *
     * A set-aside's share climbs as its due date nears — fewer months left to
     * spread the same bill over — so seeing next month now is the difference
     * between noticing in advance and noticing on the day.
     */
    val nextMonthOut: Double get() = outlook(1).firstOrNull()?.out ?: 0.0

    /** One month ahead: what it asks for, and what would be left of it. */
    class OutlookMonth(
        val label: String,
        val recurring: Double,
        val loans: Double,
        val setAside: Double,
        val income: Double,
        /** A loan that makes its last payment this month, worth seeing coming. */
        val loanEnding: String
    ) {
        val out: Double get() = Ledger.paise(recurring + loans + setAside)
        val left: Double get() = Ledger.paise(income - out)
    }

    /**
     * The months ahead, from what is already known.
     *
     * Recurring bills, EMIs and set-asides are the parts of a month you can
     * actually see coming, and none of them stays still: a set-aside's share
     * climbs as its due date nears and drops back once paid, and an EMI stops
     * altogether when the loan runs out. Spending is left out — an average of
     * past months would look like a forecast without being one.
     */
    fun outlook(months: Int = 6): List<OutlookMonth> = (1..months).map { ahead ->
        val on = Ledger.addMonths(today(), ahead)

        val setAside = annualSetAsides.sumOf { e ->
            if (e.dueDate.isEmpty()) {
                Ledger.monthlyShare(e.amount, e.everyMonths)
            } else {
                val nextDueFromToday = Ledger.nextDue(e.dueDate, e.everyMonths, today())
                if (on > nextDueFromToday) {
                    Ledger.monthlyShare(e.amount, e.everyMonths)
                } else {
                    e.monthly(salaryResetDayFor(e.person, on))
                }
            }
        }
        // Still paying only while instalments remain: an EMI that ends in March
        // must not go on being subtracted in April.
        val running = scopedLoans.filter { it.remainingMonths > ahead }
        val ending = scopedLoans.firstOrNull { it.remainingMonths == ahead }

        OutlookMonth(
            label = monthLabel(on),
            recurring = plannedRecurring,
            loans = Ledger.paise(running.sumOf { it.monthlyEmi }),
            setAside = Ledger.paise(setAside),
            income = plannedIncomeFor(bucketView, on),
            loanEnding = ending?.name.orEmpty()
        )
    }

    fun getSixMonthOutlook(): SixMonthOutlook {
        val salary = if (bucketView == "JOINT") {
            persisted.salaries.values.sum()
        } else {
            persisted.salaries[activeProfile.orEmpty()] ?: 0.0
        }
        return calculateSixMonthOutlook(salary, scopedLoans, scopedEntries, today(), persisted.salaryDays)
    }

    /** "Sep 2026" from an ISO date. */
    private fun monthLabel(iso: String): String {
        val parts = iso.split("-")
        if (parts.size < 2) return iso
        val names = listOf(
            "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
        )
        val month = parts[1].toIntOrNull()?.minus(1)?.coerceIn(0, 11) ?: return iso
        return "${names[month]} ${parts[0]}"
    }

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
    fun coveredByLoan(e: Entry): Boolean =
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
    val annualSetAsides: List<Entry> get() = annualSetAsidesFor(bucketView)

    val annualSetAsideMonthly: Double get() = plannedSetAsideFor(bucketView)

    /** What has actually been put by, part-payments included, rather than a
     *  count of the ones fully met. */
    val annualSetAsideDone: Double get() = annualSetAsideDoneFor(bucketView)

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
        // A row with no account yet belongs to neither side — it has no account
        // to take a side from — so it shows on both. It was appearing only under
        // Joint, which meant the "needs an account" line could sit above a list
        // that did not contain any of them.
        needsAccount(t) ||
            Ledger.inBucket(txnPersons(t), activeProfile, bucketView == "PERSONAL")

    private fun needsAccount(t: Txn): Boolean =
        t.source.startsWith("sms") && t.cardId.isEmpty() &&
            t.fromAccountId.isEmpty() && t.toAccountId.isEmpty()

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
                val matchesCategory = when (entriesCategoryFilter) {
                    null -> true
                    "Needs Account" -> needsAccount(t)
                    else -> t.category == entriesCategoryFilter
                }
                matchesCategory &&
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

    fun startEditTxn(id: String) {
        editingTxnId = id
        // Or the previous row's "Filed under Groceries." greets this one.
        categoriseNote = ""
    }

    /**
     * The payee to learn a category against — the bank's name for them, not
     * your description of the payment.
     *
     * Rewriting a description to "milk and eggs" replaces the note, so learning
     * from the note taught the app about that phrase. The next message from the
     * same shop still says SRI BALAJI TRADERS and matched nothing, and the
     * lesson was never used again. The original name is recovered from the
     * message the row came from.
     */
    private fun payeeOf(t: Txn): String {
        val body = persisted.smsBodies[t.id].orEmpty()
        if (body.isNotEmpty()) {
            parseBankSms(body)?.party?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return t.note
    }

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
        categoriseNote = ""
    }

    fun cancelEditTxn() {
        editingTxnId = null
        categoriseNote = ""
    }

    private fun replaceTxn(updated: Txn) {
        update { s -> s.copy(txns = s.txns.map { if (it.id == updated.id) updated else it }) }
        sync.upsertTxn(updated)
    }

    /**
     * Puts a transaction on an account, and learns the digits while it is here.
     *
     * A message that matched nothing still quoted an account number. Telling
     * the app which account that was records the digits against it, so every
     * later message from the same account matches on its own — the answer is
     * needed once rather than every month.
     */
    fun setTxnAccount(accountId: String) {
        val t = editingTxn ?: return
        val placed = if (t.kind == "INCOME") t.copy(toAccountId = accountId)
        else t.copy(fromAccountId = accountId)
        replaceTxn(placed.copy(accountTail = ""))

        val digits = t.accountTail
        if (digits.isBlank()) return
        val account = accounts.firstOrNull { it.id == accountId } ?: return
        if (account.numberTail.isNotBlank()) return
        update { s ->
            s.copy(accounts = s.accounts.map {
                if (it.id == accountId) it.copy(numberTail = digits) else it
            })
        }
        // Everything else still waiting on an account can be filed now. Only
        // those: rows you have already placed by hand stay where you put them.
        rematchImports(onlyUnfiled = true)
    }

    /** Transactions the rules could not file. */
    val uncategorisedTxns: List<Txn>
        get() = txns.filter { it.category == UNCATEGORISED || it.category.isBlank() }

    var sortingCategories by mutableStateOf(false); private set
    var sortMessage by mutableStateOf(""); private set

    /**
     * Asks OpenAI to file the payees the built-in rules didn't recognise.
     *
     * One request for all of them rather than one each: the payees are a short
     * list, and asking about thirty of them costs barely more than asking about
     * one. Only the payee names go — no amounts, no accounts, no balances.
     *
     * The answers are learned, not just applied, so the same biller is never
     * sent again. Categories the model invents are ignored: it chooses from
     * yours, and anything else stays Uncategorised for you.
     */
    fun sortCategoriesWithAi() {
        if (sortingCategories) return
        if (!chatReady) { sortMessage = "Add an OpenAI key in Settings first."; return }
        // The bank's name for them, so what comes back is learned against the
        // string a future message will actually carry.
        val payees = uncategorisedTxns.map { payeeOf(it) }
            .filter { it.isNotBlank() }
            .distinctBy { payeeKey(it) }
            .take(40)
        if (payees.isEmpty()) { sortMessage = "Nothing left to sort."; return }

        sortingCategories = true
        sortMessage = "Sorting ${payees.size} payees…"
        val key = persisted.openaiKeyText
        val known = categories.filterNot { it == UNCATEGORISED }

        Thread {
            val outcome = runCatching {
                OpenAi(key).ask(
                    instruction = "You sort Indian payee names into spending categories. " +
                        "Reply with one line per payee, exactly 'payee = category'. " +
                        "The category MUST be one of: ${known.joinToString(", ")}. " +
                        "If none genuinely fits, write 'payee = $UNCATEGORISED'. " +
                        "Never invent a category and never explain.",
                    question = payees.joinToString("\n")
                )
            }
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                sortingCategories = false
                outcome
                    .onSuccess { sortMessage = applyCategoryAnswers(it, known) }
                    .onFailure { sortMessage = it.message ?: "Could not reach OpenAI." }
            }
        }.start()
    }

    /** Reads "payee = category" lines, keeping only categories that exist. */
    private fun applyCategoryAnswers(reply: String, known: List<String>): String {
        var filed = 0
        reply.lineSequence().forEach { line ->
            val parts = line.split("=", limit = 2)
            if (parts.size < 2) return@forEach
            val payee = parts[0].trim().trim('-', '•', '*', ' ')
            val category = known.firstOrNull { it.equals(parts[1].trim(), true) } ?: return@forEach
            if (payee.isBlank()) return@forEach
            learnPayeeCategory(payee, category)
            filed++
        }
        return if (filed == 0) "Nothing could be filed confidently — file them yourself."
        else "Filed $filed payees. They stay filed from now on."
    }

    fun clearSortMessage() { sortMessage = "" }

    /** Imported rows with no account yet — the only ones that need you. */
    val txnsNeedingAccount: List<Txn> get() = txns.filter { needsAccount(it) }

    /**
     * Files a transaction, and remembers the choice.
     *
     * Correcting one Eastern Power payment settles Eastern Power for good —
     * every future message from that payee is filed there, and the ones already
     * sitting uncategorised are moved with it. Filing the same biller month
     * after month was the tedium this app existed to remove.
     */
    fun setTxnCategory(category: String) {
        val txn = editingTxn ?: return
        replaceTxn(txn.copy(category = category))
        val payee = payeeOf(txn)
        learnPayeeCategory(payee, category)

        if (txn.source.startsWith("sms") && payee.isNotBlank() && category != UNCATEGORISED && category.isNotBlank()) {
            val cleanPattern = payee.trim()
            update { s ->
                val nextRules = s.smsRules + (cleanPattern to category)
                val updatedTxns = s.txns.map { t ->
                    if (t.source == "sms" && t.note.lowercase().contains(cleanPattern.lowercase())) {
                        t.copy(category = category)
                    } else {
                        t
                    }
                }
                s.copy(smsRules = nextRules, txns = updatedTxns)
            }
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(appContext, "Category saved for future transactions!", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** Teaches the payee, then re-files anything unsorted from the same one. */
    private fun learnPayeeCategory(payee: String, category: String) {
        val key = payeeKey(payee)
        if (key.isBlank() || category.isBlank() || category == UNCATEGORISED) return
        // Matched on the bank's name too, not the note: a row whose description
        // you rewrote is still the same shop, and should move with the rest.
        val same = { t: Txn -> t.category == UNCATEGORISED && payeeKey(payeeOf(t)) == key }
        val moved = persisted.txns.filter(same)
        update { s ->
            s.copy(
                payeeCategories = s.payeeCategories + (key to category),
                txns = s.txns.map { if (same(it)) it.copy(category = category) else it }
            )
        }
        moved.forEach { sync.upsertTxn(it.copy(category = category)) }
    }

    /**
     * The description, and the category that follows from it.
     *
     * Optional: leave it and the row keeps whatever the bank called the payee.
     * But describing something is exactly the moment its category becomes
     * obvious, so writing one files it — no second button, no second save.
     * Only when it is still unfiled: a category you chose is never overruled.
     */
    fun setTxnNote(note: String) {
        val txn = editingTxn ?: return
        val text = note.trim()
        var category = txn.category
        
        if (text.isNotBlank() && (category == UNCATEGORISED || category.isBlank())) {
            val lowerText = text.lowercase()
            // Check rules first
            val matchedRuleCategory = smsRules.entries.firstOrNull { (pattern, _) ->
                lowerText.contains(pattern) || pattern.contains(lowerText)
            }?.value
            
            if (matchedRuleCategory != null) {
                category = matchedRuleCategory
            } else {
                // Check past transactions
                val pastTxnCategory = txns.firstOrNull {
                    it.note.trim().equals(text, ignoreCase = true) &&
                        it.category != UNCATEGORISED &&
                        it.category.isNotBlank()
                }?.category
                if (pastTxnCategory != null) {
                    category = pastTxnCategory
                }
            }
        }
        
        replaceTxn(txn.copy(note = text, category = category))
        
        if (category == UNCATEGORISED && text.isNotBlank() && chatReady) {
            categoriseFromDescription(txn.id, text)
        }
    }

    /** Shown in the edit sheet while a description is being filed. */
    var categorisingTxnId by mutableStateOf(""); private set
    var categoriseNote by mutableStateOf(""); private set

    /**
     * Asks for one description's category, in the background.
     *
     * A single short question, so it costs almost nothing and finishes while
     * you are still looking at the row. The answer is learned as well as
     * applied, so the same payee is never asked about twice.
     */
    private fun categoriseFromDescription(txnId: String, description: String) {
        if (!chatReady || categorisingTxnId.isNotEmpty()) return
        val known = categories.filterNot { it == UNCATEGORISED }
        if (known.isEmpty()) return

        categorisingTxnId = txnId
        categoriseNote = "Finding a category…"
        val key = persisted.openaiKeyText

        Thread {
            val outcome = runCatching {
                OpenAi(key).ask(
                    instruction = "You file Indian payments into spending categories. " +
                        "Answer with ONE category from this list and nothing else: " +
                        known.joinToString(", ") +
                        ". If none genuinely fits, answer exactly $UNCATEGORISED.",
                    question = description,
                    maxTokens = 20
                )
            }
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                categorisingTxnId = ""
                val answer = outcome.getOrNull()?.trim().orEmpty()
                val category = known.firstOrNull { it.equals(answer, true) }
                val current = txns.firstOrNull { it.id == txnId }
                // Only if it is still unfiled: you may have chosen one yourself
                // while this was in flight, and that choice wins.
                if (category == null || current == null ||
                    (current.category != UNCATEGORISED && current.category.isNotBlank())
                ) {
                    categoriseNote = if (outcome.isFailure) "Couldn't reach OpenAI — pick one."
                    else if (category == null) "Nothing fitted — pick one."
                    else ""
                    return@post
                }
                replaceTxn(current.copy(category = category))
                learnPayeeCategory(payeeOf(current), category)
                categoriseNote = "Filed under $category."
            }
        }.start()
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
        addStandardCategories()
        if (persisted.firebaseConfigText.isNotBlank()) connectSync()
    }

    /**
     * Adds the everyday kinds of spending that were missing.
     *
     * The payee rules file petrol under Fuel and a chemist under Health, but
     * those did not exist in an older list, so each was created the first time
     * it was needed and the order looked arbitrary. Nothing is renamed or
     * removed — only the gaps are filled.
     */
    private fun addStandardCategories() {
        val missing = STANDARD_CATEGORIES.filterNot { s ->
            persisted.categories.any { it.equals(s, true) }
        }
        if (missing.isEmpty()) return
        update { it.copy(categories = it.categories + missing) }
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

    /** Three steps back, matching what the store keeps. */
    private val UNDO_DEPTH = 3

    /** Enough to scroll back through, capped so the store cannot grow forever. */
    private val KEPT_MESSAGES = 100

    private val assistant = Assistant(this)

    /**
     * What the user sees, per profile and kept on disk.
     *
     * The model's own transcript, including tool calls and their results, is
     * separate — see [assistantHistory]. That one is rebuilt from this on
     * launch rather than stored, since tool plumbing is not worth persisting
     * and the plain exchange is enough for the model to follow a thread.
     */
    val chat: List<ChatMessage>
        get() = persisted.chats[activeProfile.orEmpty()].orEmpty()

    private fun appendChat(message: ChatMessage) {
        val who = activeProfile.orEmpty()
        update { s ->
            s.copy(chats = s.chats + (who to (s.chats[who].orEmpty() + message).takeLast(KEPT_MESSAGES)))
        }
    }
    var chatBusy by mutableStateOf(false); private set

    /**
     * What the assistant is doing right now, shown while it works.
     *
     * The reply arrives in one piece at the end of the whole tool loop, so
     * without this the screen sits blank for several seconds and looks stuck.
     * Saying "Reading your accounts…" costs nothing and answers the only
     * question the wait raises.
     */
    var chatStatus by mutableStateOf(""); private set

    /** Called from the tool loop, on the main thread. */
    fun reportChatStep(step: String) { chatStatus = step }

    /**
     * A deletion the assistant wants to make, waiting for you.
     *
     * The tool descriptions asked the model to confirm first, but that was a
     * sentence in a prompt rather than a rule — nothing stopped it deleting
     * outright, and "tidy up my old transactions" could take a lot with it.
     * Now the tool describes what would go and the button here is what does it.
     */
    class PendingDeletion(
        val what: String,
        val detail: String,
        private val run: () -> Unit
    ) {
        fun execute() = run()
    }

    var pendingDeletion by mutableStateOf<PendingDeletion?>(null); private set

    /** Called by the delete tools instead of deleting. */
    fun proposeDeletion(what: String, detail: String, run: () -> Unit) {
        pendingDeletion = PendingDeletion(what, detail, run)
    }

    fun confirmDeletion() {
        val p = pendingDeletion ?: return
        markUndoPoint()
        p.execute()
        pendingDeletion = null
        appendChat(ChatMessage("assistant", "Deleted ${p.what}."))
    }

    fun cancelDeletion() {
        val p = pendingDeletion ?: return
        pendingDeletion = null
        appendChat(ChatMessage("assistant", "Left ${p.what} alone."))
    }
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
        appendLine("FinTrack Household Financial Context.")
        appendLine("Active Profile: ${activeProfile.orEmpty()}")
        
        appendLine("--- PERSONAL DATA ---")
        val pBalance = totalBalanceFor("PERSONAL")
        val pTotals = monthTotalsFor("PERSONAL")
        appendLine("Balance: ${inr(pBalance)}")
        appendLine("Recorded this month: received ${inr(pTotals.income)}, spent ${inr(pTotals.spent)}, set aside ${inr(pTotals.saved)}, invested ${inr(pTotals.invested)}")
        appendLine("Planned each month: ${inr(plannedIncomeFor("PERSONAL"))} in, ${inr(plannedExpenseFor("PERSONAL"))} out")
        
        val pEntries = scopedEntriesFor("PERSONAL").filter {
            it.frequency != "ONE_TIME" && !it.isSetAside &&
            (it.category != "EMI" || !coveredByLoan(it))
        }
        if (pEntries.isNotEmpty()) {
            appendLine("Monthly commitments:")
            pEntries.forEach {
                appendLine("  [${it.id}] ${it.category} ${inr(it.amount)}" +
                    (if (it.note.isNotBlank()) " (${it.note})" else "") +
                    if (isConfirmed(it.id)) " — done" else "")
            }
        }
        val pSetAsides = annualSetAsidesFor("PERSONAL")
        if (pSetAsides.isNotEmpty()) {
            appendLine("Set-asides (need ${inr(plannedSetAsideFor("PERSONAL"))}/mo, ${inr(annualSetAsideDoneFor("PERSONAL"))} done):")
            pSetAsides.forEach {
                appendLine("  [${it.id}] ${it.category} ${inr(it.amount)} every ${it.everyMonths} months = ${inr(it.monthly)}/mo" +
                    if (isConfirmed(it.id)) " — done" else "")
            }
        }
        val pLoans = scopedLoansFor("PERSONAL")
        if (pLoans.isNotEmpty()) {
            appendLine("Loans:")
            pLoans.forEach {
                appendLine("  [${it.id}] ${it.name} ${inr(it.monthlyEmi)}/mo, ${it.remainingMonths}/${it.totalMonths} months left" +
                    if (isLoanConfirmed(it.id)) " — paid" else "")
            }
        }

        appendLine("--- JOINT DATA ---")
        val jBalance = totalBalanceFor("JOINT")
        val jTotals = monthTotalsFor("JOINT")
        appendLine("Balance: ${inr(jBalance)}")
        appendLine("Recorded this month: received ${inr(jTotals.income)}, spent ${inr(jTotals.spent)}, set aside ${inr(jTotals.saved)}, invested ${inr(jTotals.invested)}")
        appendLine("Planned each month: ${inr(plannedIncomeFor("JOINT"))} in, ${inr(plannedExpenseFor("JOINT"))} out")
        
        val jEntries = scopedEntriesFor("JOINT").filter {
            it.frequency != "ONE_TIME" && !it.isSetAside &&
            (it.category != "EMI" || !coveredByLoan(it))
        }
        if (jEntries.isNotEmpty()) {
            appendLine("Monthly commitments:")
            jEntries.forEach {
                appendLine("  [${it.id}] ${it.category} ${inr(it.amount)}" +
                    (if (it.note.isNotBlank()) " (${it.note})" else "") +
                    if (isConfirmed(it.id)) " — done" else "")
            }
        }
        val jSetAsides = annualSetAsidesFor("JOINT")
        if (jSetAsides.isNotEmpty()) {
            appendLine("Set-asides (need ${inr(plannedSetAsideFor("JOINT"))}/mo, ${inr(annualSetAsideDoneFor("JOINT"))} done):")
            jSetAsides.forEach {
                appendLine("  [${it.id}] ${it.category} ${inr(it.amount)} every ${it.everyMonths} months = ${inr(it.monthly)}/mo" +
                    if (isConfirmed(it.id)) " — done" else "")
            }
        }
        val jLoans = scopedLoansFor("JOINT")
        if (jLoans.isNotEmpty()) {
            appendLine("Loans:")
            jLoans.forEach {
                appendLine("  [${it.id}] ${it.name} ${inr(it.monthlyEmi)}/mo, ${it.remainingMonths}/${it.totalMonths} months left" +
                    if (isLoanConfirmed(it.id)) " — paid" else "")
            }
        }

        appendLine("--- ACCOUNTS & CARDS ---")
        appendLine("Accounts:")
        visibleAccounts.forEach {
            appendLine("  ${it.name} = ${inr(balanceOf(it))} (${it.person}" +
                (if (it.numberTail.isNotBlank()) ", ends ${it.numberTail}" else "") + ")")
        }
        if (visibleCards.isNotEmpty()) {
            appendLine("Cards:")
            visibleCards.forEach {
                appendLine("  ${it.name} owes ${inr(it.balance)} of ${inr(it.limit)}, due ${it.due} (${it.owner})" +
                    if (it.paid) " (paid)" else "")
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
        val who = activeProfile.orEmpty()
        update { s -> s.copy(chats = s.chats - who) }
        assistantHistory = null
    }

    /**
     * Rebuilds the model's transcript from the visible conversation.
     *
     * Called when the stored chat is there but the transcript is not — after a
     * restart, or on switching profile. Only the plain exchange is restored;
     * tool calls and their replies are dropped, which is safe because a tool
     * reply is only ever valid immediately after the request that asked for it.
     */
    private fun historyFromChat(): List<kotlinx.serialization.json.JsonElement> =
        chat.map { m ->
            kotlinx.serialization.json.buildJsonObject {
                put("role", if (m.role == "user") "user" else "assistant")
                put("content", m.text)
            }
        }

    /**
     * Sends a message and applies whatever the assistant decides to do. Runs off
     * the main thread; the tools hop back onto it themselves.
     */
    fun sendChat() = send(chatInput.trim(), fromRetry = false)

    /**
     * The question that failed, so it can be asked again without retyping it.
     *
     * A dropped connection used to lose the message entirely: it was already
     * off the input box and the only copy left was on screen, unusable.
     */
    var failedMessage by mutableStateOf(""); private set

    fun retryChat() {
        val text = failedMessage
        if (text.isBlank() || chatBusy) return
        failedMessage = ""
        send(text, fromRetry = true)
    }

    fun dismissRetry() { failedMessage = "" }

    private fun send(text: String, fromRetry: Boolean) {
        if (text.isEmpty() || chatBusy) return
        if (!chatReady) {
            appendChat(ChatMessage("assistant", "Add an OpenAI key in Settings first."))
            return
        }
        failedMessage = ""
        chatInput = ""
        // On a retry the question is already in the conversation; adding it
        // again would show it twice and send it twice.
        if (!fromRetry) appendChat(ChatMessage("user", text))
        chatBusy = true
        chatStatus = "Thinking…"
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
        // After a restart the transcript is empty while the conversation on
        // screen is not; rebuild it so a follow-up like "change that to 5000"
        // still has something to refer to.
        val kept = trimHistory(assistantHistory ?: historyFromChat())
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
                        appendChat(ChatMessage("assistant", it.reply))
                        if (!it.changed) dropUndoPoint()
                    }
                    .onFailure { e ->
                        dropUndoPoint()
                        // Kept so Try again can resend it.
                        failedMessage = text
                        appendChat(
                            ChatMessage(
                                "assistant",
                                e.message ?: "Something went wrong talking to OpenAI."
                            )
                        )
                    }
                chatBusy = false
                chatStatus = ""
            }
        }.start()
    }

    // ── what the assistant drives ──────────────────────────────────────
    // The same paths the screens use, so a change made in chat syncs, moves
    // balances and respects profiles exactly as a tap would.

    /**
     * States to undo back to, newest last.
     *
     * Kept on disk and several deep. A single in-memory snapshot was cleared by
     * the next message and lost when the app closed, so a mistake noticed one
     * message later — or the next morning — could not be taken back at all.
     */
    private var undoStack: List<PersistedState> = store.loadUndo()

    val canUndoAssistant: Boolean get() = undoStack.isNotEmpty()

    fun markUndoPoint() {
        undoStack = (undoStack + persisted).takeLast(UNDO_DEPTH)
        store.saveUndo(undoStack)
    }

    /** Drops the point just taken, for a turn that changed nothing — an undo
     *  that restores identical state is only confusing. */
    private fun dropUndoPoint() {
        if (undoStack.isEmpty()) return
        undoStack = undoStack.dropLast(1)
        store.saveUndo(undoStack)
    }

    fun undoAssistant(): Boolean {
        val prev = undoStack.lastOrNull() ?: return false
        // The chat itself is not rolled back: undo is for the money, and losing
        // the conversation that explains what happened helps nobody.
        val chats = persisted.chats
        persisted = prev.copy(chats = chats).also { ownRevision = store.save(it) }
        undoStack = undoStack.dropLast(1)
        store.saveUndo(undoStack)
        pushNow()
        return true
    }

    /**
     * Totals worked out here rather than by the model.
     *
     * Asked "how much on groceries since April", the assistant used to list
     * every transaction and add them up itself — a long column of numbers,
     * added by a language model, which is the least reliable thing it does and
     * the dearest way to ask. These figures cannot be miscounted.
     */
    fun spendingSummary(months: Int, category: String?): String = buildString {
        val ids = scopedAccounts.map { it.id }.toSet()
        val cardIds = scopedCards.map { it.id }.toSet()
        val totals = LinkedHashMap<String, Double>()
        appendLine("Spending on the $bucketLabel side, most recent month last:")
        for (back in (months - 1) downTo 0) {
            val period = Ledger.cycleBefore(cycle(), back)
            val byCat = Ledger.spendByCategory(persisted.txns, period, ids, cardIds)
                .filterKeys { category.isNullOrBlank() || it.equals(category, true) }
            val month = byCat.values.sum()
            byCat.forEach { (c, v) -> totals[c] = (totals[c] ?: 0.0) + v }
            appendLine("  $period total ${inr(month)}" +
                if (byCat.isEmpty()) " — nothing recorded"
                else ": " + byCat.entries.sortedByDescending { it.value }
                    .joinToString(", ") { "${it.key} ${inr(it.value)}" })
        }
        if (totals.isNotEmpty()) {
            val all = totals.values.sum()
            appendLine("Over $months months: ${inr(all)} total, " +
                "${inr(Ledger.paise(all / months))} a month on average.")
            appendLine("By category: " + totals.entries.sortedByDescending { it.value }
                .joinToString(", ") { "${it.key} ${inr(it.value)}" })
        }
    }

    /** What is coming up, from the same dates the daily reminder reads. */
    fun dueSoonText(days: Int): String = buildString {
        val now = today()
        appendLine("Due in the next $days days:")
        var any = false
        visibleEntries.filter { !it.closed && it.nextDue.isNotEmpty() }.forEach { e ->
            val left = Ledger.daysBetween(now, e.nextDue)
            if (left < 0 || left > days) return@forEach
            any = true
            append("  [${e.id}] ${e.note.ifEmpty { e.category }} ${inr(e.amount)} on " +
                "${prettyDate(e.nextDue)} (in $left days)")
            if (e.isSetAside) append(" — ${inr(setAsidePot(e))} saved so far")
            if (isConfirmed(e.id)) append(" — already done this cycle")
            appendLine()
        }
        visibleLoans.filter { it.remainingMonths > 0 && it.nextDue.isNotEmpty() }.forEach { l ->
            val left = Ledger.daysBetween(now, l.nextDue)
            if (left < 0 || left > days) return@forEach
            any = true
            appendLine("  [${l.id}] ${l.name} EMI ${inr(l.monthlyEmi)} on " +
                "${prettyDate(l.nextDue)} (in $left days)" +
                if (isLoanConfirmed(l.id)) " — already paid" else "")
        }
        visibleCards.filter { !it.paid && it.balance > 0 && it.nextDue.isNotEmpty() }.forEach { c ->
            val left = Ledger.daysBetween(now, c.nextDue)
            if (left < 0 || left > days) return@forEach
            any = true
            appendLine("  ${c.name} bill ${inr(c.balance)} on ${prettyDate(c.nextDue)} " +
                "(in $left days)")
        }
        if (!any) appendLine("  Nothing.")
    }

    /** Teaches a payee's category from the chat, and reports how many moved. */
    fun rememberPayeeCategory(payee: String, category: String): Int {
        val key = payeeKey(payee)
        if (key.isBlank() || category.isBlank()) return 0
        val moved = persisted.txns.count {
            it.category == UNCATEGORISED && payeeKey(payeeOf(it)) == key
        }
        learnPayeeCategory(payee, category)
        return moved
    }

    fun cardNamed(name: String): Card? =
        visibleCards.firstOrNull { it.name.equals(name, true) }
            ?: visibleCards.firstOrNull { name.isNotBlank() && it.name.contains(name, true) }

    fun loanNamed(name: String): Loan? =
        visibleLoans.firstOrNull { it.name.equals(name, true) }
            ?: visibleLoans.firstOrNull { name.isNotBlank() && it.name.contains(name, true) }

    fun updateCardDirect(
        id: String,
        newName: String?,
        limit: Double?,
        balance: Double?,
        minDue: Double?,
        dueDate: String?,
        tail: String?,
        paid: Boolean?
    ): Card? {
        val c = cards.firstOrNull { it.id == id } ?: return null
        val updated = c.copy(
            name = newName ?: c.name,
            limit = limit ?: c.limit,
            balance = balance ?: c.balance,
            minDue = minDue ?: c.minDue,
            dueDate = dueDate ?: c.dueDate,
            numberTail = tail ?: c.numberTail,
            paid = paid ?: c.paid
        )
        update { s -> s.copy(cards = s.cards.map { if (it.id == id) updated else it }) }
        return updated
    }

    fun updateLoanDirect(
        id: String,
        emi: Double?,
        remaining: Int?,
        dueDate: String?,
        accountName: String?,
        cardName: String?
    ): Loan? {
        val l = loans.firstOrNull { it.id == id } ?: return null
        val card = cardName?.takeIf { it.isNotBlank() }?.let { cardNamed(it) }
        val account = accountName?.takeIf { it.isNotBlank() }?.let { accountNamed(it) }
        val updated = l.copy(
            monthlyEmi = emi ?: l.monthlyEmi,
            remainingMonths = (remaining ?: l.remainingMonths).coerceIn(0, l.totalMonths),
            dueDate = dueDate ?: l.dueDate,
            // Naming one clears the other: a loan charged to an account and a
            // card at once would be paid twice.
            cardId = card?.id ?: if (account != null) "" else l.cardId,
            accountId = account?.id ?: if (card != null) "" else l.accountId
        )
        update { s -> s.copy(loans = s.loans.map { if (it.id == id) updated else it }) }
        return updated
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
    /**
     * The category for a name the assistant supplied.
     *
     * Only ever an existing category, or one of the standard kinds the payee
     * rules recognise — "petrol" becomes Fuel. Anything else is left
     * Uncategorised rather than becoming a category of its own: a payee is not
     * a category, and a list of shops is not a set of budgets. Adding a real
     * new category is add_category, which is a deliberate act.
     */
    fun categoryNamed(name: String): String {
        categories.firstOrNull { it.equals(name, true) }?.let { return it }
        categories.firstOrNull { name.contains(it, true) }?.let { return it }
        val resolved = categoryForParty(name, categories)
        if (resolved !in categories) addCategoryNamed(resolved)
        return resolved
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

    enum class VelocitySpeed {
        LOW, ON_TRACK, HIGH
    }

    data class SpendVelocity(
        val speed: VelocitySpeed,
        val actualDailySpend: Double,
        val targetDailyBudget: Double,
        val daysRemaining: Int
    )

    fun getSpendVelocity(): SpendVelocity {
        val resetDay = salaryResetDayFor(activeProfile.orEmpty())
        val todayIso = today()
        val parts = todayIso.split("-")
        val y = parts[0].toIntOrNull() ?: 2026
        val m = parts[1].toIntOrNull() ?: 8
        val d = parts[2].toIntOrNull() ?: 12
        
        var startYear = y
        var startMonth = m
        var endYear = y
        var endMonth = m
        
        if (d >= resetDay) {
            if (m == 12) {
                endMonth = 1
                endYear++
            } else {
                endMonth++
            }
        } else {
            if (m == 1) {
                startMonth = 12
                startYear--
            } else {
                startMonth--
            }
        }
        
        val startCalendar = java.util.Calendar.getInstance()
        startCalendar.set(java.util.Calendar.YEAR, startYear)
        startCalendar.set(java.util.Calendar.MONTH, startMonth - 1)
        val startMaxDay = startCalendar.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
        val startDay = minOf(resetDay, startMaxDay)
        val startDate = "%04d-%02d-%02d".format(startYear, startMonth, startDay)
        
        val endCalendar = java.util.Calendar.getInstance()
        endCalendar.set(java.util.Calendar.YEAR, endYear)
        endCalendar.set(java.util.Calendar.MONTH, endMonth - 1)
        val endMaxDay = endCalendar.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
        val endDay = minOf(resetDay - 1, endMaxDay)
        val endDate = "%04d-%02d-%02d".format(endYear, endMonth, endDay)
        
        val totalCycleDays = Ledger.daysBetween(startDate, endDate) + 1
        val elapsedDays = Ledger.daysBetween(startDate, todayIso) + 1
        val daysRemaining = maxOf(0, totalCycleDays - elapsedDays)
        
        val cycleTxns = txns.filter { inBucket(it) }.filter { it.date >= startDate && it.date <= todayIso && it.kind == "EXPENSE" }
        val discretionarySpend = cycleTxns.filter { t ->
            t.loanId.isEmpty() && t.entryId.isEmpty() &&
            t.category != "EMI" && !t.category.lowercase().contains("emi") &&
            !t.category.lowercase().contains("loan") && t.category != "Credit Card Bill"
        }.sumOf { it.amount }
        
        val outlook = getSixMonthOutlook()
        val discretionaryBudget = (outlook.monthlySalary - outlook.monthlyLoans - outlook.monthlyRecurring - outlook.monthlySetAside).coerceAtLeast(0.0)
        
        val targetDailyBudget = discretionaryBudget / totalCycleDays.coerceAtLeast(1)
        val actualDailySpend = discretionarySpend / elapsedDays.coerceAtLeast(1)
        
        val speed = when {
            targetDailyBudget <= 0.0 -> VelocitySpeed.ON_TRACK
            actualDailySpend <= targetDailyBudget * 0.9 -> VelocitySpeed.LOW
            actualDailySpend <= targetDailyBudget * 1.1 -> VelocitySpeed.ON_TRACK
            else -> VelocitySpeed.HIGH
        }
        
        return SpendVelocity(speed, actualDailySpend, targetDailyBudget, daysRemaining)
    }

    fun linkSmsToSinkingFund(txnId: String, entryId: String) {
        update { s ->
            s.copy(
                txns = s.txns.map {
                    if (it.id == txnId) it.copy(entryId = entryId, kind = "TRANSFER")
                    else it
                },
                smsSuggestions = s.smsSuggestions - txnId
            )
        }
    }

    fun dismissSmsSuggestion(txnId: String) {
        update { s ->
            s.copy(smsSuggestions = s.smsSuggestions - txnId)
        }
    }

    fun settleCardBill(cardId: String) {
        val card = persisted.cards.firstOrNull { it.id == cardId } ?: return
        if (card.balance <= 0.0) return
        
        val fromAccId = persisted.accounts.firstOrNull { it.name == defaultAccount }?.id
            ?: persisted.accounts.firstOrNull()?.id
            ?: ""
            
        val now = today()
        val settleAmount = if (card.statementAmount > 0.0) card.statementAmount else card.balance
        val txn = Txn(
            id = newId("t"),
            date = now,
            kind = "EXPENSE",
            amount = settleAmount,
            category = "Credit Card Bill",
            fromAccountId = fromAccId,
            cardId = card.id,
            period = Ledger.cycleOf(now, cycleResetDay),
            note = "Settled ${card.name} Bill",
            at = System.currentTimeMillis()
        )
        
        update { s ->
            s.copy(
                txns = s.txns + txn,
                cards = s.cards.map {
                    if (it.id == cardId) {
                        val nextBal = (it.balance - settleAmount).coerceAtLeast(0.0)
                        it.copy(balance = nextBal, statementAmount = 0.0, paid = true)
                    } else it
                }
            )
        }
    }

    fun addSmsRule(pattern: String, category: String) {
        val cleanPattern = pattern.trim()
        if (cleanPattern.isEmpty() || category.isEmpty()) return
        
        update { s ->
            val nextRules = s.smsRules + (cleanPattern to category)
            val updatedTxns = s.txns.map { t ->
                if (t.source == "sms" && t.note.lowercase().contains(cleanPattern.lowercase())) {
                    t.copy(category = category)
                } else {
                    t
                }
            }
            s.copy(smsRules = nextRules, txns = updatedTxns)
        }
    }
}

