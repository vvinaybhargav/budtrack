package com.vinay.fintrack.data

/**
 * The Firestore connection details, pasted into a single Settings field as a
 * comma-separated list:
 *
 *     apiKey, projectId, storageBucket, messagingSenderId, appId
 *
 * authDomain may be included but is ignored — it exists only for the Auth web
 * SDK. Order is not significant; see [parseFirebaseConfig].
 *
 * Nothing here is ever committed — the config lives only in the app's own
 * storage on the device it was typed into.
 */
data class FirebaseConfig(
    val apiKey: String,
    val authDomain: String,
    val projectId: String,
    val storageBucket: String,
    val messagingSenderId: String,
    val appId: String
) {
    /** appId and apiKey are what FirebaseOptions actually refuses to go without. */
    val isUsable: Boolean get() = apiKey.isNotBlank() && appId.isNotBlank() && projectId.isNotBlank()
}

/**
 * Lenient on purpose: accepts a bare comma-separated list, and also tolerates a
 * config block pasted straight from the Firebase console — `apiKey: "…",` and
 * friends — by dropping any `key:` prefix, quotes, and braces.
 */
private val CONFIG_KEYS = setOf(
    "apikey", "authdomain", "projectid", "storagebucket",
    "messagingsenderid", "appid", "measurementid", "databaseurl"
)

/** Strips a leading `apiKey:` only when the prefix really is a config key —
 *  appId is itself full of colons (`1:123…:web:abc`) and must survive intact. */
private fun stripKeyPrefix(piece: String): String {
    val colon = piece.indexOf(':')
    if (colon <= 0) return piece
    val prefix = piece.take(colon).trim().trim('"', '\'').lowercase()
    return if (prefix in CONFIG_KEYS) piece.substring(colon + 1) else piece
}

/**
 * Values are recognised by shape rather than position, so the order doesn't
 * matter and optional ones can simply be left out. Only apiKey, projectId and
 * appId are actually required — authDomain belongs to the Auth web SDK and has
 * no Android equivalent, while the rest can be derived from the project id.
 */
fun parseFirebaseConfig(text: String): FirebaseConfig? {
    if (text.isBlank()) return null

    var apiKey = ""
    var authDomain = ""
    var projectId = ""
    var storageBucket = ""
    var senderId = ""
    var appId = ""

    text.replace("{", "").replace("}", "").split(",").forEach { raw ->
        val piece = raw.trim()
        if (piece.isEmpty()) return@forEach

        // An explicit `apiKey: "…"` is the most reliable signal — trust it first.
        val named = namedKeyOf(piece)
        val value = stripKeyPrefix(piece).trim().trim('"', '\'', ';').trim()
        if (value.isEmpty()) return@forEach

        when {
            named == "apikey" -> apiKey = value
            named == "authdomain" -> authDomain = value
            named == "projectid" -> projectId = value
            named == "storagebucket" -> storageBucket = value
            named == "messagingsenderid" -> senderId = value
            named == "appid" -> appId = value

            // Otherwise fall back to what the value looks like.
            value.startsWith("AIza") -> apiKey = value
            value.count { it == ':' } >= 2 -> appId = value
            value.all { it.isDigit() } -> senderId = value
            value.endsWith(".firebasestorage.app") || value.contains(".appspot.com") ->
                storageBucket = value
            value.endsWith(".firebaseapp.com") -> authDomain = value
            projectId.isEmpty() -> projectId = value
        }
    }

    if (apiKey.isBlank() || appId.isBlank()) return null
    // `1:1091860164856:web:abc` — the middle segment is the sender id.
    if (projectId.isBlank()) projectId = storageBucket.substringBefore('.')
    if (senderId.isBlank()) senderId = appId.split(":").getOrElse(1) { "" }
    if (storageBucket.isBlank()) storageBucket = "$projectId.appspot.com"
    if (authDomain.isBlank()) authDomain = "$projectId.firebaseapp.com"

    return FirebaseConfig(apiKey, authDomain, projectId, storageBucket, senderId, appId)
        .takeIf { it.isUsable }
}

/** The config key this piece names, or null when it's a bare value. */
private fun namedKeyOf(piece: String): String? {
    val colon = piece.indexOf(':')
    if (colon <= 0) return null
    val prefix = piece.take(colon).trim().trim('"', '\'').lowercase()
    return prefix.takeIf { it in CONFIG_KEYS }
}
