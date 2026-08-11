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
