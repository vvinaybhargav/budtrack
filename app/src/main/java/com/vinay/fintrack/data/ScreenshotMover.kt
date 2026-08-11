package com.vinay.fintrack.data

import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log

/**
 * Moves imported receipts into Pictures/PhonePe.
 *
 * Android 11 and up will not let an app relocate images it did not create
 * without the user agreeing, so this asks once for the whole batch through
 * [MediaStore.createWriteRequest] rather than per file. That prompt is
 * unavoidable — there is no silent path for other apps' media.
 */
class ScreenshotMover(private val context: Context) {

    /**
     * Writes a copy into Pictures/PhonePe. This needs no permission dialog at
     * all: the copy is a file this app creates, and an app owns what it
     * creates. The original stays in Screenshots unless you delete it.
     *
     * @return true if the copy landed.
     */
    fun copyToPhonePe(source: Uri): Boolean {
        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val name = "phonepe_${System.currentTimeMillis()}.png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, TARGET)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        return runCatching {
            val dest = resolver.insert(collection, values) ?: return false
            resolver.openInputStream(source)?.use { input ->
                resolver.openOutputStream(dest)?.use { output -> input.copyTo(output) }
            } ?: return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.update(
                    dest,
                    ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                    null, null
                )
            }
            true
        }.getOrElse { Log.w(TAG, "copy failed for $source", it); false }
    }

    /**
     * Consent prompt for deleting the originals out of Screenshots, once the
     * copies are filed. Android grants this per batch, and performs the delete
     * itself when the user agrees — nothing more to do afterwards.
     *
     * Null below Android 11, where [deleteDirectly] works without a prompt.
     */
    fun deleteRequest(uris: List<Uri>): IntentSender? {
        if (uris.isEmpty()) return null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return runCatching {
            MediaStore.createDeleteRequest(context.contentResolver, uris).intentSender
        }.getOrElse { Log.w(TAG, "createDeleteRequest failed", it); null }
    }

    /** Pre-Android-11 path, where a plain delete is permitted. */
    fun deleteDirectly(uris: List<Uri>): Int {
        var gone = 0
        for (uri in uris) {
            runCatching { context.contentResolver.delete(uri, null, null) }
                .onSuccess { if (it > 0) gone++ }
                .onFailure { Log.w(TAG, "delete failed for $uri", it) }
        }
        return gone
    }

    private companion object {
        const val TAG = "ScreenshotMover"
        const val TARGET = "Pictures/PhonePe/"
    }
}
