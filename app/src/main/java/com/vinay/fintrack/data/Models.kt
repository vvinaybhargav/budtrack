package com.vinay.fintrack.data

import kotlinx.serialization.Serializable
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

@Serializable
data class Entry(
    val id: String,
    val person: String,
    val type: String,      // INCOME | EXPENSE | SAVINGS
    val bucket: String,    // JOINT | PERSONAL
    val category: String,
    val amount: Double,
    val frequency: String, // MONTHLY | ANNUAL | ONE_TIME
    val note: String = "",
    /** Account this is normally paid from, chosen when the entry is created so
     *  confirming it doesn't start from a guess. */
    val accountId: String = "",
    /** How many months between payments, 1–12. Zero means fall back to
     *  [frequency], so entries written before this field still read correctly. */
    val periodMonths: Int = 0,
    val startDate: String = "",
    /** When the bill is actually due, as YYYY-MM-DD. Empty means it isn't
     *  known, and the amount is simply split over [everyMonths]. */
    val dueDate: String = "",
    /**
     * Finished with. A set-aside that has been saved up and paid, or a bill that
     * has stopped: kept for its history rather than deleted, but off Home and
     * out of the month's plan.
     */
    val closed: Boolean = false,
    /** Marked as money lent to someone, tracked inside Set a Side. */
    val isLent: Boolean = false
) {
    val everyMonths: Int
        get() = when {
            periodMonths in 1..12 -> periodMonths
            frequency == "ANNUAL" -> 12
            else -> 1
        }

    val nextDue: String get() = Ledger.nextDue(dueDate, everyMonths, today())

    val monthly: Double
        get() = monthly(1)

    fun monthly(resetDay: Int): Double = when {
        frequency == "ONE_TIME" -> amount
        dueDate.isNotEmpty() -> {
            val start = if (startDate.isNotEmpty()) startDate else today()
            val instalments = Ledger.instalmentsBetween(start, nextDue, resetDay)
            Ledger.monthlyShare(amount, instalments)
        }
        else -> Ledger.monthlyShare(amount, everyMonths)
    }

    val isSetAside: Boolean get() = isLent || (frequency != "ONE_TIME" && (everyMonths > 1 || dueDate.isNotEmpty() || frequency == "ANNUAL" || type == "SAVINGS"))
}

@Serializable
data class Account(
    val id: String,
    val name: String,
    val owner: String,
    val person: String,
    /** Balance before any recorded transaction. The live balance is derived —
     *  see [FinTrackViewModel.balanceOf] — so undoing a confirm reverses itself. */
    val openingBalance: Double,
    /** Last digits as the bank writes them in its SMS ("A/c XX1234" → "1234").
     *  This is how an imported message lands on the right account. */
    val numberTail: String = ""
)

@Serializable
data class Loan(
    val id: String,
    val name: String,
    val person: String,
    val monthlyEmi: Double,
    val totalMonths: Int,
    val remainingMonths: Int,
    /** EMI is always paid from here, so confirming a loan needs no prompt. */
    val accountId: String = "",
    /**
     * Set when the EMI is charged to a credit card rather than a bank account —
     * a purchase converted to instalments. The instalment adds to what the card
     * owes; no bank balance moves until the card bill itself is settled.
     */
    val cardId: String = "",
    val startMonth: String = "", // YYYY-MM
    val startDate: String = "",  // YYYY-MM-DD
    /** The day the EMI comes out, as YYYY-MM-DD. Empty when it isn't known. */
    val dueDate: String = "",
    val dueDay: Int = 0,
    val lastProcessedMonth: String = ""
) {
    /** The next EMI date, rolled past any already gone. Monthly by definition. */
    val nextDue: String get() = Ledger.nextDue(dueDate, 1, today())

    val onCard: Boolean get() = cardId.isNotEmpty()

    fun isStarted(todayIso: String = today(), resetDay: Int = 1): Boolean {
        if (startMonth.isNotEmpty()) {
            val currentCycle = Ledger.cycleOf(todayIso, resetDay)
            return currentCycle >= startMonth
        }
        if (startDate.isNotEmpty()) {
            return todayIso >= startDate
        }
        return true
    }
}

@Serializable
data class MissingConfigItem(
    val entityType: String,
    val entityId: String,
    val name: String,
    val missingFields: List<String>
)

@Serializable
data class Debt(
    val id: String,
    val person: String,                // Profile owner e.g. "Vinay" or "Joint"
    val type: String,                  // "LENT" (I gave money, they owe me) | "BORROWED" (I took money, I owe them)
    val peerName: String,              // Person's name (e.g. "Rahul", "Priya")
    val amount: Double,                // Total amount
    val dueDate: String = "",          // Expected payback/return date (YYYY-MM-DD or DD-MM-YYYY)
    val accountId: String = "",        // Linked account (if debited or credited)
    val settled: Boolean = false,      // Fully returned / settled
    val settledAmount: Double = 0.0,   // Amount returned so far
    val settledDate: String = "",      // Date settled (YYYY-MM-DD)
    val note: String = "",             // Note / purpose
    val createdAt: Long = 0L           // Epoch millis
) {
    val remainingAmount: Double get() = (amount - settledAmount).coerceAtLeast(0.0)
    val isLent: Boolean get() = type.equals("LENT", ignoreCase = true)
    val isBorrowed: Boolean get() = type.equals("BORROWED", ignoreCase = true)
}


/**
 * An actual movement of money, unlike [Entry] which is only the recurring plan.
 * EXPENSE debits [fromAccountId]; INCOME credits [toAccountId]; TRANSFER does both
 * — that is how an annual set-aside keeps the money yours.
 *
 * REFUND also credits an account, but it is money coming back rather than money
 * earned: it reduces what the month spent instead of adding to what it received.
 */
@Serializable
data class Txn(
    val id: String,
    val date: String,                 // yyyy-MM-dd
    val kind: String,                 // EXPENSE | INCOME | TRANSFER | REFUND
    val amount: Double,
    val category: String = "",
    val fromAccountId: String = "",
    val toAccountId: String = "",
    val entryId: String = "",         // the commitment this settles, if any
    val loanId: String = "",
    val cardId: String = "",
    val period: String = "",          // yyyy-MM the confirmation belongs to
    val note: String = "",
    /** Bank reference (UTR/RRN) for imported ones — what stops a re-read
     *  recording the same payment twice. */
    val ref: String = "",
    /** "sms" when read from a bank alert, empty when entered by hand. */
    val source: String = "",
    /** The amount exactly as the source wrote it, for tracing a misparse. */
    val rawAmountText: String = "",
    /** When it happened, to the minute. Recorded automatically: the date alone
     *  left several payments on one day with no order between them. Zero for
     *  anything written before this field, which falls back to the date. */
    val at: Long = 0L,
    val borrowedFrom: String = "",
    val returned: Boolean = false,
    val returnDate: String = "",
    val returnedAmount: Double = 0.0,
    /**
     * The account digits the bank message quoted, when they matched no account.
     *
     * Kept so the row can say which account the bank meant, and so choosing one
     * can record those digits against it — after which every later message from
     * that account matches on its own.
     */
    val accountTail: String = ""
) {
    val month: String get() = period.ifEmpty { date.take(7) }

    /** Newest-first ordering that still works for older, untimed records. */
    val sortKey: Long get() = if (at > 0L) at else millisOfDate(date)

    /** "11 Aug 2026, 14:35", or just the date when no time was recorded. */
    val whenText: String
        get() = if (at > 0L) "${prettyDate(date)}, ${clockOf(at)}" else prettyDate(date)
}

@Serializable
data class Card(
    val id: String,
    val name: String,
    val owner: String,
    val limit: Double,
    val balance: Double,
    val minDue: Double,
    val due: String,
    val paid: Boolean = false,
    /** Last digits as the bank writes them ("Card XX4321"). A card spend is
     *  matched on these, and adds to the card rather than to any account. */
    val numberTail: String = "",
    /**
     * The bill date as YYYY-MM-DD, rolled forward monthly.
     *
     * [due] before this was free text like "18 Sep", which reads fine and can be
     * compared to nothing — so the one payment with a real late fee attached was
     * the only thing the app could not warn about.
     */
    val dueDate: String = "",
    val statementDay: Int = 20,
    val statementAmount: Double = 0.0
) {
    /** The next bill date, past any already gone. Cards bill monthly. */
    val nextDue: String get() = Ledger.nextDue(dueDate, 1, today())

    /** What to show: the real date when there is one, else the old free text. */
    val dueText: String get() = if (nextDue.isNotEmpty()) prettyDate(nextDue) else due
}

@Serializable
data class ChatMessage(val role: String, val text: String)

/**
 * Ids must be unique across devices, not just within one. A counter like
 * "t12" collides when two phones both add a transaction offline, and since
 * each transaction is its own Firestore document, the collision silently
 * overwrites one of them.
 */
fun newId(prefix: String): String =
    prefix + java.util.UUID.randomUUID().toString().replace("-", "").take(16)

/**
 * Stores a PIN as a hash so the profile list can be shared between phones
 * without the actual digits travelling with it.
 *
 * A four-digit PIN has ten thousand possibilities, so this is no defence
 * against someone determined who has the database — it stops the PIN being
 * readable at a glance, which is the realistic risk.
 */
fun hashPin(pin: String): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    // Not salted with the profile name: renaming a profile would otherwise
    // invalidate its PIN, and the plaintext isn't available to re-hash with.
    val bytes = digest.digest("fintrack:$pin".toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}

/** Plain four digits, as everything before hashing stored them. */
fun looksLikePlainPin(value: String): Boolean =
    value.length in 4..6 && value.all { it.isDigit() }

private val inrFormat: NumberFormat = NumberFormat.getInstance(Locale("en", "IN")).apply {
    minimumFractionDigits = 2
    maximumFractionDigits = 2
}

fun inr(n: Double): String {
    val isNeg = n < 0
    val absVal = kotlin.math.abs(n)
    val formatted = inrFormat.format(absVal)
    return if (isNeg) "-₹$formatted" else "₹$formatted"
}

fun friendlyCycle(cycle: String): String {
    val parts = cycle.split("-")
    if (parts.size == 2) {
        val y = parts[0]
        val m = parts[1].toIntOrNull()
        val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        if (m != null && m in 1..12) {
            return "${monthNames[m - 1]} $y"
        }
    }
    return cycle
}

fun ownerLabel(person: String): String = if (person == "Joint") "Joint" else "$person · personal"

private val isoDate = java.text.SimpleDateFormat("yyyy-MM-dd", Locale("en", "IN"))
private val isoMonth = java.text.SimpleDateFormat("yyyy-MM", Locale("en", "IN"))
private val prettyFmt = java.text.SimpleDateFormat("d MMM yyyy", Locale("en", "IN"))

fun today(): String = isoDate.format(Calendar.getInstance().time)

fun addDays(iso: String, days: Int): String = runCatching {
    val c = Calendar.getInstance()
    c.time = isoDate.parse(iso)!!
    c.add(Calendar.DAY_OF_YEAR, days)
    isoDate.format(c.time)
}.getOrDefault(iso)

fun currentPeriod(): String = isoMonth.format(Calendar.getInstance().time)

/** "2026-08-08" → "8 Aug 2026". Falls back to the raw string if unparseable. */
fun prettyDate(iso: String): String =
    runCatching { prettyFmt.format(isoDate.parse(iso)!!) }.getOrDefault(iso)

/** "2026-08" → "Aug 2026". */
fun prettyMonth(period: String): String =
    runCatching {
        java.text.SimpleDateFormat("MMM yyyy", Locale("en", "IN")).format(isoMonth.parse(period)!!)
    }.getOrDefault(period)

private val clockFmt = java.text.SimpleDateFormat("HH:mm", Locale("en", "IN"))

/** 24-hour, matching how the build timestamps read. */
fun clockOf(millis: Long): String = clockFmt.format(java.util.Date(millis))

/** Midday on the given date, so a date-only record sorts inside its own day. */
fun millisOfDate(iso: String): Long =
    runCatching {
        val c = Calendar.getInstance()
        c.time = isoDate.parse(iso)!!
        c.set(Calendar.HOUR_OF_DAY, 12)
        c.timeInMillis
    }.getOrDefault(0L)

private val dayFirst = java.text.SimpleDateFormat("dd-MM-yyyy", Locale("en", "IN"))

/** Today as people write it here: 11-08-2026. */
fun todayDayFirst(): String = dayFirst.format(Calendar.getInstance().time)

/** "2027-01-29" → "29-01-2027", the way the forms take a date back. */
fun dayFirstOf(iso: String): String {
    val p = iso.split("-")
    return if (p.size < 3) "" else "${p[2]}-${p[1]}-${p[0]}"
}

/**
 * "11-08-2026" → "2026-08-11", day first as written in India. Accepts `-`,
 * `/`, `.` or spaces, and a two-digit year. Null when it isn't a real date, so
 * the caller can fall back rather than storing something nonsensical.
 */
fun isoFromDayFirst(text: String): String? {
    val m = Regex("""(\d{1,2})\s*[-/. ]\s*(\d{1,2})\s*[-/. ]\s*(\d{2,4})""").find(text.trim())
        ?: return null
    val day = m.groupValues[1].toIntOrNull() ?: return null
    val month = m.groupValues[2].toIntOrNull() ?: return null
    var year = m.groupValues[3].toIntOrNull() ?: return null
    if (year < 100) year += 2000
    if (month !in 1..12 || day !in 1..31) return null
    return String.format(Locale("en", "IN"), "%04d-%02d-%02d", year, month, day)
}

/** Converts both DD-MM-YYYY and YYYY-MM-DD to standard ISO YYYY-MM-DD. */
fun normalizeDateToIso(text: String): String? {
    val trimmed = text.trim()
    if (Regex("""^\d{4}-\d{2}-\d{2}$""").matches(trimmed)) {
        val parts = trimmed.split("-")
        val y = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        val d = parts[2].toIntOrNull() ?: return null
        if (m in 1..12 && d in 1..31 && y in 2000..2099) return trimmed
    }
    return isoFromDayFirst(trimmed)
}

fun monthsToDate(remaining: Int): String {
    val c = Calendar.getInstance()
    c.add(Calendar.MONTH, remaining)
    return java.text.SimpleDateFormat("MMM yyyy", Locale("en", "IN")).format(c.time)
}

object Seed {
    val entries = listOf(
        Entry("e1", "Me", "INCOME", "JOINT", "Salary", 120000.0, "MONTHLY"),
        Entry("e2", "Wife", "INCOME", "JOINT", "Salary", 140000.0, "MONTHLY"),
        // No EMI entries: the loans below carry those, and having both meant the
        // same debt counted twice while showing on neither list.
        Entry("e6", "Me", "EXPENSE", "PERSONAL", "Health Insurance", 55000.0, "ANNUAL", "Parents"),
        Entry("e7", "Me", "EXPENSE", "JOINT", "Health Insurance", 15000.0, "ANNUAL", "Self + wife"),
        Entry("e8", "Me", "EXPENSE", "JOINT", "Car Insurance", 40000.0, "ANNUAL"),
        Entry("e9", "Me", "EXPENSE", "PERSONAL", "Parents", 50000.0, "ANNUAL", "Parents' health"),
        Entry("e10", "Wife", "EXPENSE", "PERSONAL", "Music Classes", 4500.0, "MONTHLY"),
        Entry("e11", "Wife", "EXPENSE", "PERSONAL", "Music Classes", 1500.0, "MONTHLY"),
        Entry("e12", "Wife", "SAVINGS", "JOINT", "RD", 20000.0, "MONTHLY"),
        Entry("e13", "Me", "SAVINGS", "PERSONAL", "LIC", 40000.0, "ANNUAL"),
        Entry("e14", "Wife", "SAVINGS", "PERSONAL", "LIC", 35000.0, "ANNUAL"),
        Entry("e15", "Wife", "SAVINGS", "PERSONAL", "PPF", 50000.0, "ANNUAL")
    )

    val accounts = listOf(
        Account("a1", "ICICI Joint", "Joint", "Joint", 485000.0),
        Account("a2", "SBI Savings", "Me · personal", "Me", 120000.0),
        Account("a3", "HDFC Savings", "Wife · personal", "Wife", 95000.0),
        Account("a4", "Sinking Fund", "Joint · set-aside", "Joint", 18000.0)
    )

    val loans = listOf(
        Loan("l1", "Car loan — Me", "Me", 22000.0, 84, 42, "a1"),
        Loan("l2", "Home loan — Me", "Me", 15300.0, 180, 130, "a1"),
        Loan("l3", "Car loan — Wife", "Wife", 27500.0, 60, 38, "a3")
    )

    val cards = listOf(
        Card("cc1", "HDFC Regalia", "Me", 300000.0, 42500.0, 2200.0, "18 Sep"),
        Card("cc2", "ICICI Amazon Pay", "Wife", 150000.0, 68200.0, 3400.0, "22 Sep")
    )

    val budgets = linkedMapOf(
        "Music Classes" to 6500.0,
        "Health Insurance" to 5800.0,
        "Car Insurance" to 3800.0
    )

    /**
     * Kinds of spending, not places you spent it.
     *
     * Every category the payee rules can land on is here, so a petrol payment
     * finds Fuel rather than creating it, and the list stays a set of budgets
     * you could actually keep.
     */
    val categoriesMedium = listOf(
        "Groceries", "Eating Out", "Utilities", "Home Expenses", "Fuel", "Travel",
        "Shopping", "Health", "EMI", "Health Insurance", "Car Insurance", "LIC",
        "Music Classes", "RD", "FD", "PPF", "SIP", UNCATEGORISED
    )
}

val INVEST_CATEGORIES = listOf("LIC", "PPF")
val SAVINGS_CATEGORIES = listOf("RD", "FD")
val INVEST_PICKABLE = listOf("LIC", "PPF", "SIP", "Mutual Funds", "Stocks", "Gold")

data class SmartParse(
    val person: String,
    val type: String,
    val bucket: String,
    val category: String,
    val amount: Double,
    val frequency: String,
    val note: String
)

fun parseSmartAdd(text: String, categories: List<String>): SmartParse {
    val lower = text.lowercase()
    var amount = 0.0
    Regex("([\\d,.]+)\\s*(k)?").find(lower)?.let { m ->
        amount = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: 0.0
        if (m.groupValues[2] == "k") amount *= 1000
    }
    val person = if (lower.contains("wife")) "Wife" else "Me"
    val category = categories.firstOrNull { c ->
        val cl = c.lowercase()
        val singular = when {
            cl.endsWith("es") -> cl.dropLast(2)
            cl.endsWith("s") -> cl.dropLast(1)
            else -> cl
        }
        lower.contains(cl) || lower.contains(singular)
    } ?: categories.firstOrNull().orEmpty()
    val frequency = if (Regex("annual|/yr|yearly").containsMatchIn(lower)) "ANNUAL" else "MONTHLY"
    val type = when {
        lower.contains("salary") || lower.contains("income") -> "INCOME"
        INVEST_PICKABLE.contains(category) || SAVINGS_CATEGORIES.contains(category) -> "SAVINGS"
        else -> "EXPENSE"
    }
    val bucket = if (lower.contains("personal")) "PERSONAL" else "JOINT"
    return SmartParse(person, type, bucket, category, amount, frequency, text)
}
