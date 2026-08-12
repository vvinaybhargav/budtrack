package com.vinay.fintrack.sms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.vinay.fintrack.data.Ledger
import com.vinay.fintrack.data.Store
import com.vinay.fintrack.data.Txn
import com.vinay.fintrack.data.resolveNextDueDate
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
        val store = Store(context)
        var state = store.load()
        val now = today()
        var stateChanged = false
        val updatedLoans = state.loans.map { l ->
            if (l.remainingMonths <= 0 || l.dueDay <= 0) return@map l
            val currentMonth = now.take(7) // "yyyy-MM"
            
            val parts = now.split("-")
            val y = parts[0].toIntOrNull() ?: return@map l
            val m = parts[1].toIntOrNull() ?: return@map l
            
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.YEAR, y)
            calendar.set(Calendar.MONTH, m - 1)
            val maxDay = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
            val clampDay = minOf(l.dueDay, maxDay)
            val currentMonthDueDate = "%04d-%02d-%02d".format(y, m, clampDay)
            
            val days = Ledger.daysBetween(now, currentMonthDueDate)
            
            if (days == 1) {
                Notifier.notifyDue(
                    context,
                    "${l.name} due tomorrow",
                    "${l.name} due tomorrow. ₹${l.monthlyEmi.toLong()} will be paid automatically.",
                    l.id
                )
                l
            } else if (days == 0 && l.lastProcessedMonth != currentMonth) {
                val accountId = if (l.onCard) "" else {
                    state.accounts.firstOrNull { it.id == l.accountId }?.id
                        ?: state.accounts.firstOrNull { it.name == state.defaultAccount }?.id
                        ?: ""
                }
                
                val txnId = com.vinay.fintrack.data.newId("t")
                val newTxn = Txn(
                    id = txnId,
                    date = now,
                    kind = "EXPENSE",
                    amount = l.monthlyEmi,
                    category = "EMI",
                    fromAccountId = accountId,
                    cardId = l.cardId,
                    loanId = l.id,
                    period = Ledger.cycleOf(now, state.salaryDays[l.person] ?: state.cycleResetDay),
                    note = l.name,
                    at = System.currentTimeMillis()
                )
                
                val updatedRemaining = maxOf(0, l.remainingMonths - 1)
                val nextDueDate = resolveNextDueDate(l.dueDay, Ledger.addMonths(now, 1))
                
                if (l.onCard) {
                    state = state.copy(
                        cards = state.cards.map {
                            if (it.id == l.cardId) it.copy(balance = it.balance + l.monthlyEmi, paid = false)
                            else it
                        }
                    )
                }
                
                state = state.copy(txns = state.txns + newTxn)
                stateChanged = true
                
                Notifier.notifyDue(
                    context,
                    "Loan Auto-Paid",
                    "Auto-paid: ₹${l.monthlyEmi.toLong()} for ${l.name}.",
                    l.id
                )
                
                l.copy(
                    remainingMonths = updatedRemaining,
                    lastProcessedMonth = currentMonth,
                    dueDate = nextDueDate
                )
            } else {
                l
            }
        }

        val updatedCards = state.cards.map { c ->
            if (c.paid || c.balance <= 0.0 || c.nextDue.isEmpty()) return@map c
            val days = Ledger.daysBetween(now, c.nextDue)
            if (days == 1) {
                Notifier.notifyDue(
                    context,
                    "${c.name} bill tomorrow",
                    "${c.name} bill due tomorrow. ₹${c.balance.toLong()} will be paid automatically.",
                    c.id
                )
                c
            } else if (days == 0) {
                val fromAccId = state.accounts.firstOrNull { it.name == state.defaultAccount }?.id
                    ?: state.accounts.firstOrNull()?.id
                    ?: ""
                
                val txnId = com.vinay.fintrack.data.newId("t")
                val newTxn = Txn(
                    id = txnId,
                    date = now,
                    kind = "EXPENSE",
                    amount = c.balance,
                    category = "Credit Card Bill",
                    fromAccountId = fromAccId,
                    cardId = c.id,
                    period = Ledger.cycleOf(now, state.cycleResetDay),
                    note = "Settled ${c.name} Bill (Auto-Paid)",
                    at = System.currentTimeMillis()
                )
                
                state = state.copy(txns = state.txns + newTxn)
                stateChanged = true
                
                Notifier.notifyDue(
                    context,
                    "Card Bill Paid",
                    "Auto-settled: ₹${c.balance.toLong()} for ${c.name}.",
                    c.id
                )
                
                c.copy(balance = 0.0, paid = true)
            } else {
                c
            }
        }
 
        if (stateChanged) {
            state = state.copy(
                loans = updatedLoans,
                cards = updatedCards,
                localUpdatedAt = System.currentTimeMillis()
            )
            store.save(state)
        }

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
            if (l.dueDay > 0) continue // Skip if auto-processed
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

        // The card bill is the one payment with a real late fee attached, and
        // until its date became a date it was the only thing unwarnable.
        for (c in state.cards) {
            if (c.paid || c.balance <= 0.0 || c.nextDue.isEmpty()) continue
            val days = Ledger.daysBetween(now, c.nextDue)
            if (days < 0 || days > DAYS_AHEAD) continue
            Notifier.notifyDue(
                context,
                "${c.name} bill ${whenWord(days)} · ${inr(c.balance)}",
                "Due ${prettyDate(c.nextDue)}." +
                    if (c.minDue > 0) " Minimum ${inr(c.minDue)}." else "",
                c.id
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
