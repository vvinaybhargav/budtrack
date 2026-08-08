package com.vinay.fintrack.data

/**
 * The six values Firestore needs, pasted into a single Settings field as a
 * comma-separated list in this order:
 *
 *     apiKey, authDomain, projectId, storageBucket, messagingSenderId, appId
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

fun parseFirebaseConfig(text: String): FirebaseConfig? {
    if (text.isBlank()) return null

    val parts = text
        .replace("{", "")
        .replace("}", "")
        .split(",")
        .map { piece ->
            stripKeyPrefix(piece.trim())
                .trim()
                .trim('"', '\'', ';')
                .trim()
        }
        .filter { it.isNotEmpty() }

    if (parts.size < 6) return null

    return FirebaseConfig(
        apiKey = parts[0],
        authDomain = parts[1],
        projectId = parts[2],
        storageBucket = parts[3],
        messagingSenderId = parts[4],
        appId = parts[5]
    ).takeIf { it.isUsable }
}
