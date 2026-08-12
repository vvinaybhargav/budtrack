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
     * Rounds to the nearest paisa.
     *
     * Amounts are stored as Double, and a Double cannot hold 0.1 exactly. One
     * amount is fine — it displays correctly and always has. Adding hundreds of
     * them is where it tells: the error accumulates, and a year of transactions
     * can leave a balance a few paise adrift from the sum of its parts, which is
     * the kind of discrepancy nobody can ever explain.
     *
     * Applied at every aggregate below rather than to stored values, so no data
     * has to be migrated and no figure changes — the drift simply has nowhere to
     * collect.
     */
    fun paise(value: Double): Double = Math.round(value * 100.0) / 100.0

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
        return map.mapValues { paise(it.value) }
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

    /**
     * Everything put by for a set-aside so far, less anything already paid out
     * of it — the pot, across every month rather than only this one.
     *
     * Transfers in, payments out. The bill itself is an expense tagged with the
     * same entry, so paying it empties the pot without any separate bookkeeping.
     */
    fun setAsidePot(txns: List<Txn>, entryId: String): Double = paise(
        txns.filter { it.entryId == entryId }
            .sumOf {
                when (it.kind) {
                    "TRANSFER" -> it.amount
                    "EXPENSE" -> -it.amount
                    else -> 0.0
                }
            }
    )

    /** How much has already been put by for a set-aside this cycle. */
    fun setAsideDone(txns: List<Txn>, entryId: String, cycle: String): Double = paise(
        txns.filter { it.entryId == entryId && it.month == cycle && it.kind == "TRANSFER" }
            .sumOf { it.amount }
    )

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
            if (t.kind == "TRANSFER" || t.month != period || t.source == CARD_PAYMENT) continue
            // A refund lands where the spending did, so it is netted off there:
            // returning a shirt does not earn you money, it un-spends it.
            val credit = t.kind == "REFUND"
            val counts = if (credit) {
                t.toAccountId in accountIds || (t.cardId.isNotEmpty() && t.cardId in cardIds)
            } else {
                t.fromAccountId in accountIds || (t.cardId.isNotEmpty() && t.cardId in cardIds)
            }
            if (!counts) continue
            val delta = if (credit) -t.amount else t.amount
            map[t.category] = (map[t.category] ?: 0.0) + delta
        }
        return map.mapValues { paise(it.value) }
    }

    /**
     * The cycle [back] months before [cycle]. "2026-08" back 1 is "2026-07".
     *
     * Cycles are labelled by month even when they start on the 24th, so walking
     * back is plain month arithmetic on the label.
     */
    fun cycleBefore(cycle: String, back: Int): String {
        val parts = cycle.split("-")
        if (parts.size < 2) return cycle
        val year = parts[0].toIntOrNull() ?: return cycle
        val month = parts[1].toIntOrNull() ?: return cycle
        val zero = year * 12 + (month - 1) - back
        return "%04d-%02d".format(zero / 12, (zero % 12) + 1)
    }

    /**
     * What a category may spend this month, with last month's leftover added.
     *
     * A budget that resets to the same figure regardless of how the last month
     * went says nothing about whether you are ahead or behind. Underspending
     * ₹2,000 on groceries should buy you ₹2,000 of room, and overspending should
     * cost you it — carrying only the good half would make the number flattering
     * rather than useful.
     *
     * Only one month is carried. Compounding a year of small underspends turns a
     * budget into a licence.
     */
    fun allowance(budget: Double, spentLastCycle: Double): Double =
        paise(budget + (budget - spentLastCycle))

    /** What each of the last [months] cycles spent on a category, oldest first. */
    fun spendTrend(
        txns: List<Txn>,
        cycle: String,
        category: String,
        accountIds: Set<String>,
        cardIds: Set<String>,
        months: Int = 3
    ): List<Double> = (months downTo 1).map { back ->
        spendByCategory(txns, cycleBefore(cycle, back), accountIds, cardIds)[category] ?: 0.0
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
            // Settling a card moves money out of an account, but the purchases
            // it settles were already counted when they were made. Testing the
            // account and the card separately let the bill through on the
            // account side, charging the same rupees to the month twice.
            if (t.source == CARD_PAYMENT) continue
            when (t.kind) {
                "INCOME" -> if (t.toAccountId in accountIds) income += t.amount
                // Money back, not money made. Counting a return as income
                // inflated earnings and left the spending it reverses standing.
                "REFUND" -> if (t.toAccountId in accountIds ||
                    (t.cardId.isNotEmpty() && t.cardId in cardIds)) spent -= t.amount
                // Only a transfer you confirmed against a set-aside counts as
                // money put by. A plain move between your own banks carries no
                // entry, and counting it would fill the month's set-aside figure
                // with transfers you never meant as saving — the set-aside
                // section is where that is confirmed.
                "TRANSFER" -> if (t.entryId.isNotEmpty() && t.fromAccountId in accountIds) {
                    if (t.category in investCategories) invested += t.amount else saved += t.amount
                }
                else -> {
                    val counts = t.fromAccountId in accountIds ||
                        (t.cardId.isNotEmpty() && t.cardId in cardIds)
                    if (counts) spent += t.amount
                }
            }
        }
        return MonthTotals(paise(income), paise(spent), paise(saved), paise(invested))
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

    /**
     * The next time a bill falls due, rolled past any dates already gone.
     *
     * A due date stored once would otherwise be stuck in the past forever, and
     * every share after the first payment would divide by zero months left.
     */
    fun nextDue(dueIso: String, everyMonths: Int, todayIso: String): String {
        if (dueIso.isEmpty()) return ""
        val step = everyMonths.coerceIn(1, 12)
        var due = dueIso
        // Bounded: twenty steps clears twenty years even at yearly, and a
        // nonsense date can never spin here.
        repeat(20) {
            if (due >= todayIso) return due
            due = addMonths(due, step)
        }
        return due
    }

    /** Same day, [months] later, clamped to a month that has that day. */
    fun addMonths(dateIso: String, months: Int): String {
        val parts = dateIso.split("-")
        if (parts.size < 3) return dateIso
        val year = parts[0].toIntOrNull() ?: return dateIso
        val month = parts[1].toIntOrNull() ?: return dateIso
        val day = parts[2].toIntOrNull() ?: return dateIso
        val zero = (year * 12) + (month - 1) + months
        val newYear = zero / 12
        val newMonth = (zero % 12) + 1
        return "%04d-%02d-%02d".format(newYear, newMonth, minOf(day, daysIn(newYear, newMonth)))
    }

    private fun daysIn(year: Int, month: Int): Int = when (month) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        else -> if (isLeap(year)) 29 else 28
    }

    /**
     * How many monthly instalments are left before a bill falls due.
     *
     * Counted from this month up to, but not including, the month it is due:
     * decided in August for a bill due in January, that is August through
     * December — five. The due month itself is left out so the money is there
     * before the day rather than on it, and being early costs nothing while
     * being short costs the whole point of saving up.
     */
    fun instalmentsUntil(todayIso: String, dueIso: String, resetDay: Int = 1): Int {
        if (dueIso.isEmpty()) return 1
        if (todayIso >= dueIso) return 1
        
        val partsToday = todayIso.split("-")
        val partsDue = dueIso.split("-")
        if (partsToday.size < 3 || partsDue.size < 3) return 1
        
        val startYear = partsToday[0].toIntOrNull() ?: return 1
        val startMonth = partsToday[1].toIntOrNull() ?: return 1
        val endYear = partsDue[0].toIntOrNull() ?: return 1
        val endMonth = partsDue[1].toIntOrNull() ?: return 1
        
        var count = 0
        var currYear = startYear
        var currMonth = startMonth
        
        val endLimit = endYear * 12 + endMonth
        
        while (currYear * 12 + currMonth <= endLimit) {
            val calendar = java.util.Calendar.getInstance()
            calendar.set(java.util.Calendar.YEAR, currYear)
            calendar.set(java.util.Calendar.MONTH, currMonth - 1)
            val maxDay = calendar.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
            val day = minOf(resetDay, maxDay)
            
            val paydayIso = "%04d-%02d-%02d".format(currYear, currMonth, day)
            
            if (paydayIso > todayIso && paydayIso <= dueIso) {
                count++
            }
            
            if (currMonth == 12) {
                currMonth = 1
                currYear++
            } else {
                currMonth++
            }
        }
        
        return count.coerceAtLeast(1)
    }

    /**
     * "5 months, 17 days" — how far off a date is, in the terms you'd say it.
     *
     * Whole months are counted first by advancing the month and keeping the
     * day, then the remaining days; that way 12 August to 29 January is five
     * months and seventeen days rather than an awkward 170.
     */
    fun untilText(todayIso: String, dueIso: String): String {
        val days = daysBetween(todayIso, dueIso)
        if (days <= 0) return "due now"
        var months = 0
        while (months < 600 && addMonths(todayIso, months + 1) <= dueIso) months++
        val rest = daysBetween(addMonths(todayIso, months), dueIso)
        val m = if (months > 0) "$months month${if (months == 1) "" else "s"}" else ""
        val d = if (rest > 0) "$rest day${if (rest == 1) "" else "s"}" else ""
        return listOf(m, d).filter { it.isNotEmpty() }.joinToString(", ").ifEmpty { "due today" }
    }

    /** Whole days between two ISO dates, by day number since a fixed origin. */
    fun daysBetween(fromIso: String, toIso: String): Int {
        val a = dayNumber(fromIso) ?: return 0
        val b = dayNumber(toIso) ?: return 0
        return b - a
    }

    /** Days since 1970-01-01, computed rather than parsed so this stays pure. */
    private fun dayNumber(dateIso: String): Int? {
        val p = dateIso.split("-")
        if (p.size < 3) return null
        val y = p[0].toIntOrNull() ?: return null
        val m = p[1].toIntOrNull() ?: return null
        val d = p[2].toIntOrNull() ?: return null
        var days = 0
        for (year in 1970 until y) days += if (isLeap(year)) 366 else 365
        for (month in 1 until m) days += daysIn(y, month)
        return days + d - 1
    }

    private fun isLeap(year: Int) = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)

    /** The amount split over the months genuinely left, never over more. */
    fun shareUntilDue(amount: Double, todayIso: String, dueIso: String, resetDay: Int = 1): Double =
        monthlyShare(amount, instalmentsUntil(todayIso, dueIso, resetDay))

    private fun monthIndex(dateIso: String): Int? {
        val parts = dateIso.split("-")
        if (parts.size < 2) return null
        val year = parts[0].toIntOrNull() ?: return null
        val month = parts[1].toIntOrNull() ?: return null
        return year * 12 + (month - 1)
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
const val UNCATEGORISED = "Uncategorised"

/**
 * The everyday kinds of spending, which the payee rules below can land on.
 *
 * Every one of these is something you could set a budget against. A shop is
 * not on this list and never becomes one.
 */
val STANDARD_CATEGORIES = listOf(
    "Groceries", "Eating Out", "Utilities", "Home Expenses",
    "Fuel", "Travel", "Shopping", "Health", "EMI", UNCATEGORISED
)

fun categoryForParty(
    party: String,
    categories: List<String>,
    learned: Map<String, String> = emptyMap()
): String {
    val p = party.lowercase().trim()
    // No payee to go on. Left plainly unsorted rather than named after whatever
    // string happened to be handy — the bank's shortcode became "Ad-icicit-s".
    if (p.isEmpty()) return UNCATEGORISED

    // What you filed this payee under last time beats every rule below. Filing
    // Eastern Power under Utilities once should settle it forever.
    learned[payeeKey(party)]?.let { if (it in categories) return it }

    // An existing category named in the payee wins outright.
    categories.firstOrNull { it.isNotBlank() && p.contains(it.lowercase()) }?.let { return it }

    val known = mapOf(
        "Eating Out" to listOf("swiggy", "zomato", "restaurant", "cafe", "eatclub", "dominos", "kfc"),
        "Groceries" to listOf("bigbasket", "blinkit", "zepto", "grocer", "dmart", "mart", "instamart"),
        "Utilities" to listOf(
            "electricity", "power", "energy", "discom", "gas", "water", "broadband",
            "airtel", "jio", "vodafone", "bescom", "tsspdcl", "tgspdcl", "apspdcl",
            "mseb", "torrent power", "adani electricity", "tata power"
        ),
        "Fuel" to listOf("petrol", "diesel", "fuel", "hpcl", "bpcl", "indianoil", "shell"),
        "Travel" to listOf("uber", "ola", "rapido", "irctc", "indigo", "makemytrip", "redbus"),
        "Shopping" to listOf("amazon", "flipkart", "myntra", "ajio", "meesho", "nykaa"),
        "Health" to listOf("pharmacy", "apollo", "medplus", "hospital", "clinic", "diagnostic"),
        "EMI" to listOf("emi", "loan")
    )
    known.forEach { (name, needles) ->
        if (needles.any { p.contains(it) }) return name
    }

    // Nothing fits. Left uncategorised for you to file once.
    //
    // It used to invent a category from the payee, which produced "Eastern
    // Power D" — a payee is not a category, and a list of them is not a
    // budget. One correction teaches it instead, through [learned] above.
    return UNCATEGORISED
}

/** How a payee is looked up: case and punctuation vary between messages from
 *  the same biller, so they must not make it a different payee. */
fun payeeKey(party: String): String =
    party.lowercase().filter { it.isLetterOrDigit() || it == ' ' }.trim().take(24)

// looksLikeSenderId and titleCase lived here, to turn a payee into a category
// name. Nothing does that any more — a payee is not a category — so they went
// with it.
