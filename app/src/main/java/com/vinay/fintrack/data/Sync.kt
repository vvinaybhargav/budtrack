package com.vinay.fintrack.data

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/** What the UI shows about the connection. */
enum class SyncStatus { OFF, CONNECTING, LIVE, ERROR }

/**
 * Two-way sync of the whole [PersistedState] against a single Firestore
 * document, so both profiles on both phones see the same household.
 *
 * Deliberately last-write-wins: this is a two-person household, edits rarely
 * collide, and a merge strategy nobody asked for would be harder to trust than
 * one that is easy to explain.
 */
class FirestoreSync(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private var app: FirebaseApp? = null
    private var db: FirebaseFirestore? = null
    private var listener: ListenerRegistration? = null

    var status: SyncStatus = SyncStatus.OFF
        private set
    var lastError: String = ""
        private set

    /** Epoch millis of the last successful write, 0 if none this session. */
    var lastPushedAt: Long = 0L
        private set

    /** Set by the ViewModel so a status change can drive recomposition. */
    var onStatusChange: ((SyncStatus, String) -> Unit)? = null

    private fun report(next: SyncStatus, error: String = "") {
        status = next
        lastError = error
        onStatusChange?.invoke(next, error)
    }

    /** Set while applying a remote snapshot, so the resulting local save
     *  doesn't bounce straight back up as a new write. */
    private var applyingRemote = false

    /**
     * (Re)connect using the config text from Settings. Safe to call repeatedly —
     * it tears down any previous connection first.
     *
     * @param onRemote invoked on the main thread whenever the household document
     *   changes, including the first read.
     * @param onEmptyRemote invoked when the document doesn't exist yet, so the
     *   caller can seed it from this device.
     */
    fun connect(
        configText: String,
        onRemote: (PersistedState) -> Unit,
        onEmptyRemote: () -> Unit
    ) {
        disconnect()

        val cfg = parseFirebaseConfig(configText)
        if (cfg == null) {
            if (configText.isBlank()) report(SyncStatus.OFF)
            else report(
                SyncStatus.ERROR,
                "Need 6 comma-separated values: apiKey, authDomain, projectId, " +
                    "storageBucket, messagingSenderId, appId"
            )
            return
        }

        report(SyncStatus.CONNECTING)

        val options = FirebaseOptions.Builder()
            .setApiKey(cfg.apiKey)
            .setApplicationId(cfg.appId)
            .setProjectId(cfg.projectId)
            .setStorageBucket(cfg.storageBucket)
            .setGcmSenderId(cfg.messagingSenderId)
            .build()

        val instance = runCatching {
            // A named app avoids clashing with any default one, and lets a
            // changed config replace the old connection cleanly.
            FirebaseApp.getApps(context).firstOrNull { it.name == APP_NAME }?.delete()
            FirebaseApp.initializeApp(context, options, APP_NAME)
        }.getOrElse { e ->
            report(SyncStatus.ERROR, e.message ?: "Could not start Firebase")
            Log.w(TAG, "initializeApp failed", e)
            return
        }

        app = instance
        db = FirebaseFirestore.getInstance(instance)

        listener = db!!.collection(COLLECTION).document(DOCUMENT)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    report(SyncStatus.ERROR, error.message ?: "Firestore listen failed")
                    Log.w(TAG, "listen failed", error)
                    return@addSnapshotListener
                }
                report(SyncStatus.LIVE)
                // First connect against a fresh project: the document doesn't exist
                // yet, so seed it from this device instead of sitting there empty.
                val data = snapshot?.data
                if (snapshot == null || !snapshot.exists() || data == null) {
                    onEmptyRemote()
                    return@addSnapshotListener
                }
                val state = runCatching { decodeState(data) }.getOrElse { e ->
                    report(SyncStatus.ERROR, "Remote data didn't match this app's format")
                    Log.w(TAG, "could not decode remote state", e); return@addSnapshotListener
                }
                applyingRemote = true
                try {
                    onRemote(state)
                } finally {
                    applyingRemote = false
                }
            }
    }

    fun disconnect() {
        listener?.remove()
        listener = null
        db = null
        runCatching { app?.delete() }
        app = null
        report(SyncStatus.OFF)
    }

    /** Push local state up. No-op while a remote snapshot is being applied. */
    fun push(state: PersistedState, byProfile: String) {
        if (applyingRemote) return
        val target = db ?: return
        val payload = encodeState(state).toMutableMap()
        payload["updatedAt"] = System.currentTimeMillis()
        payload["updatedBy"] = byProfile
        target.collection(COLLECTION).document(DOCUMENT)
            .set(payload, SetOptions.merge())
            .addOnSuccessListener {
                lastPushedAt = System.currentTimeMillis()
                report(SyncStatus.LIVE)
            }
            .addOnFailureListener { e ->
                report(SyncStatus.ERROR, e.message ?: "Write failed")
                Log.w(TAG, "push failed", e)
            }
    }

    // ── state ⇄ Firestore ──────────────────────────────────────────────
    // Stored as real nested maps and arrays rather than one JSON blob, so the
    // data is readable and editable in the Firestore console.

    private fun encodeState(state: PersistedState): Map<String, Any?> {
        val tree = json.encodeToJsonElement(PersistedState.serializer(), state)
        @Suppress("UNCHECKED_CAST")
        return toFirestore(tree) as Map<String, Any?>
    }

    private fun decodeState(data: Map<String, Any?>): PersistedState {
        val clean = data.filterKeys { it != "updatedAt" && it != "updatedBy" }
        return json.decodeFromJsonElement(PersistedState.serializer(), toJson(clean))
    }

    private fun toFirestore(el: JsonElement): Any? = when (el) {
        is JsonNull -> null
        is JsonObject -> el.mapValues { (_, v) -> toFirestore(v) }
        is JsonArray -> el.map { toFirestore(it) }
        is JsonPrimitive ->
            if (el.isString) el.content
            // longOrNull first: a sequence counter must not come back as 16.0
            // and fail to decode into an Int.
            else el.booleanOrNull ?: el.longOrNull ?: el.doubleOrNull ?: el.content
    }

    private fun toJson(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is String -> JsonPrimitive(value)
        is Map<*, *> -> JsonObject(value.entries.associate { (k, v) -> k.toString() to toJson(v) })
        is Iterable<*> -> JsonArray(value.map { toJson(it) })
        else -> JsonPrimitive(value.toString())
    }

    private companion object {
        const val TAG = "FinTrackSync"
        const val APP_NAME = "fintrack-sync"
        const val COLLECTION = "fintrack"
        const val DOCUMENT = "household"
    }
}
