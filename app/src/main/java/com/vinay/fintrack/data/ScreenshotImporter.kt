package com.vinay.fintrack.data

import android.content.Context
import android.util.Log

/**
 * Turns PhonePe screenshots into transactions and saves them, without needing
 * the app to be on screen — the periodic worker runs this too.
 *
 * Imports are auto-added with no review step, so every one keeps its UTR and
 * the exact text OCR read: a wrong amount can then be found and corrected
 * rather than just being wrong.
 */
class ScreenshotImporter(private val context: Context) {

    private val store = Store(context)
    private val scanner = ScreenshotScanner(context)
    private val mover = ScreenshotMover(context)

    data class Result(val imported: Int, val scannedFrom: Long)

    fun run(): Result {
        val state = store.load()
        val since = state.lastScreenshotScan
        val hits = scanner.scan(since, state.importedRefs)
        val now = System.currentTimeMillis()

        if (hits.isEmpty()) {
            store.save(state.copy(lastScreenshotScan = now))
            return Result(0, since)
        }

        val defaultId = state.accounts.firstOrNull { it.name == state.defaultAccount }?.id
            ?: state.accounts.firstOrNull()?.id.orEmpty()

        var seq = state.nextTxnSeq
        val added = hits.map { hit ->
            val p = hit.parsed
            Txn(
                id = "t${seq++}",
                date = p.date,
                kind = if (p.isCredit) "INCOME" else "EXPENSE",
                amount = p.amount,
                category = categoryFor(p.party, state.categories),
                fromAccountId = if (p.isCredit) "" else defaultId,
                toAccountId = if (p.isCredit) defaultId else "",
                period = p.date.take(7),
                note = p.party,
                ref = p.ref,
                source = "phonepe",
                rawAmountText = p.rawAmountText
            )
        }

        // Filing happens here and needs no prompt, because the copy is a file
        // this app creates. Only removing the original would need consent, and
        // that stays an explicit choice in Settings.
        val filed = hits.count { mover.copyToPhonePe(it.uri) }

        store.save(
            state.copy(
                txns = state.txns + added,
                nextTxnSeq = seq,
                importedRefs = state.importedRefs + added.map { it.ref },
                pendingMoves = state.pendingMoves + hits.map { it.uri.toString() },
                lastScreenshotScan = now
            )
        )
        Log.i(TAG, "filed $filed copies into Pictures/PhonePe")
        Log.i(TAG, "imported ${added.size} PhonePe screenshots")
        return Result(added.size, since)
    }

    /** Best-effort category from the payee name; falls back to Other. */
    private fun categoryFor(party: String, categories: List<String>): String {
        val p = party.lowercase()
        categories.firstOrNull { p.contains(it.lowercase()) }?.let { return it }
        return when {
            listOf("swiggy", "zomato", "restaurant", "cafe").any { p.contains(it) } -> "Eating Out"
            listOf("bigbasket", "blinkit", "zepto", "grocer", "mart").any { p.contains(it) } -> "Groceries"
            listOf("electricity", "gas", "water", "broadband", "airtel", "jio").any { p.contains(it) } -> "Utilities"
            else -> "Other"
        }.takeIf { it in categories } ?: categories.lastOrNull().orEmpty()
    }

    private companion object {
        const val TAG = "ScreenshotImporter"
    }
}
