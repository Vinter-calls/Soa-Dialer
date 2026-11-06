package com.dialer.truedialer.service

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.dialer.truedialer.MainActivity
import com.dialer.truedialer.R
import com.dialer.truedialer.data.model.FollowUpReminder
import com.dialer.truedialer.util.PhoneNumberUtils

class FollowUpNotificationReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_PHONE_NUMBER = "extra_phone_number"
        const val EXTRA_CUSTOMER_NAME = "extra_customer_name"
        const val EXTRA_NOTE = "extra_note"
        const val EXTRA_REMINDER_ID = "extra_reminder_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val phone = intent.getStringExtra(EXTRA_PHONE_NUMBER) ?: ""
        val name = intent.getStringExtra(EXTRA_CUSTOMER_NAME) ?: "Lead"
        val note = intent.getStringExtra(EXTRA_NOTE) ?: "Follow-up scheduled"
        val id = intent.getStringExtra(EXTRA_REMINDER_ID) ?: System.currentTimeMillis().toString()

        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val mainPendingIntent = PendingIntent.getActivity(
            context,
            id.hashCode(),
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Direct Call Intent
        val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:${PhoneNumberUtils.normalize(phone)}")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val callPendingIntent = PendingIntent.getActivity(
            context,
            (id + "_call").hashCode(),
            callIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, "follow_up_channel")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Follow-up Reminder: $name")
            .setContentText("$phone • $note")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Time to call $name ($phone).\nNote: $note"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(mainPendingIntent)
            .addAction(R.drawable.ic_launcher_foreground, "Call Now", callPendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(id.hashCode(), notification)
    }
}

fun scheduleAlarm(context: Context, reminder: FollowUpReminder) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
    val intent = Intent(context, FollowUpNotificationReceiver::class.java).apply {
        putExtra(FollowUpNotificationReceiver.EXTRA_PHONE_NUMBER, reminder.phoneNumber)
        putExtra(FollowUpNotificationReceiver.EXTRA_CUSTOMER_NAME, reminder.customerName)
        putExtra(FollowUpNotificationReceiver.EXTRA_NOTE, reminder.note)
        putExtra(FollowUpNotificationReceiver.EXTRA_REMINDER_ID, reminder.id)
    }

    val pendingIntent = PendingIntent.getBroadcast(
        context,
        reminder.id.hashCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    try {
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            reminder.scheduledTimeEpochMs,
            pendingIntent
        )
    } catch (_: SecurityException) {
        alarmManager.set(AlarmManager.RTC_WAKEUP, reminder.scheduledTimeEpochMs, pendingIntent)
    }
}
