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
import com.vinay.fintrack.data.today
import com.vinay.fintrack.data.ownerLabel
import com.vinay.fintrack.data.parseSmartAdd

enum class Tab { HOME, ENTRIES, ADD, SETTINGS }

data class Draft(
    val person: String = "Me",
    val type: String = "EXPENSE",
    val bucket: String = "JOINT",
    val category: String = "Other",
    val amountText: String = "",
    val frequency: String = "MONTHLY",
    val note: String = ""
)

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

    init {
        sync.onStatusChange = { s, e -> syncStatus = s; syncError = e }
        sync.onTxns = { remote ->
            // Anything local the server hasn't seen — recorded by the SMS
            // receiver while offline, say — is pushed rather than dropped.
            val remoteIds = remote.map { it.id }.toSet()
            val localOnly = persisted.txns.filterNot { it.id in remoteIds }
            persisted = persisted.copy(txns = remote + localOnly).also { store.save(it) }
            localOnly.forEach { sync.upsertTxn(it) }
            syncedAt = System.currentTimeMillis()
        }
        sync.onTxnsMissing = { sync.pushAllTxns(persisted.txns) }
        if (persisted.firebaseConfigText.isNotBlank()) connectSync()
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
                    // Keep this device's own secrets — they're per-device, not
                    // household data — and its transactions, which arrive on
                    // their own listener.
                    persisted = remote.copy(
                        txns = persisted.txns,
                        localUpdatedAt = persisted.localUpdatedAt,
                        firebaseConfigText = persisted.firebaseConfigText,
                        openaiKeyText = persisted.openaiKeyText
                    ).also { store.save(it) }
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
    val importedCount: Int get() = persisted.txns.count { it.source == "sms" }

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

    /** What belongs in the shared state document: not this device's keys, and
     *  not the transactions — those are documents of their own. */
    private fun sharable(s: PersistedState) =
        s.copy(txns = emptyList(), firebaseConfigText = "", openaiKeyText = "")

    override fun onCleared() {
        sync.disconnect()
        super.onCleared()
    }

    private fun update(block: (PersistedState) -> PersistedState) {
        // Stamped on every change so an offline edit can be told apart from a
        // stale server copy when the two meet.
        persisted = block(persisted)
            .copy(localUpdatedAt = System.currentTimeMillis())
            .also { store.save(it) }
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
    var isLocked by mutableStateOf(true); private set
    var pinStep by mutableStateOf("pick"); private set
    var activeProfile by mutableStateOf<String?>(null); private set
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
    var addKind by mutableStateOf("EXPENSE"); private set
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
    }

    fun savePin() {
        if (pinNew.length != 4) { pinMsg = "PIN must be 4 digits."; pinMsgIsError = true; return }
        if (pinNew != pinConfirm) { pinMsg = "PINs don't match."; pinMsgIsError = true; return }
        val p = activeProfile ?: return
        update { it.copy(profiles = it.profiles + (p to pinNew)) }
        pinMsg = "PIN updated."; pinMsgIsError = false; pinNew = ""; pinConfirm = ""
    }

    fun setPinField(isNew: Boolean, v: String) {
        val clean = v.filter { it.isDigit() }.take(4)
        if (isNew) pinNew = clean else pinConfirm = clean
        pinMsg = ""
    }

    // ── home ───────────────────────────────────────────────────────────
    fun toggleBalanceVisible() { balanceHidden = !balanceHidden }
    fun toggleLoanDetail(id: String) { expandedLoan = if (expandedLoan == id) null else id }
    fun payCard(id: String) = update { s -> s.copy(cards = s.cards.map { if (it.id == id) it.copy(paid = true) else it }) }

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

    private fun confirmKindFor(e: Entry): String = when {
        e.type == "INCOME" -> "INCOME"
        // Annual provisions and savings stay your money — they move, they aren't spent.
        e.frequency == "ANNUAL" || e.type == "SAVINGS" -> "TRANSFER"
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
            fromAccountId = if (kind == "INCOME") "" else accountIdByName(defaultAccount)
        )
    }

    /** Loan EMIs are paid from the account stored on the loan, so no sheet appears. */
    fun confirmLoan(l: Loan) {
        if (isLoanConfirmed(l.id)) {
            removeTxns { it.loanId == l.id && it.period == currentPeriod() }
            return
        }
        val from = l.accountId.ifEmpty { accountIdByName(defaultAccount) }
        addTxn { seq ->
            Txn(
                id = seq, date = today(), kind = "EXPENSE", amount = l.monthlyEmi,
                category = "EMI", fromAccountId = from, loanId = l.id,
                period = currentPeriod(), note = l.name
            )
        }
    }

    /** Adds a transaction locally and as its own Firestore document. */
    private fun addTxn(build: (id: String) -> Txn) {
        val txn = build("t${persisted.nextTxnSeq}")
        update { s -> s.copy(txns = s.txns + txn, nextTxnSeq = s.nextTxnSeq + 1) }
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
                entryId = p.entryId, period = currentPeriod(), note = p.title
            )
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
                    "e${s.nextEntrySeq}", parsed.person, parsed.type, parsed.bucket,
                    parsed.category, parsed.amount, parsed.frequency, parsed.note
                ),
                nextEntrySeq = s.nextEntrySeq + 1
            )
        }
        homeQuickText = ""
        homeQuickConfirm = "Added ${inr(parsed.amount)} · ${parsed.category} · ${parsed.person}"
    }

    // ── entries ────────────────────────────────────────────────────────
    fun deleteEntry(id: String) = update { s -> s.copy(entries = s.entries.filterNot { it.id == id }) }

    fun openEditEntry(e: Entry) {
        editingEntryId = e.id
        tab = Tab.ADD
        draft = Draft(e.person, e.type, e.bucket, e.category, e.amount.toLong().toString(), e.frequency, e.note)
    }

    fun cancelEdit() {
        editingEntryId = null
        draft = Draft(person = activeProfile ?: "Me")
        addKind = "EXPENSE"
    }

    fun setCategoryFilter(c: String?) {
        entriesCategoryFilter = if (entriesCategoryFilter == c) null else c
    }

    // ── add ────────────────────────────────────────────────────────────
    fun selectAddKind(k: String) {
        addKind = k
        val p = activeProfile ?: "Me"
        when (k) {
            "EXPENSE", "BILL" -> draft = Draft(person = p, type = "EXPENSE", frequency = "MONTHLY")
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

    fun saveDraft() {
        val amount = draft.amountText.toDoubleOrNull() ?: return
        if (amount <= 0) return
        val editingId = editingEntryId
        update { s ->
            val entry = Entry(
                editingId ?: "e${s.nextEntrySeq}", draft.person, draft.type, draft.bucket,
                draft.category, amount, draft.frequency, draft.note
            )
            if (editingId != null) {
                s.copy(entries = s.entries.map { if (it.id == entry.id) entry else it })
            } else {
                s.copy(entries = s.entries + entry, nextEntrySeq = s.nextEntrySeq + 1)
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
                    "l${s.nextLoanSeq}", newLoanDraft.name, newLoanDraft.person, emi, total, remaining,
                    newLoanDraft.accountId.ifEmpty { accountIdByName(defaultAccount) }
                ),
                nextLoanSeq = s.nextLoanSeq + 1
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
                    "a${s.nextAccountSeq}", newAccountDraft.name, ownerLabel(newAccountDraft.owner),
                    newAccountDraft.owner, newAccountDraft.balanceText.toDoubleOrNull() ?: 0.0,
                    newAccountDraft.numberTail
                ),
                nextAccountSeq = s.nextAccountSeq + 1
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
                    "cc${s.nextCardSeq}", newCardDraft.name, newCardDraft.owner, limit,
                    newCardDraft.balanceText.toDoubleOrNull() ?: 0.0,
                    newCardDraft.minDueText.toDoubleOrNull() ?: 0.0,
                    newCardDraft.due
                ),
                nextCardSeq = s.nextCardSeq + 1
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

    fun deleteAccount(id: String) {
        update { s -> s.copy(accounts = s.accounts.filterNot { it.id == id }) }
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

    /** Opening balance plus every movement in or out — so undoing a confirm
     *  restores the old number without any inverse bookkeeping. */
    fun balanceOf(a: Account): Double {
        var b = a.openingBalance
        for (t in persisted.txns) {
            if (t.fromAccountId == a.id) b -= t.amount
            if (t.toAccountId == a.id) b += t.amount
        }
        return b
    }

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
    fun spendFor(category: String): Double {
        val period = currentPeriod()
        val mine = visibleAccounts.map { it.id }.toSet()
        return persisted.txns
            .filter { it.category == category && it.month == period && it.fromAccountId in mine }
            .sumOf { it.amount }
    }

    /** Regular monthly outgoings — everything except EMIs (own section) and annuals. */
    val commitments: List<Entry>
        get() = visibleEntries.filter {
            it.frequency != "ANNUAL" &&
                ((it.type == "EXPENSE" && it.category != "EMI") || it.type == "SAVINGS")
        }

    /**
     * Annual items shown at their monthly-equivalent (amount / 12). Confirming one
     * doesn't spend the money — it transfers it to a set-aside account, so the cash
     * is waiting when the yearly bill actually lands.
     */
    val annualSetAsides: List<Entry>
        get() = visibleEntries.filter { it.frequency == "ANNUAL" && it.type != "INCOME" }

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

    /** The Transactions screen shows these and nothing else: real recorded
     *  movements, not the recurring plan. */
    val filteredTxns: List<Txn>
        get() = txns
            .filter { t ->
                if (bucketView == "PERSONAL") txnPerson(t) == activeProfile
                else txnPerson(t) == "Joint"
            }
            .filter { t ->
                (entriesCategoryFilter == null || t.category == entriesCategoryFilter) &&
                    (entriesSearch.isEmpty() ||
                        t.category.contains(entriesSearch, true) ||
                        t.note.contains(entriesSearch, true) ||
                        t.ref.contains(entriesSearch, true))
            }
            .sortedByDescending { it.date + it.id }

    val txnChips: List<String>
        get() = txns
            .filter { t ->
                if (bucketView == "PERSONAL") txnPerson(t) == activeProfile
                else txnPerson(t) == "Joint"
            }
            .map { it.category }.distinct()

    /** Removes it here and in Firestore, and the balance follows. */
    fun deleteTxn(id: String) = removeTxns { it.id == id }

    val availableChips: List<String>
        get() = entries.filter {
            if (bucketView == "PERSONAL") it.bucket == "PERSONAL" && it.person == activeProfile
            else it.bucket == "JOINT"
        }.map { it.category }.distinct()

    val draftPersonOptions: List<String> get() = listOfNotNull(activeProfile, "Joint")
}
