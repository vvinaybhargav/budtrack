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
    /** Just the amount as the message wrote it, for tracing a misparse. */
    val amountText: String,
    /** A credit that undoes a purchase — a return, a reversal, a chargeback.
     *  Placed after the required fields so existing positional callers still
     *  read correctly. */
    val isRefund: Boolean = false,
    /** The message itself. Kept on the device so an import can be checked and
     *  re-matched; stripped before anything is synced. */
    val body: String = "",
    /** When the bank sent it — the real time of the payment, better than the
     *  moment the app happened to read the message. */
    val receivedAt: Long = 0L
) {
    /** A reference, account tail, or valid merchant is what separates a real
     *  transaction message from an advert that happens to mention rupees. */
    val isUsable: Boolean get() = amount > 0 && (
        ref.isNotEmpty() ||
        accountTail.isNotEmpty() ||
        party.isNotEmpty() ||
        body.contains("paid", ignoreCase = true) ||
        body.contains("debited", ignoreCase = true) ||
        body.contains("credited", ignoreCase = true) ||
        body.contains("spent", ignoreCase = true) ||
        body.contains("sent", ignoreCase = true) ||
        body.contains("received", ignoreCase = true)
    )

    /**
     * Stable id for de-duplication when the bank omits a reference.
     *
     * The direction is part of the key. Moving money between your own banks
     * sends two messages under one reference — debited there, credited here —
     * and they are not a duplicate: dropping the second lost the other half of
     * the transfer.
     */
    val dedupeKey: String
        get() = when {
            ref.isNotEmpty() -> "$ref|${if (isCredit) "c" else "d"}"
            receivedAt > 0L -> {
                // Window into 15-second buckets so same-moment duplicates (SMS receiver + notification listener)
                // match, while separate transactions done moments apart are reliably recognized.
                val timeBucket = receivedAt / 15_000L
                "$date|${"%.2f".format(amount)}|$accountTail|${party.take(20)}|$timeBucket|${if (isCredit) "c" else "d"}"
            }
            else -> "$date|${"%.2f".format(amount)}|$accountTail|${party.take(20)}|${if (isCredit) "c" else "d"}"
        }
}

private val AMOUNT_PREFIX = Regex("""(?:rs\.?|inr|₹)\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
private val AMOUNT_SUFFIX = Regex("""\b([\d,]+(?:\.\d{1,2})?)\s*(?:rs\.?|inr|₹|rupees?|/-)""", RegexOption.IGNORE_CASE)
private val AMOUNT_ACTION = Regex(
    """\b(?:debited(?:\s+(?:by|for|with|of))?|credited(?:\s+(?:by|for|with|of))?|paid|sent|received|spent|transferred(?:\s+(?:by|for|to))?|payment(?:\s+(?:of|to|for))?)\s*(?:of\s+)?(?:rs\.?|inr|₹)?\s*([\d,]+(?:\.\d{1,2})?)\b""",
    RegexOption.IGNORE_CASE
)

private val DEBIT_WORDS = listOf(
    "debited", "debit", "sent", "paid", "withdrawn", "spent", "purchase",
    "payment of", "payment to", "payment for", "transferred", "transfer to",
    "charged", "auto-debited", "autodebit", "auto-debit", "txn done"
)
private val CREDIT_WORDS = listOf(
    "credited", "credit", "received", "deposited", "refund", "refunded",
    "reversed", "reversal", "money added", "cashback", "deposited into",
    "added to", "received from", "credited by", "credited with"
)

/** A credit that is money coming back rather than money earned. Netted off the
 *  spending it reverses instead of counted as income. */
private val REFUND_WORDS = listOf("refund", "refunded", "reversed", "reversal", "returned", "cancelled order", "chargeback")

private val ACCOUNT_TAIL = Regex(
    """\b(?:a/?c|acct|account|card|ending(?:\s+(?:with|in|at))?)\s*(?:no\.?|number|ending(?:\s+(?:with|in|at))?)?\s*[:\-]?\s*[\(\[]*\s*[xX*.\u2026]*\s*(\d{3,6})\b""",
    RegexOption.IGNORE_CASE
)

/** Comprehensive matchers for Indian bank and UPI reference numbers (UTR, UPI Ref, RRN, Txn ID, Order ID, etc.) */
private val REF_PATTERNS = listOf(
    // UPI slash formats: UPI/DR/123456789012 or UPI/CR/123456789012 or UPI/123456789012 or IMPS/123456789012
    Regex("""\b(?:upi|imps|neft|rtgs)/(?:(?:cr|dr)/)?([A-Za-z0-9]*\d{4,}[A-Za-z0-9]*)""", RegexOption.IGNORE_CASE),
    // Standard label formats: UTR: 12345, UPI Ref: 12345, Ref no: 12345, Txn ID: 12345, Order ID: 12345, RRN: 12345
    Regex("""\b(?:upi|utr|imps|neft|rtgs|rrn|txn|trans|transaction|order|ref(?:erence)?)\s*(?:ref(?:erence)?)?\s*(?:no\.?|id|num)?\s*[:\-/]?\s*([A-Za-z0-9]*\d{4,}[A-Za-z0-9]*)""", RegexOption.IGNORE_CASE)
)

private val VPA = Regex("""(?:to|from)\s+(?:vpa\s+)?([A-Za-z0-9._-]+@[A-Za-z]+)""", RegexOption.IGNORE_CASE)
private val TO_NAME = Regex("""\b(?:to|towards|for payment to)\s+([A-Za-z0-9+][A-Za-z0-9 .&'-]{2,40}?)(?=\s+on\b|\s+via\b|\s+using\b|\s+successfully\b|\s+was\b|[.;,]|$)""", RegexOption.IGNORE_CASE)
private val FROM_NAME = Regex("""\bfrom\s+([A-Za-z0-9+][A-Za-z0-9 .&'-]{2,40}?)(?=\s+on\b|\s+via\b|\s+using\b|\s+successfully\b|\s+was\b|[.;,]|$)""", RegexOption.IGNORE_CASE)

/** ICICI's shape: "Acct XX391 debited for Rs 914.00 on 12-Aug-26; Eastern
 *  Power D credited." The payee is named before the word, not after a "to". */
private val CREDITED_NAME = Regex(
    """[;,]\s*([A-Za-z0-9][A-Za-z0-9 .&'-]{2,40}?)\s+credited""",
    RegexOption.IGNORE_CASE
)

/** Card spends: "spent on Card XX4321 at SWIGGY on 09-08-26". */
private val AT_NAME = Regex(
    """\bat\s+([A-Za-z0-9][A-Za-z0-9 .&'-]{2,40}?)(?=\s+on\b|\s+via\b|\s+using\b|\s+successfully\b|\s+was\b|[.;,]|$)""",
    RegexOption.IGNORE_CASE
)

/** Sender ids like AD-ICICIT-S or VM-HDFCBK. Naming a payment after the sender
 *  produced categories such as "Ad-icicit-s" — worse than none at all. */
private val SENDER_ID = Regex("""^[A-Za-z]{2}-[A-Za-z]{4,}""")

private val DATE_NUMERIC = Regex("""(\d{1,2})[-/](\d{1,2})[-/](\d{2,4})""")
private val DATE_NAMED = Regex("""(\d{1,2})[-\s]([A-Za-z]{3})[-\s](\d{2,4})""")
private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

/** Messages that mention money but move none. Naked 'request' avoided to prevent dropping legitimate 'per your request' debits. */
private val NOT_A_TRANSACTION = listOf(
    "otp", "one time password", "will be debited", "will be deducted", "due on",
    "is due", "reminder", "failed", "declined", "unsuccessful",
    "payment request", "collect request", "money request", "requested to pay", "request to pay",
    "cashback offer", "apply now", "eligible", "pre-approved", "e-mandate"
)

fun extractBankRef(body: String): String {
    for (pattern in REF_PATTERNS) {
        val m = pattern.find(body)
        if (m != null) {
            val candidate = m.groupValues.getOrNull(1).orEmpty().trim()
            if (candidate.isNotBlank()) return candidate
        }
    }
    return ""
}

/**
 * Parses a bank SMS into a transaction, or returns null if it isn't one.
 *
 * Written to be conservative: an advert or an OTP that mentions an amount
 * should fall through rather than invent a transaction, because a wrong entry
 * here is worse than a missed one.
 */
fun parseBankSms(
    body: String,
    @Suppress("UNUSED_PARAMETER") sender: String = "",
    receivedAt: Long = 0L
): ParsedSms? {
    if (body.isBlank()) return null
    val lower = body.lowercase()

    if (NOT_A_TRANSACTION.any { lower.contains(it) }) return null

    val directionBody = lower
        .replace("credit card", "cc")
        .replace("creditcard", "cc")
        .replace("credit-card", "cc")

    val isCredit = firstIndexOf(directionBody, CREDIT_WORDS).let { credit ->
        val debit = firstIndexOf(directionBody, DEBIT_WORDS)
        when {
            credit < 0 && debit < 0 -> {
                val drMatch = Regex("""\bdr\.?\b""").find(directionBody)
                val crMatch = Regex("""\bcr\.?\b""").find(directionBody)
                when {
                    crMatch == null && drMatch == null -> return null
                    crMatch == null -> false
                    drMatch == null -> true
                    else -> crMatch.range.first < drMatch.range.first
                }
            }
            credit < 0 -> false
            debit < 0 -> true
            else -> credit < debit          // whichever the bank leads with
        }
    }

    val (amount, amountText) = extractAmountPair(body) ?: return null
    val ref = extractBankRef(body)
    val accountTail = ACCOUNT_TAIL.find(body)?.groupValues?.getOrNull(1).orEmpty()

    // Never the sender: an unnamed payment is better left unnamed than filed
    // under the bank's shortcode, which then became its category too.
    val party = listOfNotNull(
        VPA.find(body)?.groupValues?.getOrNull(1),
        CREDITED_NAME.find(body)?.groupValues?.getOrNull(1),
        (if (isCredit) FROM_NAME else TO_NAME).find(body)?.groupValues?.getOrNull(1),
        AT_NAME.find(body)?.groupValues?.getOrNull(1)
    ).map { it.trim() }
        .firstOrNull { it.isNotBlank() && !SENDER_ID.containsMatchIn(it) }
        .orEmpty()

    return ParsedSms(
        amount = amount,
        isCredit = isCredit,
        party = party.trim().trimEnd('.', ',').take(40),
        ref = ref,
        accountTail = accountTail,
        date = extractDate(body),
        amountText = amountText,
        // Only a credit can be a refund; a debit that mentions "reversal" is
        // more likely a fee charged on one.
        isRefund = isCredit && REFUND_WORDS.any { lower.contains(it) },
        body = if (sender.isNotBlank() && !body.startsWith("[")) "[$sender] ${body.take(300)}" else body.take(300),
        receivedAt = receivedAt
    ).takeIf { it.isUsable }
}

/**
 * The transacted amount, not the balance. Banks routinely append
 * "Avl Bal Rs.53,120" and taking the wrong number would be silently wrong,
 * so anything introduced by a balance phrase is skipped.
 */
private fun extractAmountPair(body: String): Pair<Double, String>? {
    val lower = body.lowercase()

    fun testMatch(m: MatchResult): Pair<Double, String>? {
        val before = lower.substring(maxOf(0, m.range.first - 28), m.range.first)
        if (listOf("bal", "balance", "limit", "outstanding", "avl", "available").any { before.contains(it) }) return null
        val raw = m.groupValues[1]
        val value = raw.replace(",", "").toDoubleOrNull() ?: return null
        return if (value > 0) value to raw else null
    }

    for (m in AMOUNT_PREFIX.findAll(body)) {
        testMatch(m)?.let { return it }
    }
    for (m in AMOUNT_SUFFIX.findAll(body)) {
        testMatch(m)?.let { return it }
    }
    for (m in AMOUNT_ACTION.findAll(body)) {
        testMatch(m)?.let { return it }
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

/** Why an account lookup landed where it did. */
sealed interface AccountMatch {
    data class One(val accountId: String) : AccountMatch
    /** More than one account ends in those digits — guessing would put money on
     *  the wrong one, and personal and joint accounts are the pair most worth
     *  not confusing. */
    data class Ambiguous(val tail: String, val count: Int) : AccountMatch
    object None : AccountMatch
}

/**
 * Finds the account a bank message refers to by its trailing digits.
 *
 * Lengths need not agree: three stored digits match a four-digit message and
 * the other way round, so recording just the last three is enough — as long as
 * they're unique across your accounts.
 */
fun matchAccountByTail(accounts: List<Account>, tail: String): AccountMatch =
    matchByTail(accounts.map { it.id to it.numberTail }, tail)

/** Cards carry digits too: "Card XX4321" is a card spend, not a bank debit. */
fun matchCardByTail(cards: List<Card>, tail: String): AccountMatch =
    matchByTail(cards.map { it.id to it.numberTail }, tail)

/**
 * Words that name no bank in particular, so they must never be what an account
 * is recognised by. Two accounts both called "… Savings" would otherwise match
 * every message mentioning savings.
 */
private val GENERIC_ACCOUNT_WORDS = setOf(
    "bank", "savings", "saving", "account", "acct", "current", "joint", "personal",
    "salary", "primary", "main", "my", "our", "the", "card", "credit", "debit"
)

/**
 * Finds the account a message belongs to by the bank in its name — "ICICI Bank
 * Acct XX391" against an account called "ICICI Joint".
 *
 * A fallback for when digits haven't been recorded yet, so a newly added
 * account starts working straight away rather than everything landing on the
 * shared one. Two accounts at the same bank are reported as ambiguous rather
 * than guessed: that pair is exactly the one worth not confusing.
 */
fun matchAccountByBank(accounts: List<Account>, body: String): AccountMatch {
    val text = body.lowercase()
    val hits = accounts.filter { account ->
        account.name.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .any { it.length >= 3 && it !in GENERIC_ACCOUNT_WORDS && text.contains(it) }
    }
    return when {
        hits.size == 1 -> AccountMatch.One(hits.first().id)
        hits.size > 1 -> AccountMatch.Ambiguous(hits.first().name, hits.size)
        else -> AccountMatch.None
    }
}

/** The same, for cards. */
fun matchCardByBank(cards: List<Card>, body: String): AccountMatch {
    val text = body.lowercase()
    val hits = cards.filter { card ->
        card.name.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .any { it.length >= 3 && it !in GENERIC_ACCOUNT_WORDS && text.contains(it) }
    }
    return when {
        hits.size == 1 -> AccountMatch.One(hits.first().id)
        hits.size > 1 -> AccountMatch.Ambiguous(hits.first().name, hits.size)
        else -> AccountMatch.None
    }
}

private fun matchByTail(items: List<Pair<String, String>>, tail: String): AccountMatch {
    val cleanTail = tail.filter { it.isDigit() }.ifEmpty { tail.trim() }
    if (cleanTail.isBlank()) return AccountMatch.None
    val withTails = items.map { (id, raw) -> id to raw.filter { it.isDigit() }.ifEmpty { raw.trim() } }
        .filter { it.second.isNotBlank() }

    withTails.filter { it.second == cleanTail }.let {
        if (it.size == 1) return AccountMatch.One(it.first().first)
        if (it.size > 1) return AccountMatch.Ambiguous(cleanTail, it.size)
    }

    val suffix = withTails.filter {
        it.second.endsWith(cleanTail) || cleanTail.endsWith(it.second)
    }
    return when {
        suffix.size == 1 -> AccountMatch.One(suffix.first().first)
        suffix.size > 1 -> AccountMatch.Ambiguous(cleanTail, suffix.size)
        else -> AccountMatch.None
    }
}

/** Banks send from ids like AD-HDFCBK or VM-ICICIB rather than a number. */
fun looksLikeBankSender(sender: String): Boolean {
    val s = sender.uppercase()
    if (s.any { it.isDigit() } && s.length >= 10) return false   // an ordinary mobile number
    return BANK_CODES.any { s.contains(it) } || s.contains("-")
}

/**
 * Detects whether a notification text has the hallmark structure of a financial transaction
 * (amount + debit/credit phrasing).
 */
fun looksLikeBankMessage(text: String): Boolean {
    if (text.isBlank()) return false
    val lower = text.lowercase()
    if (NOT_A_TRANSACTION.any { lower.contains(it) }) return false
    val hasAmount = extractAmount(text) != null
    val hasDirection = DEBIT_WORDS.any { lower.contains(it) } ||
        CREDIT_WORDS.any { lower.contains(it) } ||
        Regex("""\b(?:dr|cr)\.?\b""", RegexOption.IGNORE_CASE).containsMatchIn(text)
    return hasAmount && hasDirection
}

private val BANK_CODES = listOf(
    "HDFC", "ICICI", "SBI", "AXIS", "KOTAK", "YESBNK", "IDFC", "INDUS",
    "PNB", "BOB", "CANBNK", "UNION", "FEDBNK", "RBL", "AUBANK", "BANK", "UPI",
    "GPAY", "GOOGLE PAY", "PHONEPE", "PAYTM", "CRED", "BHIM", "AMAZON PAY",
    "SLICE", "JUPITER", "FI MONEY", "MOBIKWIK", "MESSAGES"
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
