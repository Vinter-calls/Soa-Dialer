package com.dialer.truedialer

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.dialer.truedialer.data.repository.EnquiryRepositoryProvider

class TrueDialerApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        AppConfig.init(this)
        EnquiryRepositoryProvider.initialize(this)
        com.dialer.truedialer.data.local.EnquiryDatabaseHelper(this).cleanupSampleData()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            val overlayChannel = NotificationChannel(
                "caller_id_overlay_channel",
                getString(R.string.caller_id_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.caller_id_channel_desc)
            }
            manager?.createNotificationChannel(overlayChannel)

            val followUpChannel = NotificationChannel(
                "follow_up_channel",
                "Lead Follow-Up Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts for scheduled lead callbacks and follow-ups"
            }
            manager?.createNotificationChannel(followUpChannel)

            com.dialer.truedialer.service.MissedCallNotifier.createChannel(this)
            com.dialer.truedialer.service.CallNotificationManager.createChannel(this)
        }
    }
}
