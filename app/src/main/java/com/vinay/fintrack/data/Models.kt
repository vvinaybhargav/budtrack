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
    val note: String = ""
) {
    val monthly: Double get() = if (frequency == "ANNUAL") amount / 12 else amount
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
    val accountId: String = ""
)

/**
 * An actual movement of money, unlike [Entry] which is only the recurring plan.
 * EXPENSE debits [fromAccountId]; INCOME credits [toAccountId]; TRANSFER does both
 * — that is how an annual set-aside keeps the money yours.
 */
@Serializable
data class Txn(
    val id: String,
    val date: String,                 // yyyy-MM-dd
    val kind: String,                 // EXPENSE | INCOME | TRANSFER
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
    val rawAmountText: String = ""
) {
    val month: String get() = period.ifEmpty { date.take(7) }
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
    val paid: Boolean = false
)

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

private val inrFormat: NumberFormat = NumberFormat.getIntegerInstance(Locale("en", "IN"))

fun inr(n: Double): String = "₹" + inrFormat.format(Math.round(n))

fun ownerLabel(person: String): String = if (person == "Joint") "Joint" else "$person · personal"

private val isoDate = java.text.SimpleDateFormat("yyyy-MM-dd", Locale("en", "IN"))
private val isoMonth = java.text.SimpleDateFormat("yyyy-MM", Locale("en", "IN"))
private val prettyFmt = java.text.SimpleDateFormat("d MMM yyyy", Locale("en", "IN"))

fun today(): String = isoDate.format(Calendar.getInstance().time)

fun currentPeriod(): String = isoMonth.format(Calendar.getInstance().time)

/** "2026-08-08" → "8 Aug 2026". Falls back to the raw string if unparseable. */
fun prettyDate(iso: String): String =
    runCatching { prettyFmt.format(isoDate.parse(iso)!!) }.getOrDefault(iso)

/** "2026-08" → "Aug 2026". */
fun prettyMonth(period: String): String =
    runCatching {
        java.text.SimpleDateFormat("MMM yyyy", Locale("en", "IN")).format(isoMonth.parse(period)!!)
    }.getOrDefault(period)

fun monthsToDate(remaining: Int): String {
    val c = Calendar.getInstance()
    c.add(Calendar.MONTH, remaining)
    return java.text.SimpleDateFormat("MMM yyyy", Locale("en", "IN")).format(c.time)
}

object Seed {
    val entries = listOf(
        Entry("e1", "Me", "INCOME", "JOINT", "Salary", 120000.0, "MONTHLY"),
        Entry("e2", "Wife", "INCOME", "JOINT", "Salary", 140000.0, "MONTHLY"),
        Entry("e3", "Me", "EXPENSE", "JOINT", "EMI", 22000.0, "MONTHLY", "Car loan"),
        Entry("e4", "Me", "EXPENSE", "JOINT", "EMI", 15300.0, "MONTHLY", "Home loan"),
        Entry("e5", "Wife", "EXPENSE", "JOINT", "EMI", 27500.0, "MONTHLY", "Car loan"),
        Entry("e6", "Me", "EXPENSE", "PERSONAL", "Health Insurance", 55000.0, "ANNUAL", "Parents"),
        Entry("e7", "Me", "EXPENSE", "JOINT", "Health Insurance", 15000.0, "ANNUAL", "Self + wife"),
        Entry("e8", "Me", "EXPENSE", "JOINT", "Car Insurance", 40000.0, "ANNUAL"),
        Entry("e9", "Me", "EXPENSE", "PERSONAL", "Other", 50000.0, "ANNUAL", "Parents' health"),
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

    val categoriesMedium = listOf(
        "EMI", "Health Insurance", "Car Insurance", "LIC", "Music Classes", "RD", "FD",
        "PPF", "SIP", "Home Expenses", "Groceries", "Eating Out", "Utilities", "Other"
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
