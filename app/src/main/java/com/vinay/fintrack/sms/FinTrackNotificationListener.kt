package com.vinay.fintrack.sms

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.vinay.fintrack.data.SmsImporter
import com.vinay.fintrack.data.looksLikeBankSender

/**
 * Modern Android Standard for Automatic Expense Tracking.
 *
 * Listens for incoming status bar notifications from:
 * 1. Default SMS / Messaging apps (Google Messages, Samsung Messages, etc.) carrying Bank SMS
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

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()

        val fullText = when {
            bigText.isNotBlank() -> bigText
            text.isNotBlank() -> text
            else -> ""
        }

        if (fullText.isBlank()) return

        val isTargetApp = isSupportedPackage(pkg) || looksLikeBankSender(title)
        if (!isTargetApp) return

        // Smart text composition: if the title has crucial merchant/source info, combine it
        val compositeBody = if (title.isNotBlank() && !fullText.contains(title, ignoreCase = true)) {
            "$title: $fullText"
        } else {
            fullText
        }

        val receivedAt = if (sbn.postTime > 0) sbn.postTime else System.currentTimeMillis()
        val sender = title.ifBlank { getAppNameFromPkg(pkg) }

        Thread {
            try {
                val importer = SmsImporter(applicationContext)
                val recorded = importer.importOne(compositeBody, sender, receivedAt)
                if (recorded) {
                    Log.i(TAG, "Recorded transaction from notification: pkg=$pkg, sender=$sender")
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
