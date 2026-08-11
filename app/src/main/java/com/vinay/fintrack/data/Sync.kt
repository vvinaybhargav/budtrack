package com.vinay.fintrack.data

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
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
    private var auth: FirebaseAuth? = null
    private var listener: ListenerRegistration? = null
    private var txnListener: ListenerRegistration? = null

    /** Set by the ViewModel; receives the whole transaction collection. */
    var onTxns: ((List<Txn>) -> Unit)? = null

    /** The collection doesn't exist yet — the caller should seed it. */
    var onTxnsMissing: (() -> Unit)? = null

    private var txnsSeeded = false

    /** This device's anonymous user id — paste it into the members doc to
     *  authorise the device without opening the database to everyone.
     *  Empty when signed-in anonymously wasn't possible, which is fine for
     *  rules that authorise by path instead. */
    var uid: String = ""
        private set

    /** Why sign-in didn't happen, if it didn't. Not an error on its own. */
    var authNote: String = ""
        private set

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
                "Couldn't read the config. Needs at least apiKey, projectId and appId, " +
                    "comma-separated."
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

        // Best-effort sign-in: rules that require auth will then work, but rules
        // that don't (the household workspace is open by path) must not be
        // blocked just because the Anonymous provider is switched off.
        val authInstance = runCatching { FirebaseAuth.getInstance(instance) }.getOrNull()
        auth = authInstance
        val existing = authInstance?.currentUser
        when {
            authInstance == null -> attachListener(onRemote, onEmptyRemote)
            existing != null -> {
                uid = existing.uid
                attachListener(onRemote, onEmptyRemote)
            }
            else -> authInstance.signInAnonymously()
                .addOnSuccessListener { result ->
                    uid = result.user?.uid.orEmpty()
                    attachListener(onRemote, onEmptyRemote)
                }
                .addOnFailureListener { e ->
                    // Carry on unauthenticated — Firestore will say if the rules
                    // actually needed a user, and that error is the useful one.
                    authNote = authHint(e)
                    Log.w(TAG, "anonymous sign-in unavailable, continuing without it", e)
                    attachListener(onRemote, onEmptyRemote)
                }
        }
    }

    /** Anonymous sign-in is off by default in a new project, and the raw error
     *  for that is not something anyone should have to decode. */
    private fun authHint(e: Exception): String {
        val msg = e.message.orEmpty()
        return when {
            msg.contains("CONFIGURATION_NOT_FOUND", true) ||
                msg.contains("ADMIN_ONLY_OPERATION", true) ||
                msg.contains("OPERATION_NOT_ALLOWED", true) ->
                "Turn on Anonymous sign-in: Firebase console → Authentication → " +
                    "Sign-in method → Anonymous → Enable."
            msg.contains("API key not valid", true) ->
                "That apiKey isn't valid for this project."
            else -> msg.ifEmpty { "Sign-in failed" }
        }
    }

    private fun attachListener(
        onRemote: (PersistedState) -> Unit,
        onEmptyRemote: () -> Unit
    ) {
        txnListener = txnsCollection(db!!).addSnapshotListener { snap, error ->
            if (error != null) {
                report(SyncStatus.ERROR, error.message ?: "Transaction listen failed")
                Log.w(TAG, "txn listen failed", error); return@addSnapshotListener
            }
            val list = snap?.documents.orEmpty().mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                runCatching {
                    json.decodeFromJsonElement(Txn.serializer(), toJson(data))
                }.getOrElse { Log.w(TAG, "bad txn ${doc.id}", it); null }
            }
            // The very first snapshot being empty means the collection isn't
            // there yet — seed it from this device rather than letting it wipe
            // local history. A later empty snapshot is a real deletion.
            if (list.isEmpty() && !txnsSeeded) {
                txnsSeeded = true
                onTxnsMissing?.invoke()
                return@addSnapshotListener
            }
            txnsSeeded = true
            applyingRemote = true
            try {
                onTxns?.invoke(list.sortedBy { it.date + it.id })
            } finally {
                applyingRemote = false
            }
        }

        listener = stateDoc(db!!)
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
        txnListener?.remove()
        txnListener = null
        txnsSeeded = false
        db = null
        auth = null
        uid = ""
        authNote = ""
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
        stateDoc(target)
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

    private fun stateDoc(db: FirebaseFirestore) =
        db.collection(WORKSPACES).document(WORKSPACE_ID)
            .collection(APP_SECTION).document(STATE_DOC)

    /** One document per transaction, so two phones confirming different things
     *  don't overwrite each other the way a single state blob would. */
    private fun txnsCollection(db: FirebaseFirestore) = stateDoc(db).collection(TXNS)

    // No applyingRemote guard here, unlike the state document: these are only
    // ever called for a genuinely local change, never echoing a remote one, and
    // a silent no-op would lose an imported transaction.
    fun upsertTxn(t: Txn) {
        val target = db ?: return
        @Suppress("UNCHECKED_CAST")
        val map = toFirestore(json.encodeToJsonElement(Txn.serializer(), t)) as Map<String, Any?>
        txnsCollection(target).document(t.id).set(map)
            .addOnSuccessListener { lastPushedAt = System.currentTimeMillis() }
            .addOnFailureListener { e ->
                report(SyncStatus.ERROR, e.message ?: "Transaction write failed")
                Log.w(TAG, "upsertTxn failed", e)
            }
    }

    fun deleteTxn(id: String) {
        val target = db ?: return
        txnsCollection(target).document(id).delete()
            .addOnFailureListener { e -> Log.w(TAG, "deleteTxn failed", e) }
    }

    /** Send every transaction up — used when seeding a fresh document. */
    fun pushAllTxns(txns: List<Txn>) = txns.forEach { upsertTxn(it) }

    private companion object {
        const val TAG = "FinTrackSync"
        const val APP_NAME = "fintrack-sync"

        // Sits under workspaces/household/** so the existing household-finance
        // rules already cover it, in its own section so it cannot disturb that
        // app's entries / accounts / loans collections.
        const val WORKSPACES = "workspaces"
        const val WORKSPACE_ID = "household"
        const val APP_SECTION = "budtrack"
        const val STATE_DOC = "state"
        const val TXNS = "transactions"
    }
}
