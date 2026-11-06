package com.dialer.truedialer.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.dialer.truedialer.data.local.EnquiryDatabaseHelper
import com.dialer.truedialer.service.scheduleAlarm

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.QUICKBOOT_POWER_ON" || intent.action == "com.htc.intent.action.QUICKBOOT_POWER_ON") {
            Log.i("BootReceiver", "Device booted up. Rescheduling follow-up alarms & starting services...")
            try {
                val dbHelper = EnquiryDatabaseHelper(context)
                val pendingReminders = dbHelper.getPendingFollowUps()
                for (reminder in pendingReminders) {
                    if (reminder.scheduledTimeEpochMs > System.currentTimeMillis()) {
                        scheduleAlarm(context, reminder)
                    }
                }
            } catch (e: Exception) {
                Log.e("BootReceiver", "Error rescheduling alarms on boot: ${e.message}", e)
            }
        }
    }
}
