package com.vinay.fintrack.data

import android.content.Context
import android.content.SharedPreferences
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
    /** Joint is a profile you can sign in to, not only a side of the data:
     *  signing in as it shows the shared accounts and commitments and nothing
     *  personal. Both people know its PIN. */
    val profiles: Map<String, String> =
        mapOf("Me" to "1234", "Wife" to "1234", "Joint" to "1234"),
    val categories: List<String> = Seed.categoriesMedium,
    val defaultAccount: String = "ICICI Joint",
    val firebaseConfigText: String = "",
    val openaiKeyText: String = "",
    /** When this device last changed anything. Compared against the remote
     *  document's updatedAt so a newer local edit isn't overwritten by an older
     *  snapshot — the failure mode when editing offline. */
    val localUpdatedAt: Long = 0L,
    /** Epoch millis of the newest SMS already read, so a backfill is incremental. */
    val lastSmsScan: Long = 0L,
    /** References already imported — the duplicate guard against a re-read,
     *  and against the same payment arriving twice from different senders. */
    val importedRefs: Set<String> = emptySet(),
    /** Profile this device signs in as, so the picker is skipped on launch.
     *  Device-local: the other phone belongs to the other person. */
    val lastProfile: String = "",
    /** Whether the PIN is still asked for once a profile is remembered. Off by
     *  default: on a phone that belongs to one person it was pure friction. */
    val askPinOnLaunch: Boolean = false,
    val smsImportOn: Boolean = false,
    /** Recent import decisions, newest first, capped — the only way to see why
     *  a bank message didn't become a transaction. */
    val smsLog: List<String> = emptyList()
)

class Store(context: Context) {
    private val prefs = context.getSharedPreferences("fintrack", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun load(): PersistedState {
        val raw = prefs.getString(KEY, null) ?: return PersistedState()
        return runCatching { json.decodeFromString<PersistedState>(raw) }.getOrElse { PersistedState() }
    }

    /** @return the revision this write produced, so a caller can recognise
     *  its own writes and ignore them when the change listener fires. */
    fun save(state: PersistedState): Int {
        val next = revision() + 1
        prefs.edit()
            .putString(KEY, json.encodeToString(PersistedState.serializer(), state))
            .putInt(REV, next)
            .apply()
        return next
    }

    fun revision(): Int = prefs.getInt(REV, 0)

    /**
     * Notifies when anything writes state — the SMS receiver runs on its own
     * thread while the app may be open, and without this the ViewModel's older
     * in-memory copy overwrites the import on its next save.
     *
     * Keep a reference to the returned listener: preferences hold it weakly.
     */
    fun observe(onChanged: () -> Unit): SharedPreferences.OnSharedPreferenceChangeListener {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == REV) onChanged()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        return listener
    }

    fun stopObserving(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    private companion object {
        // v2: account balances became opening balances, confirmations became txns.
        const val KEY = "state_v2"
        const val REV = "state_rev"
    }
}
