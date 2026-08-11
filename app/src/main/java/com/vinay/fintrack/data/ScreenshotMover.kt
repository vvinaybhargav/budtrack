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

    /** The consent prompt to launch, or null if none is needed. */
    fun consentRequest(uris: List<Uri>): IntentSender? {
        if (uris.isEmpty()) return null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return runCatching {
            MediaStore.createWriteRequest(context.contentResolver, uris).intentSender
        }.getOrElse { Log.w(TAG, "createWriteRequest failed", it); null }
    }

    /**
     * Retags each image into the PhonePe folder. Call after consent is granted
     * — before that, every update throws a security exception.
     *
     * @return how many actually moved.
     */
    fun move(uris: List<Uri>): Int {
        var moved = 0
        for (uri in uris) {
            val values = ContentValues().apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, TARGET)
                }
            }
            if (values.size() == 0) continue
            runCatching { context.contentResolver.update(uri, values, null, null) }
                .onSuccess { if (it > 0) moved++ }
                .onFailure { Log.w(TAG, "move failed for $uri", it) }
        }
        return moved
    }

    private companion object {
        const val TAG = "ScreenshotMover"
        const val TARGET = "Pictures/PhonePe/"
    }
}
