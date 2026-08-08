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
    val confirmed: Set<String> = emptySet(),
    val profiles: Map<String, String> = mapOf("Me" to "1234", "Wife" to "1234"),
    val categories: List<String> = Seed.categoriesMedium,
    val defaultAccount: String = "ICICI Joint",
    val firebaseConfigText: String = "",
    val openaiKeyText: String = "",
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
        const val KEY = "state_v1"
    }
}
