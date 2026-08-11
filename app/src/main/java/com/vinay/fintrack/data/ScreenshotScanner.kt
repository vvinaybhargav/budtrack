package com.vinay.fintrack.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/** A screenshot that read as a UPI receipt. */
data class ScreenshotHit(
    val uri: Uri,
    val takenAt: Long,
    val parsed: ParsedUpi
)

/**
 * Finds PhonePe receipts in the device's screenshots and reads them with
 * on-device OCR. No network, no API key — ML Kit ships the model with the app.
 */
class ScreenshotScanner(private val context: Context) {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * @param since only look at screenshots taken after this epoch-millis,
     *   which keeps a periodic scan cheap.
     * @param skipRefs references already imported, so a rescan can't duplicate.
     */
    fun scan(since: Long, skipRefs: Set<String>, limit: Int = 60): List<ScreenshotHit> {
        val hits = mutableListOf<ScreenshotHit>()
        for ((uri, takenAt) in recentScreenshots(since, limit)) {
            val text = runCatching { readText(uri) }.getOrElse { e ->
                Log.w(TAG, "OCR failed for $uri", e); ""
            }
            if (text.isEmpty()) continue
            val parsed = parseUpiScreenshot(text) ?: continue
            if (parsed.ref in skipRefs) continue
            if (hits.any { it.parsed.ref == parsed.ref }) continue
            hits += ScreenshotHit(uri, takenAt, parsed)
        }
        return hits
    }

    /** Newest first, restricted to the Screenshots bucket. */
    private fun recentScreenshots(since: Long, limit: Int): List<Pair<Uri, Long>> {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val projection = buildList {
            add(MediaStore.Images.Media._ID)
            add(MediaStore.Images.Media.DATE_ADDED)
            add(MediaStore.Images.Media.DISPLAY_NAME)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Images.Media.RELATIVE_PATH)
            }
        }.toTypedArray()
        // DATE_ADDED is in seconds, unlike almost every other timestamp here.
        val selection = "${MediaStore.Images.Media.DATE_ADDED} > ?"
        val args = arrayOf((since / 1000).toString())
        val order = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        val out = mutableListOf<Pair<Uri, Long>>()
        context.contentResolver.query(collection, projection, selection, args, order)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dateCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            while (c.moveToNext() && out.size < limit) {
                val name = c.getString(nameCol).orEmpty()
                if (!isScreenshot(name, c)) continue
                out += ContentUris.withAppendedId(collection, c.getLong(idCol)) to
                    c.getLong(dateCol) * 1000
            }
        }
        return out
    }

    /** RELATIVE_PATH only exists on Q+; below that fall back to the filename. */
    private fun isScreenshot(name: String, c: android.database.Cursor): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val idx = c.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
            if (idx >= 0) {
                val path = c.getString(idx).orEmpty()
                if (path.isNotEmpty()) return path.contains("screenshot", true)
            }
        }
        return name.contains("screenshot", true) || name.startsWith("Screenshot_")
    }

    private fun readText(uri: Uri): String {
        val image = InputImage.fromFilePath(context, uri)
        // Blocking is fine and wanted here: this runs on a worker thread.
        return Tasks.await(recognizer.process(image)).text
    }

    private companion object {
        const val TAG = "ScreenshotScanner"
    }
}
