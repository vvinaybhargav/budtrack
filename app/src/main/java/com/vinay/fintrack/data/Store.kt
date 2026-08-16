package com.vinay.fintrack.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
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
    /** Carry last month's leftover into this month's budget. Off by default:
     *  it changes what every bar means, so it should be a deliberate choice. */
    val budgetRollover: Boolean = false,
    /** Joint isn't one of these — it's a view you switch to on Home, not
     *  something you sign in to. */
    val profiles: Map<String, String> = mapOf("Me" to "1234", "Wife" to "1234"),
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
    /** Day of the month a new cycle starts, for pay that does not arrive on the
     *  1st. Set-asides and confirmations follow it. */
    val cycleResetDay: Int = 1,
    /**
     * Salary day per profile, since two people are rarely paid on the same
     * date. Synced, so it follows the person rather than the phone; falls back
     * to [cycleResetDay] for anyone without one set.
     */
    val salaryDays: Map<String, Int> = emptyMap(),
    val salaries: Map<String, Double> = emptyMap(),
    val smsImportOn: Boolean = false,
    /** Whether SMS access has ever been requested. Android only reveals that a
     *  permission is permanently denied after a first attempt, so without
     *  remembering this the app cannot tell "not asked yet" from "declined for
     *  good" — and offers a button that does nothing. */
    val smsAsked: Boolean = false,
    val smsLog: List<String> = emptyList(),
    /** The message each imported transaction came from, keyed by its id.
     *  Device-local and never synced: bank texts carry account numbers and
     *  balances, and this exists so you can check what was read, not so it can
     *  travel. Capped, oldest dropped first. */
    val smsBodies: Map<String, String> = emptyMap(),
    /**
     * The chat, per profile.
     *
     * Device-local and never synced: it is your own working conversation, and
     * it quotes balances and payees that have no business in a shared
     * document. Kept so closing the app does not wipe it.
     */
    val chats: Map<String, List<ChatMessage>> = emptyMap(),
    /**
     * What each payee was filed under, learned from your corrections.
     *
     * Synced, because it is knowledge about the household rather than about
     * this phone: teaching it once should settle it on both.
     */
    val payeeCategories: Map<String, String> = emptyMap(),
    val smsSuggestions: Map<String, String> = emptyMap(),
    val smsRules: Map<String, String> = emptyMap(),
    val salaryOverrides: Map<String, SalaryOverride> = emptyMap()
)

@Serializable
data class SalaryOverride(
    val amount: Double,
    val resetDay: Int
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

    /**
     * Snapshots to undo back to, newest last, at most [MAX_UNDO].
     *
     * Its own key rather than a field on the state: undo history is this
     * device's, it must not sync, and it must not be part of what a snapshot
     * itself contains.
     */
    fun saveUndo(states: List<PersistedState>) {
        val kept = states.takeLast(MAX_UNDO)
        prefs.edit()
            .putString(UNDO, json.encodeToString(ListSerializer(PersistedState.serializer()), kept))
            .apply()
    }

    fun loadUndo(): List<PersistedState> {
        val raw = prefs.getString(UNDO, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(PersistedState.serializer()), raw)
        }.getOrElse { emptyList() }
    }

    private companion object {
        // v2: account balances became opening balances, confirmations became txns.
        const val KEY = "state_v2"
        const val REV = "state_rev"
        const val UNDO = "state_undo"

        // Three steps back. Enough to catch a mistake you noticed a message or
        // two later, without storing the whole app several times over.
        const val MAX_UNDO = 3
    }
}
