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
import com.vinay.fintrack.data.INVEST_CATEGORIES
import com.vinay.fintrack.data.Loan
import com.vinay.fintrack.data.PersistedState
import com.vinay.fintrack.data.SAVINGS_CATEGORIES
import com.vinay.fintrack.data.Store
import com.vinay.fintrack.data.inr
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
    val totalMonthsText: String = "", val remainingMonthsText: String = ""
)

data class NewAccountDraft(val name: String = "", val owner: String = "Me", val balanceText: String = "")

data class NewCardDraft(
    val name: String = "", val owner: String = "Me", val limitText: String = "",
    val balanceText: String = "", val minDueText: String = "", val due: String = ""
)

class FinTrackViewModel(app: Application) : AndroidViewModel(app) {

    private val store = Store(app)
    private var persisted by mutableStateOf(store.load())

    private fun update(block: (PersistedState) -> PersistedState) {
        persisted = block(persisted).also { store.save(it) }
    }

    // ── persisted views ────────────────────────────────────────────────
    val entries: List<Entry> get() = persisted.entries
    val accounts: List<Account> get() = persisted.accounts
    val loans: List<Loan> get() = persisted.loans
    val cards: List<Card> get() = persisted.cards
    val categories: List<String> get() = persisted.categories
    val confirmed: Set<String> get() = persisted.confirmed
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

    fun toggleCommitment(key: String) = update { s ->
        s.copy(confirmed = if (key in s.confirmed) s.confirmed - key else s.confirmed + key)
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
        tab = Tab.ENTRIES
    }

    fun addNewLoan() {
        val emi = newLoanDraft.emiText.toDoubleOrNull() ?: return
        val total = newLoanDraft.totalMonthsText.toIntOrNull() ?: return
        if (newLoanDraft.name.isBlank() || emi <= 0 || total <= 0) return
        val remaining = newLoanDraft.remainingMonthsText.toIntOrNull() ?: total
        update { s ->
            s.copy(
                loans = s.loans + Loan("l${s.nextLoanSeq}", newLoanDraft.name, newLoanDraft.person, emi, total, remaining),
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
                    newAccountDraft.owner, newAccountDraft.balanceText.toDoubleOrNull() ?: 0.0
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
        accountDraft = NewAccountDraft(a.name, a.owner, a.balance.toLong().toString())
    }

    fun cancelEditAccount() { editingAccountId = null }

    fun saveAccount() {
        val id = editingAccountId ?: return
        update { s ->
            s.copy(accounts = s.accounts.map {
                if (it.id == id) it.copy(
                    name = accountDraft.name, owner = accountDraft.owner,
                    balance = accountDraft.balanceText.toDoubleOrNull() ?: 0.0
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
            l.totalMonths.toString(), l.remainingMonths.toString()
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
                    remainingMonths = loanDraft.remainingMonthsText.toIntOrNull() ?: 0
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

    fun setDefaultAccount(v: String) = update { it.copy(defaultAccount = v) }
    fun setFirebaseConfig(v: String) = update { it.copy(firebaseConfigText = v) }
    fun setOpenaiKey(v: String) = update { it.copy(openaiKeyText = v) }

    // ── derived ────────────────────────────────────────────────────────
    private fun visible(person: String) = person == activeProfile || person == "Joint"

    val visibleEntries: List<Entry> get() = entries.filter { visible(it.person) }
    val visibleAccounts: List<Account> get() = accounts.filter { visible(it.person) }
    val visibleLoans: List<Loan> get() = loans.filter { visible(it.person) }
    val visibleCards: List<Card> get() = cards.filter { visible(it.owner) }

    val totalBalance: Double get() = visibleAccounts.sumOf { it.balance }

    val monthlyIncome: Double get() = visibleEntries.filter { it.type == "INCOME" }.sumOf { it.monthly }
    val monthlyExpense: Double get() = visibleEntries.filter { it.type == "EXPENSE" }.sumOf { it.monthly }
    val monthlyInvestment: Double
        get() = visibleEntries.filter { it.type == "SAVINGS" && it.category in INVEST_CATEGORIES }.sumOf { it.monthly }
    val monthlySavings: Double
        get() = visibleEntries.filter { it.type == "SAVINGS" && it.category !in INVEST_CATEGORIES }.sumOf { it.monthly }

    fun spendFor(category: String): Double =
        visibleEntries.filter { it.type == "EXPENSE" && it.category == category }.sumOf { it.monthly }

    val commitments: List<Entry>
        get() = visibleEntries.filter { it.type == "EXPENSE" && it.category != "EMI" } +
            visibleEntries.filter { it.type == "SAVINGS" }

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

    val availableChips: List<String>
        get() = entries.filter {
            if (bucketView == "PERSONAL") it.bucket == "PERSONAL" && it.person == activeProfile
            else it.bucket == "JOINT"
        }.map { it.category }.distinct()

    val draftPersonOptions: List<String> get() = listOfNotNull(activeProfile, "Joint")
}
