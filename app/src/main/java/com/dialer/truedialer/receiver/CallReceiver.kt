package com.dialer.truedialer.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import com.dialer.truedialer.AppConfig
import com.dialer.truedialer.service.CallerOverlayService
import com.dialer.truedialer.ui.activities.PostCallActivity
import com.dialer.truedialer.util.PermissionUtils
import com.dialer.truedialer.util.PhoneNumberUtils

class CallReceiver : BroadcastReceiver() {

    companion object {
        private var lastState = TelephonyManager.EXTRA_STATE_IDLE
        private var activePhoneNumber: String? = null
        private var ringStartTime: Long = 0L
        private var ringDuration: Long = 0L
        private var callStartTime: Long = 0L
    }

    override fun onReceive(context: Context, intent: Intent) {
        try {
            if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

            val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

            if (!incomingNumber.isNullOrBlank()) {
                val normalized = PhoneNumberUtils.normalize(incomingNumber)
                activePhoneNumber = normalized
                com.dialer.truedialer.service.ActiveCallManager.lastIncomingNumber = normalized
            }

            when (state) {
                TelephonyManager.EXTRA_STATE_RINGING -> {
                    ringStartTime = System.currentTimeMillis()
                    ringDuration = 0L
                    val resolvedNum = activePhoneNumber
                        ?: com.dialer.truedialer.service.ActiveCallManager.lastIncomingNumber
                        ?: com.dialer.truedialer.service.ActiveCallManager.getCallPhoneNumber(com.dialer.truedialer.service.ActiveCallManager.currentCall)

                    if (!resolvedNum.isNullOrBlank() && resolvedNum != "Unknown Number" && !com.dialer.truedialer.service.ActiveCallManager.isInCallActivityVisible && PermissionUtils.canDrawOverlays(context)) {
                        val cleanNumber = PhoneNumberUtils.normalize(resolvedNum)
                        CallerOverlayService.showOverlay(context, cleanNumber)
                    }
                }
                TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                    if (ringStartTime > 0) {
                        ringDuration = (System.currentTimeMillis() - ringStartTime) / 1000
                    }
                    callStartTime = System.currentTimeMillis()
                }
                TelephonyManager.EXTRA_STATE_IDLE -> {
                    // Dismiss overlay safely
                    CallerOverlayService.dismissOverlay(context)

                    // If call was handled by DialerInCallService or InCallActivity, avoid duplicate logging or false missed alerts
                    val handledByApp = com.dialer.truedialer.service.ActiveCallManager.wasHandledByInCallService ||
                            com.dialer.truedialer.service.ActiveCallManager.isInCallServiceRunning() ||
                            com.dialer.truedialer.service.ActiveCallManager.isInCallActivityVisible

                    val phoneToReport = activePhoneNumber
                        ?: com.dialer.truedialer.service.ActiveCallManager.lastIncomingNumber
                    val talkDurationSeconds = if (callStartTime > 0) (System.currentTimeMillis() - callStartTime) / 1000 else 0L
                    val finalRingSeconds = if (ringDuration > 0) ringDuration else if (ringStartTime > 0) (System.currentTimeMillis() - ringStartTime) / 1000 else 0L

                    if (!handledByApp && !phoneToReport.isNullOrBlank() && phoneToReport != "Unknown Number") {
                        val dbHelper = com.dialer.truedialer.data.local.EnquiryDatabaseHelper(context)
                        val isOutgoing = com.dialer.truedialer.service.ActiveCallManager.isOutgoingCall
                        val wasAnswered = lastState == TelephonyManager.EXTRA_STATE_OFFHOOK ||
                                talkDurationSeconds > 0 ||
                                com.dialer.truedialer.service.ActiveCallManager.lastCallWasAnswered

                        // Only consider MISSED if it was an incoming call that was actually ringing and never answered
                        val callType = if (isOutgoing) {
                            "OUTGOING"
                        } else if (wasAnswered) {
                            "INCOMING"
                        } else if (lastState == TelephonyManager.EXTRA_STATE_RINGING) {
                            "MISSED"
                        } else {
                            "INCOMING"
                        }

                        dbHelper.recordCallStats(phoneToReport, callType, finalRingSeconds, talkDurationSeconds)

                        if (callType == "MISSED" && !isOutgoing && AppConfig.NOTIF_MISSED_CALL) {
                            com.dialer.truedialer.service.MissedCallNotifier.showMissedCallNotification(
                                context = context,
                                phoneNumber = phoneToReport,
                                ringDurationSeconds = finalRingSeconds
                            )
                        }

                        // Trigger Post-Call Dialog only if call was actually answered/active (OFFHOOK) or outgoing
                        if ((wasAnswered || isOutgoing) && AppConfig.SHOW_POST_CALL_DIALOG) {
                            try {
                                val postCallIntent = Intent(context, PostCallActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                    putExtra(PostCallActivity.EXTRA_PHONE_NUMBER, phoneToReport)
                                    putExtra(PostCallActivity.EXTRA_CALL_DURATION, talkDurationSeconds)
                                    putExtra(PostCallActivity.EXTRA_CALL_TYPE, if (isOutgoing) "OUTGOING" else "INCOMING")
                                }
                                context.startActivity(postCallIntent)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }

                    // Reset trackers
                    com.dialer.truedialer.service.ActiveCallManager.wasHandledByInCallService = false
                    com.dialer.truedialer.service.ActiveCallManager.lastCallWasAnswered = false
                    com.dialer.truedialer.service.ActiveCallManager.isOutgoingCall = false
                    activePhoneNumber = null
                    callStartTime = 0L
                    ringStartTime = 0L
                    ringDuration = 0L
                }
            }

            if (state != null) {
                lastState = state
            }
        } catch (e: Exception) {
            Log.e("CallReceiver", "Error in CallReceiver: ${e.message}", e)
        }
    }
}
