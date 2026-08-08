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
    val balance: Double
)

@Serializable
data class Loan(
    val id: String,
    val name: String,
    val person: String,
    val monthlyEmi: Double,
    val totalMonths: Int,
    val remainingMonths: Int
)

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

private val inrFormat: NumberFormat = NumberFormat.getIntegerInstance(Locale("en", "IN"))

fun inr(n: Double): String = "₹" + inrFormat.format(Math.round(n))

fun ownerLabel(person: String): String = if (person == "Joint") "Joint" else "$person · personal"

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
        Loan("l1", "Car loan — Me", "Me", 22000.0, 84, 42),
        Loan("l2", "Home loan — Me", "Me", 15300.0, 180, 130),
        Loan("l3", "Car loan — Wife", "Wife", 27500.0, 60, 38)
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
