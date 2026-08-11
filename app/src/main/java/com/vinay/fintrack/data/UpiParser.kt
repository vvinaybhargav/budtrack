package com.vinay.fintrack.data

import java.util.Calendar
import java.util.Locale

/**
 * What a PhonePe receipt screenshot turned into. [ref] is the UTR or
 * transaction id, which is what makes re-scanning the same folder safe.
 */
data class ParsedUpi(
    val amount: Double,
    val isCredit: Boolean,
    val party: String,
    val ref: String,
    val date: String,
    val rawAmountText: String
) {
    val isUsable: Boolean get() = amount > 0 && ref.isNotEmpty()
}

/** Cheap check before doing anything expensive with the text. */
fun looksLikePhonePe(text: String): Boolean {
    val t = text.lowercase()
    return t.contains("phonepe") ||
        (t.contains("utr") && (t.contains("upi") || t.contains("paid to") || t.contains("transaction id")))
}

private val AMOUNT = Regex("""[₹]\s*([\d,]+(?:\.\d{1,2})?)""")
private val BARE_AMOUNT = Regex("""(?:^|\n)\s*([\d,]{2,}(?:\.\d{2})?)\s*(?:$|\n)""")
private val UTR = Regex("""(?:utr|upi\s*(?:transaction\s*)?id|transaction\s*id)\s*[:\-]?\s*([A-Za-z0-9]{8,})""", RegexOption.IGNORE_CASE)
private val PAID_TO = Regex("""paid\s+to\s+(.+)""", RegexOption.IGNORE_CASE)
private val RECEIVED_FROM = Regex("""received\s+from\s+(.+)""", RegexOption.IGNORE_CASE)
private val DATE = Regex("""(\d{1,2})\s+([A-Za-z]{3,})\s+(\d{4})""")

private val MONTHS = listOf(
    "jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec"
)

/**
 * Pulls a transaction out of OCR text. Deliberately forgiving about layout —
 * PhonePe changes it — and anchored on the few things that stay put: a rupee
 * amount, a direction word, and a reference number.
 */
fun parseUpiScreenshot(text: String): ParsedUpi? {
    if (!looksLikePhonePe(text)) return null

    val amountMatch = AMOUNT.find(text) ?: BARE_AMOUNT.find(text)
    val rawAmount = amountMatch?.groupValues?.getOrNull(1).orEmpty()
    val amount = rawAmount.replace(",", "").toDoubleOrNull() ?: 0.0

    val lower = text.lowercase()
    val isCredit = lower.contains("received from") ||
        lower.contains("credited to") ||
        (lower.contains("credited") && !lower.contains("debited"))

    val party = (RECEIVED_FROM.find(text) ?: PAID_TO.find(text))
        ?.groupValues?.getOrNull(1)
        ?.lineSequence()?.firstOrNull()
        ?.trim()
        ?.take(60)
        .orEmpty()

    val ref = UTR.find(text)?.groupValues?.getOrNull(1).orEmpty()

    return ParsedUpi(
        amount = amount,
        isCredit = isCredit,
        party = party.ifEmpty { if (isCredit) "UPI credit" else "UPI payment" },
        ref = ref,
        date = extractDate(text),
        rawAmountText = rawAmount
    ).takeIf { it.isUsable }
}

/** Date off the receipt if it's legible, otherwise today. */
private fun extractDate(text: String): String {
    val m = DATE.find(text) ?: return today()
    val day = m.groupValues[1].toIntOrNull() ?: return today()
    val month = MONTHS.indexOf(m.groupValues[2].take(3).lowercase())
    val year = m.groupValues[3].toIntOrNull() ?: return today()
    if (month < 0) return today()
    val c = Calendar.getInstance()
    c.set(year, month, day, 12, 0, 0)
    return String.format(Locale("en", "IN"), "%04d-%02d-%02d", year, month + 1, day)
}
