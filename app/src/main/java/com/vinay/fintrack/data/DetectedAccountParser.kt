package com.vinay.fintrack.data

import java.util.Locale

/**
 * Result of deterministic rule-based parsing of an SMS for account/card/loan detection.
 * All logic runs 100% on-device using regex and string matching (0% AI / LLM).
 */
data class DetectedFinancialEntity(
    val tail: String,
    val kind: String, // "BANK_ACCOUNT", "CREDIT_CARD", "EMI_LOAN"
    val bankName: String,
    val suggestedName: String,
    val owner: String = "Me",
    val balanceText: String = "0",
    val limitText: String = "50000",
    val minDueText: String = "0",
    val dueDayText: String = "",
    val statementDayText: String = "20",
    val statementAmountText: String = "0",
    val emiText: String = "0",
    val tenureMonthsText: String = "12",
    val originalSms: String = ""
)

/**
 * Suggested recurring bill or subscription detected from regular monthly spending.
 */
data class RecurringSuggestion(
    val party: String,
    val category: String,
    val averageAmount: Double,
    val suggestedDay: Int,
    val occurrences: Int
)

object DetectedAccountParser {

    private val BANK_DICTIONARY = listOf(
        listOf("HDFCBK", "HDFC BANK", "HDFCCC", "HDFC") to "HDFC Bank",
        listOf("ICICIB", "ICICIT", "ICICIC", "ICICI BANK", "ICICI") to "ICICI Bank",
        listOf("SBICAD", "SBICARD", "SBIINB", "STATE BANK", "SBIUPI", "SBI") to "SBI",
        listOf("AXISBK", "AXIS BANK", "UTIB", "AXIS") to "Axis Bank",
        listOf("KOTAKB", "KMB", "KOTAK BANK", "KOTAK") to "Kotak Bank",
        listOf("INDUSIND", "INDUSB", "INDB", "INDUS") to "IndusInd Bank",
        listOf("PUNJAB NATIONAL", "PNB") to "PNB",
        listOf("BANK OF BARODA", "BARODA", "BOB") to "Bank of Baroda",
        listOf("CANARA", "CANBNK", "CNRB") to "Canara Bank",
        listOf("UNION BANK", "UNION", "UBI") to "Union Bank",
        listOf("FEDERAL BANK", "FEDBNK", "FEDERAL") to "Federal Bank",
        listOf("IDFC FIRST", "IDFCFB", "IDFC") to "IDFC FIRST Bank",
        listOf("YES BANK", "YESBNK", "YESBK", "YES") to "Yes Bank",
        listOf("RBL BANK", "RATNAKAR", "RBL") to "RBL Bank",
        listOf("AMERICAN EXPRESS", "AMEX") to "American Express",
        listOf("CITIBANK", "CITIBK", "CITI") to "Citi",
        listOf("STANDARD CHARTERED", "STANCHAR", "SCB") to "Standard Chartered",
        listOf("HSBC") to "HSBC",
        listOf("AU SMALL", "AU BANK", "AUBANK") to "AU Small Finance Bank",
        listOf("BANDHAN BANK", "BANDHAN") to "Bandhan Bank",
        listOf("DIGIBANK", "DBS") to "DBS Bank",
        listOf("PAYTM BANK", "PAYTM", "PYTM") to "Paytm Bank",
        listOf("JUPITER", "FI MONEY", "EPIC") to "Jupiter / Fi",
        listOf("ONECARD") to "OneCard",
        listOf("SLICE") to "Slice",
        listOf("SCAPIA") to "Scapia Card",
        listOf("UNI CARD", "UNI") to "Uni Card"
    )

    private val CARD_KEYWORDS = listOf(
        "credit card", "creditcard", "spent on card", "card ending",
        "card xx", "card no", "statement generated", "min due", "minimum due",
        "total due", "tot due", "avail limit", "available limit", "avail lmt",
        "avl lmt", "cr limit", "credit limit", "pos txn", "e-comm", "statement balance"
    )

    private val LOAN_KEYWORDS = listOf(
        "loan", "emi", "instalment", "tenure", "car loan", "home loan",
        "personal loan", "loan account", "disbursed", "auto debit for emi",
        "emi due", "emi debited"
    )

    /**
     * Extracts bank name by examining both message body and any sender prefix (e.g. "[AD-HDFCBK]").
     */
    fun extractBankName(text: String): String {
        val upper = text.uppercase(Locale.ROOT)
        for ((patterns, bankName) in BANK_DICTIONARY) {
            for (p in patterns) {
                if (upper.contains(p.uppercase(Locale.ROOT))) {
                    return bankName
                }
            }
        }
        return ""
    }

    /**
     * Extracts a monetary amount following one of the given keyword phrases.
     * Prevents false matches by looking for currency indicators or immediate number formatting.
     */
    fun extractAmountAfter(text: String, keywords: List<String>): Double? {
        val lower = text.lowercase(Locale.ROOT)
        for (kw in keywords) {
            val idx = lower.indexOf(kw.lowercase(Locale.ROOT))
            if (idx != -1) {
                val sub = text.substring(idx + kw.length).take(80)
                // Try matching standard Indian currency notations like "Rs. 12,345.50" or "INR 5000" or ": 45,000.00"
                val currMatch = Regex(
                    """(?:[:=\s\-]*(?:is\s*)?(?:rs\.?|inr|₹)?\s*[:=\s\-]*)([\d,]+(?:\.\d{1,2})?)""",
                    RegexOption.IGNORE_CASE
                ).find(sub)

                if (currMatch != null) {
                    val rawNum = currMatch.groupValues[1].replace(",", "")
                    val amt = rawNum.toDoubleOrNull()
                    if (amt != null && amt > 0) return amt
                }
            }
        }
        return null
    }

    /**
     * Extracts day of month (1-31) from date expressions like "due on 15-Aug", "due date 20/09/2026", etc.
     */
    fun extractDueDay(text: String): Int? {
        val patterns = listOf(
            Regex("""(?:due\s+on|due\s+date|payment\s+due|by)\s*[:\-]?\s*(\d{1,2})[-\s/](?:[A-Za-z]{3}|\d{1,2})""", RegexOption.IGNORE_CASE),
            Regex("""(?:statement\s+date|stmt\s+date)\s*[:\-]?\s*(\d{1,2})[-\s/](?:[A-Za-z]{3}|\d{1,2})""", RegexOption.IGNORE_CASE)
        )
        for (pat in patterns) {
            val m = pat.find(text)
            if (m != null) {
                val day = m.groupValues[1].toIntOrNull()
                if (day != null && day in 1..31) return day
            }
        }
        return null
    }

    /**
     * Determines whether the entity is a Bank Account, Credit Card, or Loan/EMI.
     */
    fun classifyKind(text: String, tail: String): String {
        val lower = text.lowercase(Locale.ROOT)
        val upper = text.uppercase(Locale.ROOT)

        // 1. Check for Loan / EMI signals
        if (LOAN_KEYWORDS.any { lower.contains(it) } && (lower.contains("emi") || lower.contains("loan"))) {
            return "EMI_LOAN"
        }

        // 2. Check for Credit Card signals
        val isSenderCard = listOf("CARD", "CC", "SBICAD", "HDFCCC", "ICICIC").any { upper.contains(it) }
        if (isSenderCard || CARD_KEYWORDS.any { lower.contains(it) }) {
            return "CREDIT_CARD"
        }

        // 3. Default to Bank Account
        return "BANK_ACCOUNT"
    }

    /**
     * Full deterministic parsing for a detected tail and message text.
     */
    fun parse(tail: String, smsText: String, activeProfile: String? = "Me"): DetectedFinancialEntity {
        val bank = extractBankName(smsText)
        val kind = classifyKind(smsText, tail)
        val owner = if (!activeProfile.isNullOrBlank()) activeProfile else "Me"

        // Extract balances and amounts
        val balance = extractAmountAfter(
            smsText,
            listOf("avl bal", "avail bal", "available balance", "clear bal", "clr bal", "bal", "balance", "total bal")
        ) ?: 0.0

        val creditLimit = extractAmountAfter(
            smsText,
            listOf("credit limit", "total limit", "avail limit", "available limit", "avail lmt", "avl lmt", "total credit limit", "limit")
        ) ?: 50000.0

        val outstanding = extractAmountAfter(
            smsText,
            listOf("outstanding", "total due", "amt due", "amount due", "tot due", "current balance", "cur bal", "due")
        ) ?: 0.0

        val minDue = extractAmountAfter(
            smsText,
            listOf("min due", "minimum due", "minimum amount due", "mad")
        ) ?: 0.0

        val dueDay = extractDueDay(smsText)
        val emiAmount = extractAmountAfter(
            smsText,
            listOf("emi amount", "monthly emi", "emi of", "emi is", "emi")
        ) ?: (if (balance > 0) balance else 0.0)

        val suggestedName = when (kind) {
            "CREDIT_CARD" -> if (bank.isNotEmpty()) "$bank Card ••$tail" else "Card ••$tail"
            "EMI_LOAN" -> if (bank.isNotEmpty()) "$bank Loan ••$tail" else "Loan ••$tail"
            else -> if (bank.isNotEmpty()) "$bank A/c ••$tail" else "Bank A/c ••$tail"
        }

        return DetectedFinancialEntity(
            tail = tail,
            kind = kind,
            bankName = bank,
            suggestedName = suggestedName,
            owner = owner,
            balanceText = if (balance > 0.0) balance.toLong().toString() else "0",
            limitText = if (creditLimit > 0.0) creditLimit.toLong().toString() else "50000",
            minDueText = if (minDue > 0.0) minDue.toLong().toString() else "0",
            dueDayText = dueDay?.toString() ?: "",
            statementDayText = "20",
            statementAmountText = if (outstanding > 0.0) outstanding.toLong().toString() else "0",
            emiText = if (emiAmount > 0.0) emiAmount.toLong().toString() else "0",
            tenureMonthsText = "12",
            originalSms = smsText
        )
    }

    /**
     * Checks if two account/card tails match (e.g. "345" vs "1345" or exact match).
     */
    fun tailsMatch(a: String, b: String): Boolean {
        val digitsA = a.filter { it.isDigit() }
        val digitsB = b.filter { it.isDigit() }
        if (digitsA.isNotEmpty() && digitsB.isNotEmpty()) {
            return digitsA == digitsB || digitsA.endsWith(digitsB) || digitsB.endsWith(digitsA)
        }
        val cleanA = a.trim()
        val cleanB = b.trim()
        if (cleanA.isEmpty() || cleanB.isEmpty()) return false
        return cleanA.equals(cleanB, ignoreCase = true) || cleanA.endsWith(cleanB, ignoreCase = true) || cleanB.endsWith(cleanA, ignoreCase = true)
    }

    /**
     * Smart Subscription & Recurring Bill Detector (Rule-based).
     * Analyzes transaction history across cycles to find recurring patterns (e.g. electricity, rent, Wi-Fi, Netflix).
     */
    fun detectRecurringBills(txns: List<Txn>, existingEntryCategories: Set<String>): List<RecurringSuggestion> {
        val expenseTxns = txns.filter { it.kind == "EXPENSE" && it.amount > 0 && it.note.isNotBlank() }
        val byPayee = expenseTxns.groupBy { it.note.trim().lowercase(Locale.ROOT) }
        val suggestions = mutableListOf<RecurringSuggestion>()

        for ((_, items) in byPayee) {
            if (items.size < 2) continue
            val first = items.first()
            val category = first.category
            // If already tracked as an entry, skip
            if (category in existingEntryCategories || first.note in existingEntryCategories) continue

            // Check if amounts are within 20% of each other or identical
            val avgAmt = items.map { it.amount }.average()
            val variance = items.all { kotlin.math.abs(it.amount - avgAmt) / avgAmt <= 0.20 }

            if (variance) {
                // Extract days of month
                val days = items.mapNotNull { it.date.split("-").getOrNull(2)?.toIntOrNull() }
                val avgDay = if (days.isNotEmpty()) days.average().toInt().coerceIn(1, 31) else 15
                suggestions.add(
                    RecurringSuggestion(
                        party = first.note,
                        category = if (category.isNotBlank() && category != "Uncategorised") category else "Subscriptions",
                        averageAmount = avgAmt,
                        suggestedDay = avgDay,
                        occurrences = items.size
                    )
                )
            }
        }
        return suggestions.sortedByDescending { it.occurrences }
    }
}
