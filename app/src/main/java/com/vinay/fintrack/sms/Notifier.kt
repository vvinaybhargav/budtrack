package com.vinay.fintrack.sms

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.vinay.fintrack.MainActivity
import com.vinay.fintrack.R
import com.vinay.fintrack.data.Txn
import com.vinay.fintrack.data.inr

/**
 * Announces every transaction the app records, wherever it came from.
 *
 * A bank message is read with the app closed, so without this the first you'd
 * know of a wrongly-filed payment is next time you opened the app and scrolled.
 * The notification opens that exact transaction for editing, so a wrong account,
 * category or description is a tap away from being fixed while you still
 * remember the payment.
 */
object Notifier {

    /** Read by MainActivity to know which transaction to open. */
    const val EXTRA_TXN_ID = "com.vinay.fintrack.OPEN_TXN"

    private const val CHANNEL_ID = "imports"
    private const val DUE_CHANNEL_ID = "due"

    /** Keeps reminder ids clear of the transaction ones, so a bill coming up
     *  never replaces the notification for a payment just recorded. */
    private const val DUE_BASE = 900_000

    // canNotify() below is the check; lint can't see through it.
    @SuppressLint("MissingPermission")
    fun notifyRecorded(context: Context, txn: Txn) {
        if (!canNotify(context)) return
        ensureChannel(context)

        val open = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_TXN_ID, txn.id)
        }
        // The transaction id as the request code: two imports arriving together
        // must not hand each other's PendingIntent back, or both notifications
        // would open the same transaction.
        val pending = PendingIntent.getActivity(
            context,
            txn.id.hashCode(),
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val direction = when (txn.kind) {
            "TRANSFER" -> "Moved"
            "INCOME" -> "Received"
            else -> "Paid"
        }
        val who = txn.note.ifEmpty { txn.category }

        val note = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle("$direction ${inr(txn.amount)} · $who")
            .setContentText("Tap to change the account, category or note")
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail(txn)))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(txn.id.hashCode(), note)
        }
    }

    /**
     * A bill coming up. Opens the app rather than a single row, since what you
     * do next is confirm it on Home.
     */
    @SuppressLint("MissingPermission")
    fun notifyDue(context: Context, title: String, body: String, id: String) {
        if (!canNotify(context)) return
        ensureChannel(context)
        val open = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context, id.hashCode(), open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val note = NotificationCompat.Builder(context, DUE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(DUE_BASE + id.hashCode(), note)
        }
    }

    private fun detail(txn: Txn): String =
        "${txn.category} · ${txn.whenText}\nTap to change the account, category or note."

    /**
     * Android 13 asks for notifications like any other permission, and posting
     * without it throws. Older versions have it from the manifest.
     */
    private fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        // Default importance, not high, and silent: an imported one arrives
        // alongside the bank's own alert, and two things buzzing for one
        // payment is a reason to turn both off.
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID, "Recorded payments", NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "When a transaction is recorded, so you can check it"
                enableVibration(false)
            }
        )
        // Its own channel, so reminders can be silenced without losing the
        // record of what was imported, or the other way round.
        manager.createNotificationChannel(
            NotificationChannel(
                DUE_CHANNEL_ID, "Bills coming up", NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "A few days before a bill, EMI or set-aside is due"
            }
        )
    }
}
