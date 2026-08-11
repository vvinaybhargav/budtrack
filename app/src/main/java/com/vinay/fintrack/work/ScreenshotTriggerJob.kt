package com.vinay.fintrack.work

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.provider.MediaStore
import android.util.Log
import com.vinay.fintrack.data.ScreenshotImporter
import com.vinay.fintrack.data.Store

/**
 * Wakes as soon as a new image appears, so a screenshot taken with the phone's
 * own multi-finger gesture becomes a transaction within a second or two.
 *
 * Content-triggered jobs are the API built for this: no foreground service and
 * no permanent notification, unlike watching MediaStore ourselves. They fire
 * once and must re-arm, which [onStartJob] does before finishing.
 */
class ScreenshotTriggerJob : JobService() {

    override fun onStartJob(params: JobParameters): Boolean {
        Thread {
            runCatching {
                if (Store(applicationContext).load().screenshotImportOn &&
                    ScreenshotWorker.hasMediaPermission(applicationContext)
                ) {
                    ScreenshotImporter(applicationContext).run()
                }
            }.onFailure { Log.w(TAG, "trigger import failed", it) }

            schedule(applicationContext)   // one-shot: arm the next one
            jobFinished(params, false)
        }.start()
        return true                        // work continues on that thread
    }

    override fun onStopJob(params: JobParameters): Boolean = true

    companion object {
        private const val TAG = "ScreenshotTrigger"
        private const val JOB_ID = 4201

        fun schedule(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
            val job = JobInfo.Builder(
                JOB_ID, ComponentName(context, ScreenshotTriggerJob::class.java)
            )
                .addTriggerContentUri(
                    JobInfo.TriggerContentUri(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS
                    )
                )
                // Fire quickly rather than batching up changes for later.
                .setTriggerContentUpdateDelay(500)
                .setTriggerContentMaxDelay(3_000)
                .build()
            runCatching { scheduler.schedule(job) }
                .onFailure { Log.w(TAG, "could not schedule trigger job", it) }
        }

        fun cancel(context: Context) {
            context.getSystemService(JobScheduler::class.java)?.cancel(JOB_ID)
        }
    }
}
