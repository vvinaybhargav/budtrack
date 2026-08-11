package com.vinay.fintrack.data

/**
 * The money rules, as plain functions over plain data.
 *
 * These used to live inside the ViewModel, where they need an Android runtime
 * and so were only ever exercised by using the app. Every balance, bucket and
 * budget bug found so far was found that way. Here they can be tested.
 *
 * Nothing in this file touches Android, state or storage.
 */
object Ledger {

    /** Marks the transaction that settles a card bill, rather than a spend on it. */
    const val CARD_PAYMENT = "cardpay"

    /**
     * Every account's balance in one pass: its opening figure plus what came in,
     * minus what went out. Derived rather than stored, so undoing anything
     * reverses itself without inverse bookkeeping.
     */
    fun balances(accounts: List<Account>, txns: List<Txn>): Map<String, Double> {
        val map = HashMap<String, Double>(accounts.size)
        accounts.forEach { map[it.id] = it.openingBalance }
        for (t in txns) {
            if (t.fromAccountId.isNotEmpty()) map[t.fromAccountId]?.let { map[t.fromAccountId] = it - t.amount }
            if (t.toAccountId.isNotEmpty()) map[t.toAccountId]?.let { map[t.toAccountId] = it + t.amount }
        }
        return map
    }

    /**
     * Which side of the household a transaction belongs to, taken from whatever
     * it moved through. A card spend touches no account, so it takes the card's
     * owner; an empty result means the account or card is gone.
     */
    fun personOf(t: Txn, accounts: List<Account>, cards: List<Card>): String {
        if (t.cardId.isNotEmpty()) return cards.firstOrNull { it.id == t.cardId }?.owner.orEmpty()
        val id = t.fromAccountId.ifEmpty { t.toAccountId }
        return accounts.firstOrNull { it.id == id }?.person.orEmpty()
    }

    /**
     * Everyone a transaction concerns — both ends of a transfer, not just the
     * one it left.
     *
     * A transfer from your account to the joint one is yours and the
     * household's alike, and moving money to your wife's account is something
     * you both need to see. Taking only the source hid it from one of them.
     */
    fun personsOf(t: Txn, accounts: List<Account>, cards: List<Card>): Set<String> {
        if (t.cardId.isNotEmpty()) {
            return setOfNotNull(cards.firstOrNull { it.id == t.cardId }?.owner)
        }
        val people = listOf(t.fromAccountId, t.toAccountId)
            .filter { it.isNotEmpty() }
            .mapNotNull { id -> accounts.firstOrNull { it.id == id }?.person }
        return people.toSet()
    }

    private fun <T : Any> setOfNotNull(value: T?): Set<T> = if (value == null) emptySet() else setOf(value)

    /**
     * Personal is the active profile's; joint is the shared side, and takes
     * orphans too so a transaction whose account was deleted stays reachable
     * rather than disappearing from both.
     */
    fun inBucket(person: String, activeProfile: String?, personalView: Boolean): Boolean =
        inBucket(setOf(person), activeProfile, personalView)

    /** Both ends count, so a transfer between two people shows for each. */
    fun inBucket(persons: Set<String>, activeProfile: String?, personalView: Boolean): Boolean =
        if (personalView) activeProfile != null && activeProfile in persons
        else "Joint" in persons || persons.isEmpty() || persons.all { it.isEmpty() }

    /**
     * The month a date belongs to when months don't start on the 1st.
     *
     * Salaries and set-asides follow a pay cycle, so [resetDay] moves the
     * boundary: with a reset on the 5th, the 3rd of August still belongs to
     * July's cycle.
     */
    fun cycleOf(dateIso: String, resetDay: Int): String {
        if (resetDay <= 1) return dateIso.take(7)
        val parts = dateIso.split("-")
        if (parts.size < 3) return dateIso.take(7)
        val year = parts[0].toIntOrNull() ?: return dateIso.take(7)
        val month = parts[1].toIntOrNull() ?: return dateIso.take(7)
        val day = parts[2].toIntOrNull() ?: return dateIso.take(7)
        if (day >= resetDay) return "%04d-%02d".format(year, month)
        return if (month == 1) "%04d-12".format(year - 1) else "%04d-%02d".format(year, month - 1)
    }

    /** How much has already been put by for a set-aside this cycle. */
    fun setAsideDone(txns: List<Txn>, entryId: String, cycle: String): Double =
        txns.filter { it.entryId == entryId && it.month == cycle && it.kind == "TRANSFER" }
            .sumOf { it.amount }

    /**
     * Real money out per category this month.
     *
     * Transfers are excluded — setting money aside for car insurance is not
     * spending on car insurance. Card spends are included even though they
     * leave no account, but settling the card bill is not, or the purchase and
     * paying for it would both be charged to the budget.
     */
    fun spendByCategory(
        txns: List<Txn>,
        period: String,
        accountIds: Set<String>,
        cardIds: Set<String>
    ): Map<String, Double> {
        val map = HashMap<String, Double>()
        for (t in txns) {
            if (t.kind == "TRANSFER" || t.month != period) continue
            val counts = t.fromAccountId in accountIds ||
                (t.cardId.isNotEmpty() && t.cardId in cardIds && t.source != CARD_PAYMENT)
            if (counts) map[t.category] = (map[t.category] ?: 0.0) + t.amount
        }
        return map
    }

    /** Money that actually moved this month, as opposed to what was planned. */
    class MonthTotals(
        val income: Double,
        val spent: Double,
        val saved: Double,
        val invested: Double
    )

    /**
     * The month's real figures, from transactions.
     *
     * Not from entries: those are the plan, so they showed the same numbers
     * whether or not a rupee had moved — deleting every transaction left them
     * untouched, which reads as the app being wrong.
     *
     * A transfer is money kept rather than spent, so it counts as saved or
     * invested by its category, never as an expense. A card spend counts as
     * spending; settling the card does not, or it would count twice.
     */
    fun monthTotals(
        txns: List<Txn>,
        period: String,
        accountIds: Set<String>,
        cardIds: Set<String>,
        investCategories: List<String>
    ): MonthTotals {
        var income = 0.0
        var spent = 0.0
        var saved = 0.0
        var invested = 0.0
        for (t in txns) {
            if (t.month != period) continue
            when (t.kind) {
                "INCOME" -> if (t.toAccountId in accountIds) income += t.amount
                "TRANSFER" -> if (t.fromAccountId in accountIds) {
                    if (t.category in investCategories) invested += t.amount else saved += t.amount
                }
                else -> {
                    val counts = t.fromAccountId in accountIds ||
                        (t.cardId.isNotEmpty() && t.cardId in cardIds && t.source != CARD_PAYMENT)
                    if (counts) spent += t.amount
                }
            }
        }
        return MonthTotals(income, spent, saved, invested)
    }

    /**
     * What to put by this month for a commitment paid every [everyMonths].
     *
     * The remainder is spread over the first months rather than dropped, so the
     * twelve monthly shares of a ₹40,000 yearly bill add back up to ₹40,000
     * instead of ₹39,996.
     */
    fun monthlyShare(amount: Double, everyMonths: Int, monthIndex: Int = 0): Double {
        if (everyMonths <= 1) return amount
        val paise = Math.round(amount * 100)
        val base = paise / everyMonths
        val remainder = paise % everyMonths
        val extra = if (monthIndex < remainder) 1 else 0
        return (base + extra) / 100.0
    }

    /** True when a bank alert and an existing confirmation are the same payment. */
    fun isSamePayment(existing: Txn, amount: Double, date: String, isCredit: Boolean): Boolean {
        val wanted = if (isCredit) "INCOME" else "EXPENSE"
        return existing.ref.isEmpty() &&
            existing.source.isEmpty() &&
            (existing.kind == wanted || existing.kind == "TRANSFER") &&
            kotlin.math.abs(existing.amount - amount) < 0.5 &&
            withinDays(existing.date, date, 4)
    }
}

/**
 * The category a payment belongs to, inventing one from the payee when nothing
 * fits.
 *
 * Never "Other": everything unrecognised landing in one bucket makes budgets
 * useless and hides what the money actually went on. A new category named after
 * the payee is at least true, and can be renamed or merged later.
 */
fun categoryForParty(party: String, categories: List<String>): String {
    val p = party.lowercase().trim()
    if (p.isEmpty()) return categories.firstOrNull().orEmpty()

    // An existing category named in the payee wins outright.
    categories.firstOrNull { it.isNotBlank() && p.contains(it.lowercase()) }?.let { return it }

    val known = mapOf(
        "Eating Out" to listOf("swiggy", "zomato", "restaurant", "cafe", "eatclub", "dominos", "kfc"),
        "Groceries" to listOf("bigbasket", "blinkit", "zepto", "grocer", "dmart", "mart", "instamart"),
        "Utilities" to listOf("electricity", "gas", "water", "broadband", "airtel", "jio", "vodafone", "bescom"),
        "Fuel" to listOf("petrol", "diesel", "fuel", "hpcl", "bpcl", "indianoil", "shell"),
        "Travel" to listOf("uber", "ola", "rapido", "irctc", "indigo", "makemytrip", "redbus"),
        "Shopping" to listOf("amazon", "flipkart", "myntra", "ajio", "meesho", "nykaa"),
        "Health" to listOf("pharmacy", "apollo", "medplus", "hospital", "clinic", "diagnostic"),
        "EMI" to listOf("emi", "loan")
    )
    known.forEach { (name, needles) ->
        if (needles.any { p.contains(it) }) return name
    }

    // Nothing fits: name it after the payee rather than burying it in Other.
    return titleCase(party)
}

/** "SWIGGY" and "swiggy limited" both become "Swiggy Limited". */
private fun titleCase(raw: String): String =
    raw.trim()
        .split(Regex("\s+"))
        .filter { it.isNotBlank() }
        .joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { it.uppercase() }
        }
        .take(24)
        .ifBlank { "Uncategorised" }
