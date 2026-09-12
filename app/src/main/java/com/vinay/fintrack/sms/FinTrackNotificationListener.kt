package com.vinay.fintrack.sms

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.vinay.fintrack.data.SmsImporter
import com.vinay.fintrack.data.looksLikeBankMessage
import com.vinay.fintrack.data.looksLikeBankSender
import com.vinay.fintrack.data.parseBankSms

/**
 * Modern Android Standard for Automatic Expense Tracking.
 *
 * Listens for incoming status bar notifications from:
 * 1. Default SMS / Messaging apps (Google Messages, Samsung Messages, OEM Messages, etc.) carrying Bank SMS
 * 2. UPI & Payment apps (Google Pay, PhonePe, Paytm, CRED, BHIM, Bank Apps)
 *
 * Catches transaction alerts immediately in the background and logs them
 * into FinTrack with 100% Google Play Store policy compliance.
 */
class FinTrackNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val pkg = sbn.packageName.orEmpty()

        // Never process our own notifications to avoid recursion loops
        if (pkg == packageName) return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim().orEmpty()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim().orEmpty()
        val conversationTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()?.trim().orEmpty()
        val infoText = extras.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString()?.trim().orEmpty()
        val summaryText = extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString()?.trim().orEmpty()
        val titleBig = extras.getCharSequence("android.title.big")?.toString()?.trim().orEmpty()
        val ticker = notification.tickerText?.toString()?.trim().orEmpty()

        // Extract all candidate text snippets from standard extras, lines, and MessagingStyle bundles
        val candidateTexts = mutableListOf<String>()

        fun addSnippet(s: String) {
            val trimmed = s.trim()
            if (trimmed.isNotBlank() && !candidateTexts.contains(trimmed)) {
                candidateTexts.add(trimmed)
            }
        }

        if (bigText.isNotBlank()) addSnippet(bigText)
        if (text.isNotBlank()) addSnippet(text)
        if (infoText.isNotBlank()) addSnippet(infoText)
        if (summaryText.isNotBlank()) addSnippet(summaryText)
        if (ticker.isNotBlank()) addSnippet(ticker)

        // Handle InboxStyle (lines)
        extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach { line ->
            line?.toString()?.let { addSnippet(it) }
        }

        // Handle MessagingStyle (array of message Bundles or Objects)
        val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        if (messages != null) {
            for (msg in messages) {
                if (msg is Bundle) {
                    msg.getCharSequence("text")?.toString()?.let { addSnippet(it) }
                } else if (msg != null) {
                    runCatching {
                        val textMethod = msg.javaClass.getMethod("getText")
                        (textMethod.invoke(msg) as? CharSequence)?.toString()?.let { addSnippet(it) }
                    }
                }
            }
        }

        if (candidateTexts.isEmpty() && title.isNotBlank()) {
            addSnippet(title)
        }

        if (candidateTexts.isEmpty()) return

        // Pick the most accurate sender identifier
        val sender = when {
            looksLikeBankSender(subText) -> subText
            looksLikeBankSender(conversationTitle) -> conversationTitle
            looksLikeBankSender(title) -> title
            looksLikeBankSender(titleBig) -> titleBig
            title.isNotBlank() && title.lowercase() != "messages" -> title
            subText.isNotBlank() && subText.lowercase() != "messages" -> subText
            else -> getAppNameFromPkg(pkg)
        }

        // Check if this notification is relevant
        val isTargetApp = isSupportedPackage(pkg) ||
            looksLikeBankSender(title) ||
            looksLikeBankSender(subText) ||
            looksLikeBankSender(conversationTitle) ||
            candidateTexts.any { looksLikeBankMessage(it) || parseBankSms(it, sender) != null }

        if (!isTargetApp) return

        val receivedAt = if (sbn.postTime > 0) sbn.postTime else System.currentTimeMillis()

        Thread {
            try {
                val importer = SmsImporter(applicationContext)
                for (rawCandidate in candidateTexts) {
                    // Try parsing with sender prepended or clean candidate
                    val compositeBody = if (title.isNotBlank() && !rawCandidate.contains(title, ignoreCase = true)) {
                        "$title: $rawCandidate"
                    } else {
                        rawCandidate
                    }

                    // Try composite first, then rawCandidate
                    val recorded = importer.importOne(compositeBody, sender, receivedAt) ||
                        importer.importOne(rawCandidate, sender, receivedAt)

                    if (recorded) {
                        Log.i(TAG, "Recorded transaction from notification: pkg=$pkg, sender=$sender")
                        break
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to process notification from $pkg", e)
            }
        }.start()
    }

    private fun isSupportedPackage(pkg: String): Boolean {
        val lower = pkg.lowercase()
        return lower.contains("messaging") ||
            lower.contains("mms") ||
            lower.contains("sms") ||
            lower.contains("telephony") ||
            lower.contains("message") ||
            KNOWN_PAYMENT_PACKAGES.any { lower == it || lower.contains(it) }
    }

    private fun getAppNameFromPkg(pkg: String): String {
        return when {
            pkg.contains("paisa") || pkg.contains("nbu") -> "Google Pay"
            pkg.contains("phonepe") -> "PhonePe"
            pkg.contains("paytm") -> "Paytm"
            pkg.contains("dreamplug") || pkg.contains("cred") -> "CRED"
            pkg.contains("npci") || pkg.contains("bhim") -> "BHIM"
            pkg.contains("amazon") -> "Amazon Pay"
            else -> "Notification"
        }
    }

    companion object {
        private const val TAG = "FinTrackNotifListener"

        val KNOWN_PAYMENT_PACKAGES = setOf(
            "com.google.android.apps.nbu.paisa.user", // Google Pay
            "com.phonepe.app",                        // PhonePe
            "net.one97.paytm",                        // Paytm
            "com.dreamplug.androidapp",               // CRED
            "in.org.npci.upiapp",                     // BHIM UPI
            "com.amazon.mShop.android.shopping",      // Amazon Pay
            "com.csam.icici.bank.imobile",            // iMobile Pay
            "com.sbi.lotusintouch",                   // YONO SBI
            "com.hdfcbank.mobilebanking",             // HDFC MobileBanking
            "com.axis.mobile",                        // Axis Mobile
            "com.msf.kbank.mobile",                   // Kotak Bank
            "com.fedmobile",                          // Federal Bank
            "com.rblbank.mobank",                     // RBL MoBank
            "com.upi.axispay",                        // Axis Pay
            "com.mobikwik_new",                       // MobiKwik
            "com.freecharge.android",                 // FreeCharge
            "money.jupiter",                          // Jupiter
            "co.fi.money"                             // Fi Money
        )

        /**
         * Checks whether Android Notification Access has been enabled by the user.
         */
        fun isNotificationAccessGranted(context: Context): Boolean {
            val pkgName = context.packageName
            val flat = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            )
            return flat != null && flat.contains(pkgName)
        }

        /**
         * Opens Android's system Notification Access settings screen.
         */
        fun openNotificationAccessSettings(context: Context) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
