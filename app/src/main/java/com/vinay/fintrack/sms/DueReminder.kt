package com.vinay.fintrack.sms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.vinay.fintrack.data.Ledger
import com.vinay.fintrack.data.Store
import com.vinay.fintrack.data.inr
import com.vinay.fintrack.data.prettyDate
import com.vinay.fintrack.data.today
import java.util.Calendar

/**
 * Reminds you of a bill before it lands.
 *
 * Due dates were recorded and then never mentioned again — the app knew the
 * premium was due on the 29th and said nothing. A daily check is enough: these
 * are dates weeks apart, not appointments.
 *
 * Deliberately an inexact alarm. The exact ones need a special permission on
 * Android 12 and up, and being reminded at nine or at half past makes no
 * difference to a bill due next week.
 */
object DueReminder {

    /**
     * How far ahead to warn: the day before, and again on the day itself.
     *
     * Three days meant four notifications for one bill, which is how a reminder
     * becomes something you swipe away without reading. One night's warning is
     * enough to move money.
     */
    private const val DAYS_AHEAD = 1

    private const val REQUEST = 4711

    fun schedule(context: Context) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_MONTH, 1)
        }
        runCatching {
            manager.setInexactRepeating(
                AlarmManager.RTC,
                next.timeInMillis,
                AlarmManager.INTERVAL_DAY,
                pendingIntent(context)
            )
        }.onFailure { Log.w(TAG, "could not schedule reminders", it) }
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST,
            Intent(context, DueReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /**
     * Posts a reminder for anything due within [DAYS_AHEAD] days that hasn't
     * been confirmed for its cycle yet.
     *
     * Already-confirmed things are skipped: being told to pay what you have
     * paid is how people stop reading notifications.
     */
    fun check(context: Context) {
        val state = Store(context).load()
        val now = today()

        for (e in state.entries) {
            if (e.closed || e.nextDue.isEmpty()) continue
            val days = Ledger.daysBetween(now, e.nextDue)
            if (days < 0 || days > DAYS_AHEAD) continue
            val cycle = Ledger.cycleOf(now, state.salaryDays[e.person] ?: state.cycleResetDay)
            val paid = state.txns.any { it.entryId == e.id && it.month == cycle }
            if (paid) continue

            val what = e.note.ifEmpty { e.category }
            // A set-aside says what is in the pot, because the useful question
            // on the day is whether there is enough to pay it.
            if (e.isSetAside) {
                val pot = Ledger.setAsidePot(state.txns, e.id)
                Notifier.notifyDue(
                    context,
                    "$what due ${whenWord(days)} · ${inr(e.amount)}",
                    "${inr(pot)} set aside so far" +
                        (if (pot >= e.amount - 0.5) " — enough to pay it."
                        else ", ${inr(e.amount - pot)} short.") +
                        " Due ${prettyDate(e.nextDue)}.",
                    e.id
                )
            } else {
                Notifier.notifyDue(
                    context,
                    "$what due ${whenWord(days)} · ${inr(e.monthly)}",
                    "Due ${prettyDate(e.nextDue)}. Confirm it on Home once it has gone.",
                    e.id
                )
            }
        }

        for (l in state.loans) {
            if (l.remainingMonths <= 0 || l.nextDue.isEmpty()) continue
            val days = Ledger.daysBetween(now, l.nextDue)
            if (days < 0 || days > DAYS_AHEAD) continue
            val cycle = Ledger.cycleOf(now, state.salaryDays[l.person] ?: state.cycleResetDay)
            if (state.txns.any { it.loanId == l.id && it.month == cycle }) continue
            Notifier.notifyDue(
                context,
                "${l.name} EMI ${whenWord(days)} · ${inr(l.monthlyEmi)}",
                "Due ${prettyDate(l.nextDue)}. ${l.remainingMonths} of " +
                    "${l.totalMonths} months left.",
                l.id
            )
        }
    }

    private fun whenWord(days: Int): String = when (days) {
        0 -> "today"
        1 -> "tomorrow"
        else -> "in $days days"
    }

    private const val TAG = "DueReminder"
}

/** Fires daily, and again after a restart — alarms don't survive a reboot. */
class DueReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            DueReminder.schedule(app)
            return
        }
        // Reading state touches disk, so it happens off the main thread; the
        // receiver is kept alive until it finishes.
        val pending = goAsync()
        Thread {
            try {
                runCatching { DueReminder.check(app) }
                    .onFailure { Log.w("DueReceiver", "reminder check failed", it) }
            } finally {
                pending.finish()
            }
        }.start()
    }
}
