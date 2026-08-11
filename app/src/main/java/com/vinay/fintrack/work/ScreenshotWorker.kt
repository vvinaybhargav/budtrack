package com.vinay.fintrack.work

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.vinay.fintrack.data.ScreenshotImporter
import com.vinay.fintrack.data.Store
import java.util.concurrent.TimeUnit

/**
 * Periodic scan for new PhonePe receipts. Fifteen minutes is the floor
 * WorkManager allows, and the OS may still delay a run while the phone is in
 * Doze — so this catches up in batches rather than firing the instant a
 * screenshot is taken.
 */
class ScreenshotWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        if (!hasMediaPermission(applicationContext)) return Result.success()
        if (!Store(applicationContext).load().screenshotImportOn) return Result.success()
        return runCatching { ScreenshotImporter(applicationContext).run() }
            .fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
    }

    companion object {
        private const val NAME = "screenshot-import"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ScreenshotWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder().setRequiresBatteryNotLow(true).build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }

        fun hasMediaPermission(context: Context): Boolean {
            val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_IMAGES
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
            return ContextCompat.checkSelfPermission(context, perm) ==
                PackageManager.PERMISSION_GRANTED
        }
    }
}
