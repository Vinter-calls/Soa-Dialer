package com.dialer.truedialer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.dialer.truedialer.R
import com.dialer.truedialer.receiver.CallActionReceiver
import com.dialer.truedialer.ui.activities.InCallActivity
import com.dialer.truedialer.util.PhoneNumberUtils

object CallNotificationManager {

    private const val CHANNEL_ID = "incoming_call_silent_channel_v4"
    private const val NOTIFICATION_ID = 9999

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            // Delete legacy noisy channels to ensure no duplicate ringtones/bells persist on device
            try {
                manager.deleteNotificationChannel("incoming_call_channel")
                manager.deleteNotificationChannel("incoming_call_channel_v2")
                manager.deleteNotificationChannel("incoming_call_silent_channel_v3")
            } catch (_: Exception) {}

            val channel = NotificationChannel(
                CHANNEL_ID,
                "Incoming Phone Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Full-screen alert and controls for incoming phone calls"
                // Telecom subsystem manages ringing and vibration for phone calls.
                // The notification must remain silent to prevent dual ringtone ("2 bells ringing").
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(true)
                setBypassDnd(true)
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun showIncomingCallNotification(context: Context, phoneNumber: String, callerDisplayName: String? = null) {
        createChannel(context)

        val cleanPhone = PhoneNumberUtils.normalize(phoneNumber)
        val nameToShow = callerDisplayName?.ifBlank { null } ?: PhoneNumberUtils.formatDisplay(cleanPhone)

        // 1. Full-screen Intent pointing directly to InCallActivity
        val fullScreenIntent = Intent(context, InCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(InCallActivity.EXTRA_PHONE_NUMBER, cleanPhone)
            putExtra(InCallActivity.EXTRA_IS_INCOMING, true)
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 2. Answer Action
        val answerIntent = Intent(context, CallActionReceiver::class.java).apply {
            action = CallActionReceiver.ACTION_ANSWER_CALL
            putExtra(CallActionReceiver.EXTRA_PHONE_NUMBER, cleanPhone)
        }
        val answerPendingIntent = PendingIntent.getBroadcast(
            context,
            NOTIFICATION_ID + 1,
            answerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 3. Decline Action
        val declineIntent = Intent(context, CallActionReceiver::class.java).apply {
            action = CallActionReceiver.ACTION_DECLINE_CALL
            putExtra(CallActionReceiver.EXTRA_PHONE_NUMBER, cleanPhone)
        }
        val declinePendingIntent = PendingIntent.getBroadcast(
            context,
            NOTIFICATION_ID + 2,
            declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Incoming Call")
            .setContentText(nameToShow)
            .setSubText(PhoneNumberUtils.formatDisplay(cleanPhone))
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setSound(null)
            .setVibrate(null)
            .setContentIntent(fullScreenPendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(android.R.drawable.sym_call_incoming, "Answer", answerPendingIntent)
            .addAction(android.R.drawable.sym_call_missed, "Decline", declinePendingIntent)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, builder.build())
    }

    fun cancelIncomingCallNotification(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.cancel(NOTIFICATION_ID)
    }
}
