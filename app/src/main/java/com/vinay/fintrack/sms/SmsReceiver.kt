package com.vinay.fintrack.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.vinay.fintrack.data.SmsImporter
import com.vinay.fintrack.data.looksLikeBankSender

/**
 * Catches bank messages as they arrive, so a payment shows up in the app
 * without anything being opened or tapped.
 *
 * Multi-part messages arrive as several PDUs belonging to one message, so the
 * parts are joined per sender before parsing — a long HDFC alert split across
 * two parts would otherwise lose its reference number.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = runCatching { Telephony.Sms.Intents.getMessagesFromIntent(intent) }
            .getOrNull() ?: return

        val joined = LinkedHashMap<String, StringBuilder>()
        var receivedAt = System.currentTimeMillis()
        for (m in messages) {
            val sender = m.originatingAddress.orEmpty()
            joined.getOrPut(sender) { StringBuilder() }.append(m.messageBody.orEmpty())
            receivedAt = m.timestampMillis.takeIf { it > 0 } ?: receivedAt
        }

        val importer = SmsImporter(context.applicationContext)
        val pending = goAsync()
        Thread {
            try {
                for ((sender, body) in joined) {
                    if (!looksLikeBankSender(sender)) continue
                    runCatching { importer.importOne(body.toString(), sender, receivedAt) }
                        .onFailure { Log.w(TAG, "import failed", it) }
                }
            } finally {
                pending.finish()
            }
        }.start()
    }

    private companion object {
        const val TAG = "SmsReceiver"
    }
}
