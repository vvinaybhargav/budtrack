package com.vinay.fintrack.data

import android.content.Context
import android.net.Uri
import android.provider.Telephony
import android.util.Log

/**
 * Turns bank SMS into transactions. Runs from the broadcast receiver as
 * messages arrive, and over the inbox for a one-off backfill, so it has to work
 * without the app being on screen.
 *
 * Message text is parsed and discarded — only the parsed fields are stored, and
 * nothing raw is ever uploaded.
 */
class SmsImporter(private val context: Context) {

    private val store = Store(context)

    /** Reads one freshly-arrived message. Returns true if it became a transaction. */
    fun importOne(body: String, sender: String, receivedAt: Long): Boolean {
        val state = store.load()
        if (!state.smsImportOn) return false
        val parsed = parseBankSms(body, sender) ?: return false
        if (parsed.dedupeKey in state.importedRefs) return false
        store.save(apply(state, listOf(parsed), maxOf(state.lastSmsScan, receivedAt)))
        return true
    }

    /**
     * Reads the SMS inbox for the last [days] days. Used once when the feature
     * is switched on, so history isn't lost.
     */
    fun backfill(days: Int = 60): Int {
        val state = store.load()
        val since = maxOf(state.lastSmsScan, daysAgoMillis(days))
        val found = mutableListOf<ParsedSms>()
        var newest = state.lastSmsScan

        val projection = arrayOf(
            Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE
        )
        runCatching {
            context.contentResolver.query(
                Uri.parse("content://sms/inbox"),
                projection,
                "${Telephony.Sms.DATE} > ?",
                arrayOf(since.toString()),
                "${Telephony.Sms.DATE} DESC"
            )?.use { c ->
                val addr = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyCol = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateCol = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
                while (c.moveToNext()) {
                    val sender = c.getString(addr).orEmpty()
                    val body = c.getString(bodyCol).orEmpty()
                    val at = c.getLong(dateCol)
                    newest = maxOf(newest, at)
                    if (!looksLikeBankSender(sender)) continue
                    parseBankSms(body, sender)?.let { found += it }
                }
            }
        }.onFailure { Log.w(TAG, "inbox read failed", it) }

        if (found.isEmpty()) {
            store.save(state.copy(lastSmsScan = maxOf(newest, state.lastSmsScan)))
            return 0
        }
        val fresh = found.distinctBy { it.dedupeKey }
            .filterNot { it.dedupeKey in state.importedRefs }
        store.save(apply(state, fresh, newest))
        return fresh.size
    }

    /** Writes the parsed messages into state as transactions. */
    private fun apply(state: PersistedState, items: List<ParsedSms>, newest: Long): PersistedState {
        var seq = state.nextTxnSeq
        val added = items.map { p ->
            val accountId = accountFor(state, p.accountTail)
            Txn(
                id = "t${seq++}",
                date = p.date,
                kind = if (p.isCredit) "INCOME" else "EXPENSE",
                amount = p.amount,
                category = categoryFor(p.party, state.categories),
                fromAccountId = if (p.isCredit) "" else accountId,
                toAccountId = if (p.isCredit) accountId else "",
                period = p.date.take(7),
                note = p.party,
                ref = p.ref,
                source = "sms",
                rawAmountText = p.body
            )
        }
        Log.i(TAG, "imported ${added.size} transactions from SMS")
        return state.copy(
            txns = state.txns + added,
            nextTxnSeq = seq,
            importedRefs = state.importedRefs + items.map { it.dedupeKey },
            lastSmsScan = maxOf(newest, state.lastSmsScan)
        )
    }

    /** Matches the account by its last digits, falling back to the default. */
    private fun accountFor(state: PersistedState, tail: String): String {
        if (tail.isNotEmpty()) {
            state.accounts.firstOrNull { it.numberTail.isNotEmpty() && it.numberTail.endsWith(tail) }
                ?.let { return it.id }
            state.accounts.firstOrNull { it.numberTail.isNotEmpty() && tail.endsWith(it.numberTail) }
                ?.let { return it.id }
        }
        return state.accounts.firstOrNull { it.name == state.defaultAccount }?.id
            ?: state.accounts.firstOrNull()?.id.orEmpty()
    }

    private fun categoryFor(party: String, categories: List<String>): String {
        val p = party.lowercase()
        categories.firstOrNull { p.contains(it.lowercase()) }?.let { return it }
        val guess = when {
            listOf("swiggy", "zomato", "restaurant", "cafe", "eatclub").any { p.contains(it) } -> "Eating Out"
            listOf("bigbasket", "blinkit", "zepto", "grocer", "dmart", "mart").any { p.contains(it) } -> "Groceries"
            listOf("electricity", "gas", "water", "broadband", "airtel", "jio", "vodafone").any { p.contains(it) } -> "Utilities"
            listOf("emi", "loan").any { p.contains(it) } -> "EMI"
            else -> "Other"
        }
        return guess.takeIf { it in categories } ?: categories.lastOrNull().orEmpty()
    }

    private companion object {
        const val TAG = "SmsImporter"
    }
}
