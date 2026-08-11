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

        val parsed = parseBankSms(body, sender)
        if (parsed == null) {
            // Recorded rather than dropped silently: without this there is no way
            // to tell a wrongly-worded alert from one that never arrived.
            store.save(state.copy(smsLog = note(state, "skipped $sender: ${body.take(50)}")))
            return false
        }
        if (parsed.dedupeKey in state.importedRefs) {
            store.save(state.copy(smsLog = note(state, "already had ${inr(parsed.amount)} ${parsed.party}")))
            return false
        }
        store.save(apply(state, listOf(parsed), maxOf(state.lastSmsScan, receivedAt)))
        return true
    }

    private fun note(state: PersistedState, line: String): List<String> =
        (listOf(line) + state.smsLog).take(MAX_LOG)

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

    /**
     * Writes the parsed messages into state as transactions.
     *
     * A payment you already confirmed by hand — an EMI, say — will also be
     * texted about by the bank. Rather than recording it twice, the existing
     * transaction is annotated with the bank's reference.
     */
    private fun apply(state: PersistedState, items: List<ParsedSms>, newest: Long): PersistedState {
        val txns = state.txns.toMutableList()
        var matched = 0
        var added = 0
        val log = mutableListOf<String>()

        for (p in items) {
            val existing = matchingConfirmed(txns, p)
            if (existing != null) {
                txns[txns.indexOf(existing)] = existing.copy(
                    ref = p.ref.ifEmpty { existing.ref },
                    source = "sms+confirm"
                )
                matched++
                log += "matched ${inr(p.amount)} ${p.party} to an existing confirmation"
                continue
            }
            val accountId = accountFor(state, p.accountTail)
            txns += Txn(
                id = newId("t"),
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
            added++
            log += "added ${inr(p.amount)} ${p.party}"
        }

        Log.i(TAG, "SMS import: $added added, $matched matched to existing confirmations")
        return state.copy(
            txns = txns,
            importedRefs = capRefs(state.importedRefs + items.map { it.dedupeKey }),
            smsLog = (log.asReversed() + state.smsLog).take(MAX_LOG),
            lastSmsScan = maxOf(newest, state.lastSmsScan)
        )
    }

    /**
     * An existing hand-confirmed transaction for the same payment: same
     * direction, same amount, within a few days, and carrying no bank
     * reference of its own yet.
     */
    private fun matchingConfirmed(txns: List<Txn>, p: ParsedSms): Txn? {
        val wanted = if (p.isCredit) "INCOME" else "EXPENSE"
        return txns.firstOrNull { t ->
            t.ref.isEmpty() &&
                t.source.isEmpty() &&
                (t.kind == wanted || t.kind == "TRANSFER") &&
                kotlin.math.abs(t.amount - p.amount) < 0.5 &&
                withinDays(t.date, p.date, 4)
        }
    }

    /** Unbounded growth in SharedPreferences helps nobody; recent keys are
     *  enough to stop a re-read duplicating. */
    private fun capRefs(refs: Set<String>): Set<String> =
        if (refs.size <= MAX_REFS) refs else refs.toList().takeLast(MAX_REFS).toSet()

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
        const val MAX_REFS = 500
        const val MAX_LOG = 25
    }
}
