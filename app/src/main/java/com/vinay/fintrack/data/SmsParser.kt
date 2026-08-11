package com.vinay.fintrack.data

import java.util.Calendar
import java.util.Locale

/**
 * A bank debit or credit read out of an SMS.
 *
 * [accountTail] is the part OCR could never give us: the last digits of the
 * account the money actually moved through, which is what lets a transaction
 * land on the right account instead of a guess.
 */
data class ParsedSms(
    val amount: Double,
    val isCredit: Boolean,
    val party: String,
    val ref: String,
    val accountTail: String,
    val date: String,
    /** Just the amount as the message wrote it, for tracing a misparse. The
     *  whole body was being stored and synced, and bank texts carry account
     *  numbers and balances. */
    val amountText: String
) {
    /** A reference, or failing that the account, is what separates a real
     *  transaction message from an advert that happens to mention rupees. */
    val isUsable: Boolean get() = amount > 0 && (ref.isNotEmpty() || accountTail.isNotEmpty())

    /** Stable id for de-duplication when the bank omits a reference. */
    val dedupeKey: String
        get() = if (ref.isNotEmpty()) ref
        else "$date|${"%.2f".format(amount)}|$accountTail|${party.take(12)}"
}

private val AMOUNT = Regex("""(?:rs\.?|inr)\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
private val DEBIT_WORDS = listOf("debited", "debit", "sent", "paid", "withdrawn", "spent", "purchase")
private val CREDIT_WORDS = listOf("credited", "credit", "received", "deposited", "refund")

private val ACCOUNT_TAIL = Regex(
    """(?:a/?c|acct|account|card)\s*(?:no\.?|number)?\s*[:\-]?\s*[xX*]+\s*(\d{3,6})""",
    RegexOption.IGNORE_CASE
)
private val REF = Regex(
    """(?:upi|imps|neft|rrn|txn|transaction|ref(?:erence)?)\s*(?:ref(?:erence)?)?\s*(?:no\.?|id)?\s*[:\-]?\s*(\d{6,})""",
    RegexOption.IGNORE_CASE
)
private val VPA = Regex("""(?:to|from)\s+(?:vpa\s+)?([A-Za-z0-9._-]+@[A-Za-z]+)""", RegexOption.IGNORE_CASE)
private val TO_NAME = Regex("""\b(?:to|towards)\s+([A-Za-z][A-Za-z .&'-]{2,40})""", RegexOption.IGNORE_CASE)
private val FROM_NAME = Regex("""\bfrom\s+([A-Za-z][A-Za-z .&'-]{2,40})""", RegexOption.IGNORE_CASE)

private val DATE_NUMERIC = Regex("""(\d{1,2})[-/](\d{1,2})[-/](\d{2,4})""")
private val DATE_NAMED = Regex("""(\d{1,2})[-\s]([A-Za-z]{3})[-\s](\d{2,4})""")
private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

/** Messages that mention money but move none. */
private val NOT_A_TRANSACTION = listOf(
    "otp", "one time password", "will be debited", "will be deducted", "due on",
    "is due", "reminder", "failed", "declined", "unsuccessful", "request",
    "cashback offer", "apply now", "eligible", "pre-approved", "e-mandate"
)

/**
 * Parses a bank SMS into a transaction, or returns null if it isn't one.
 *
 * Written to be conservative: an advert or an OTP that mentions an amount
 * should fall through rather than invent a transaction, because a wrong entry
 * here is worse than a missed one.
 */
fun parseBankSms(body: String, sender: String = ""): ParsedSms? {
    if (body.isBlank()) return null
    val lower = body.lowercase()

    if (NOT_A_TRANSACTION.any { lower.contains(it) }) return null

    val isCredit = firstIndexOf(lower, CREDIT_WORDS).let { credit ->
        val debit = firstIndexOf(lower, DEBIT_WORDS)
        when {
            credit < 0 && debit < 0 -> return null
            credit < 0 -> false
            debit < 0 -> true
            else -> credit < debit          // whichever the bank leads with
        }
    }

    val (amount, amountText) = extractAmountPair(body) ?: return null
    val ref = REF.find(body)?.groupValues?.getOrNull(1).orEmpty()
    val accountTail = ACCOUNT_TAIL.find(body)?.groupValues?.getOrNull(1).orEmpty()

    val party = VPA.find(body)?.groupValues?.getOrNull(1)
        ?: (if (isCredit) FROM_NAME else TO_NAME).find(body)?.groupValues?.getOrNull(1)?.trim()
        ?: sender.ifEmpty { if (isCredit) "Credit" else "Payment" }

    return ParsedSms(
        amount = amount,
        isCredit = isCredit,
        party = party.trim().trimEnd('.', ',').take(40),
        ref = ref,
        accountTail = accountTail,
        date = extractDate(body),
        amountText = amountText
    ).takeIf { it.isUsable }
}

/**
 * The transacted amount, not the balance. Banks routinely append
 * "Avl Bal Rs.53,120" and taking the wrong number would be silently wrong,
 * so anything introduced by a balance phrase is skipped.
 */
private fun extractAmountPair(body: String): Pair<Double, String>? {
    val lower = body.lowercase()
    for (m in AMOUNT.findAll(body)) {
        val before = lower.substring(maxOf(0, m.range.first - 28), m.range.first)
        if (listOf("bal", "balance", "limit", "outstanding").any { before.contains(it) }) continue
        val raw = m.groupValues[1]
        val value = raw.replace(",", "").toDoubleOrNull() ?: continue
        if (value > 0) return value to raw
    }
    return null
}

private fun extractAmount(body: String): Double? = extractAmountPair(body)?.first

private fun firstIndexOf(text: String, words: List<String>): Int =
    words.map { text.indexOf(it) }.filter { it >= 0 }.minOrNull() ?: -1

private fun extractDate(body: String): String {
    DATE_NAMED.find(body)?.let { m ->
        val day = m.groupValues[1].toIntOrNull()
        val month = MONTHS.indexOf(m.groupValues[2].lowercase())
        val year = normaliseYear(m.groupValues[3])
        if (day != null && month >= 0 && year != null) return iso(year, month + 1, day)
    }
    DATE_NUMERIC.find(body)?.let { m ->
        val day = m.groupValues[1].toIntOrNull()
        val month = m.groupValues[2].toIntOrNull()
        val year = normaliseYear(m.groupValues[3])
        if (day != null && month != null && year != null && month in 1..12 && day in 1..31) {
            return iso(year, month, day)
        }
    }
    return today()
}

private fun normaliseYear(raw: String): Int? {
    val n = raw.toIntOrNull() ?: return null
    return when {
        n in 1000..9999 -> n
        n < 100 -> 2000 + n
        else -> null
    }
}

private fun iso(year: Int, month: Int, day: Int): String =
    String.format(Locale("en", "IN"), "%04d-%02d-%02d", year, month, day)

/**
 * Why a message wasn't treated as a transaction, in words that don't echo the
 * message. The log this feeds is stored and was previously synced, and bank
 * texts include OTPs — so it says what failed, never what was said.
 */
fun skipReason(body: String): String {
    val lower = body.lowercase()
    NOT_A_TRANSACTION.firstOrNull { lower.contains(it) }?.let { return "not a payment (\"$it\")" }
    val hasDirection = DEBIT_WORDS.any { lower.contains(it) } || CREDIT_WORDS.any { lower.contains(it) }
    if (!hasDirection) return "no debit or credit wording"
    if (extractAmountOrNull(body) == null) return "no amount found"
    return "no reference or account number"
}

internal fun extractAmountOrNull(body: String): Double? = extractAmount(body)

/** Banks send from ids like AD-HDFCBK or VM-ICICIB rather than a number. */
fun looksLikeBankSender(sender: String): Boolean {
    val s = sender.uppercase()
    if (s.any { it.isDigit() } && s.length >= 10) return false   // an ordinary mobile number
    return BANK_CODES.any { s.contains(it) } || s.contains("-")
}

private val BANK_CODES = listOf(
    "HDFC", "ICICI", "SBI", "AXIS", "KOTAK", "YESBNK", "IDFC", "INDUS",
    "PNB", "BOB", "CANBNK", "UNION", "FEDBNK", "RBL", "AUBANK", "BANK", "UPI"
)

/** True when two yyyy-MM-dd dates are at most [days] apart. */
fun withinDays(a: String, b: String, days: Int): Boolean {
    val d1 = epochDay(a) ?: return false
    val d2 = epochDay(b) ?: return false
    return kotlin.math.abs(d1 - d2) <= days
}

private fun epochDay(iso: String): Long? {
    val parts = iso.split("-")
    if (parts.size != 3) return null
    val y = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    val d = parts[2].toIntOrNull() ?: return null
    val c = Calendar.getInstance()
    c.clear()
    c.set(y, m - 1, d)
    return c.timeInMillis / 86_400_000L
}

/** Midnight today, for bounding an inbox backfill. */
fun daysAgoMillis(days: Int): Long {
    val c = Calendar.getInstance()
    c.add(Calendar.DAY_OF_YEAR, -days)
    return c.timeInMillis
}
