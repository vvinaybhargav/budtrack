package com.vinay.fintrack.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class PersistedState(
    val entries: List<Entry> = Seed.entries,
    val accounts: List<Account> = Seed.accounts,
    val loans: List<Loan> = Seed.loans,
    val cards: List<Card> = Seed.cards,
    /** Actual money movements. Confirmation state is derived from these, not stored
     *  separately — so un-confirming is just deleting the transaction. */
    val txns: List<Txn> = emptyList(),
    val budgets: Map<String, Double> = Seed.budgets,
    val profiles: Map<String, String> = mapOf("Me" to "1234", "Wife" to "1234"),
    val categories: List<String> = Seed.categoriesMedium,
    val defaultAccount: String = "ICICI Joint",
    val firebaseConfigText: String = "",
    val openaiKeyText: String = "",
    /** Epoch millis of the last screenshot scan, so each run is incremental. */
    val lastScreenshotScan: Long = 0L,
    /** UTRs already imported — the duplicate guard for a rescan. */
    val importedRefs: Set<String> = emptySet(),
    /** Screenshot URIs waiting for the batched move-consent dialog. */
    val pendingMoves: List<String> = emptyList(),
    val screenshotImportOn: Boolean = false,
    val nextTxnSeq: Int = 1,
    val nextEntrySeq: Int = 16,
    val nextLoanSeq: Int = 4,
    val nextAccountSeq: Int = 5,
    val nextCardSeq: Int = 3
)

class Store(context: Context) {
    private val prefs = context.getSharedPreferences("fintrack", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun load(): PersistedState {
        val raw = prefs.getString(KEY, null) ?: return PersistedState()
        return runCatching { json.decodeFromString<PersistedState>(raw) }.getOrElse { PersistedState() }
    }

    fun save(state: PersistedState) {
        prefs.edit().putString(KEY, json.encodeToString(PersistedState.serializer(), state)).apply()
    }

    private companion object {
        // v2: account balances became opening balances, confirmations became txns.
        const val KEY = "state_v2"
    }
}
