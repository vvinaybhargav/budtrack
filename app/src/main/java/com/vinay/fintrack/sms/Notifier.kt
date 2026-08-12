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
 * Tells you what a bank message just became.
 *
 * The import happens with the app closed, so without this the first you'd know
 * of a wrongly-filed payment is next time you opened the app and scrolled. The
 * notification opens that exact transaction for editing, so a wrong account or
 * category is a tap away from being fixed while you still remember the payment.
 */
object Notifier {

    /** Read by MainActivity to know which transaction to open. */
    const val EXTRA_TXN_ID = "com.vinay.fintrack.OPEN_TXN"

    private const val CHANNEL_ID = "imports"

    // canNotify() below is the check; lint can't see through it.
    @SuppressLint("MissingPermission")
    fun notifyImported(context: Context, txn: Txn, isNew: Boolean) {
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

        val direction = when {
            !isNew -> "Transfer"
            txn.kind == "INCOME" -> "Received"
            else -> "Paid"
        }
        val who = txn.note.ifEmpty { txn.category }

        val note = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle("$direction ${inr(txn.amount)} · $who")
            .setContentText("Tap to check the account and category")
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail(txn)))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(txn.id.hashCode(), note)
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
        // Default importance, not high: these arrive alongside the bank's own
        // alert, and two things buzzing for one payment is a reason to turn
        // both off.
        val channel = NotificationChannel(
            CHANNEL_ID, "Imported payments", NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "When a bank message is recorded as a transaction"
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }
}
