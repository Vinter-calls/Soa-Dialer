package com.dialer.truedialer.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dialer.truedialer.service.ActiveCallManager
import com.dialer.truedialer.service.CallNotificationManager
import com.dialer.truedialer.ui.activities.InCallActivity

class CallActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_ANSWER_CALL = "com.dialer.truedialer.ACTION_ANSWER_CALL"
        const val ACTION_DECLINE_CALL = "com.dialer.truedialer.ACTION_DECLINE_CALL"
        const val EXTRA_PHONE_NUMBER = "extra_phone_number"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val phoneNumber = intent.getStringExtra(EXTRA_PHONE_NUMBER) ?: ""
        when (intent.action) {
            ACTION_ANSWER_CALL -> {
                CallNotificationManager.cancelIncomingCallNotification(context)
                ActiveCallManager.answerCall(context)
                val target = if (phoneNumber.isNotBlank()) phoneNumber else (ActiveCallManager.lastIncomingNumber ?: "")
                InCallActivity.start(context, target, isIncoming = false)
            }
            ACTION_DECLINE_CALL -> {
                CallNotificationManager.cancelIncomingCallNotification(context)
                ActiveCallManager.rejectCall(context)
            }
        }
    }
}
