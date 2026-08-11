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
     * Personal is the active profile's; joint is the shared side, and takes
     * orphans too so a transaction whose account was deleted stays reachable
     * rather than disappearing from both.
     */
    fun inBucket(person: String, activeProfile: String?, personalView: Boolean): Boolean =
        if (personalView) person == activeProfile else person == "Joint" || person.isEmpty()

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
