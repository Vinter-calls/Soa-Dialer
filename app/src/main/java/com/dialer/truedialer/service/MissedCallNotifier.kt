package com.dialer.truedialer.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.dialer.truedialer.MainActivity
import com.dialer.truedialer.R
import com.dialer.truedialer.data.local.EnquiryDatabaseHelper
import com.dialer.truedialer.data.repository.ContactsRepository
import com.dialer.truedialer.ui.activities.PostCallActivity
import com.dialer.truedialer.util.PhoneNumberUtils

object MissedCallNotifier {

    const val CHANNEL_ID = "missed_calls_channel"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Missed Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts and instant callback actions for missed calls"
                enableVibration(true)
                setShowBadge(true)
            }
            manager?.createNotificationChannel(channel)
        }
    }

    fun showMissedCallNotification(
        context: Context,
        phoneNumber: String,
        ringDurationSeconds: Long = 0L
    ) {
        if (!com.dialer.truedialer.AppConfig.NOTIF_MISSED_CALL) return
        if (phoneNumber.isBlank() || phoneNumber == "Unknown Number") return
        createChannel(context)

        val clean = PhoneNumberUtils.normalize(phoneNumber)
        val contactsRepo = ContactsRepository(context)
        val dbHelper = EnquiryDatabaseHelper(context)
        val contactName = kotlinx.coroutines.runBlocking { contactsRepo.getContactName(clean) }
        val enquiry = dbHelper.findEnquiryByPhone(clean)
        val displayName = contactName ?: enquiry?.name ?: PhoneNumberUtils.formatDisplay(clean)

        val notifId = clean.hashCode()

        // 1. Content Intent (Open App Recents)
        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val mainPendingIntent = PendingIntent.getActivity(
            context,
            notifId,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 2. Direct Call Back Intent
        val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$clean")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val callPendingIntent = PendingIntent.getActivity(
            context,
            notifId + 1,
            callIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 3. WhatsApp Message Intent
        val waUri = Uri.parse("https://api.whatsapp.com/send?phone=${clean.replace("+", "")}&text=Hello%2C%20I%20missed%20your%20call.%20How%20can%20I%20help%20you%3F")
        val waIntent = Intent(Intent.ACTION_VIEW, waUri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val waPendingIntent = PendingIntent.getActivity(
            context,
            notifId + 2,
            waIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 4. Post-Call Notes & Lead Tagging Intent
        val noteIntent = Intent(context, PostCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(PostCallActivity.EXTRA_PHONE_NUMBER, clean)
            putExtra(PostCallActivity.EXTRA_CALL_DURATION, 0L)
            putExtra(PostCallActivity.EXTRA_CALL_TYPE, "MISSED")
        }
        val notePendingIntent = PendingIntent.getActivity(
            context,
            notifId + 3,
            noteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val ringText = if (ringDurationSeconds > 0) " (Rang ${ringDurationSeconds}s)" else ""
        val subtext = "${PhoneNumberUtils.formatDisplay(clean)}$ringText"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Missed Call from $displayName")
            .setContentText(subtext)
            .setStyle(NotificationCompat.BigTextStyle().bigText("Missed call from $displayName ($clean)$ringText.\nTap to view or choose an action below."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(mainPendingIntent)
            .addAction(R.drawable.ic_launcher_foreground, "Call Back", callPendingIntent)
            .addAction(R.drawable.ic_launcher_foreground, "WhatsApp", waPendingIntent)
            .addAction(R.drawable.ic_launcher_foreground, "Add Note", notePendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(notifId, notification)
    }
}
