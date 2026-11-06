package com.dialer.truedialer.service

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import androidx.annotation.RequiresApi
import com.dialer.truedialer.AppConfig
import com.dialer.truedialer.data.local.EnquiryDatabaseHelper
import com.dialer.truedialer.util.PermissionUtils
import com.dialer.truedialer.util.PhoneNumberUtils

@RequiresApi(Build.VERSION_CODES.Q)
class EnquiryCallScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        try {
            val phoneNumber = ActiveCallManager.getPhoneNumberFromDetails(callDetails)
            if (phoneNumber.isNotBlank()) {
                ActiveCallManager.lastIncomingNumber = phoneNumber
            }
            val cleanNumber = phoneNumber.ifBlank { ActiveCallManager.lastIncomingNumber ?: "" }

            val dbHelper = EnquiryDatabaseHelper(this)
            val isSpam = if (cleanNumber.isNotBlank()) dbHelper.isNumberSpam(cleanNumber) else false

            // Feature 6: Auto-reject/silence if spam and enabled
            if (isSpam && AppConfig.AUTO_REJECT_SPAM) {
                val spamResponse = CallResponse.Builder()
                    .setDisallowCall(true)
                    .setRejectCall(true)
                    .setSkipCallLog(false)
                    .setSkipNotification(true)
                    .build()
                respondToCall(callDetails, spamResponse)
                return
            }

            // Show Truecaller-style floating overlay only when InCallActivity is not visible
            if (cleanNumber.isNotBlank() && !ActiveCallManager.isInCallActivityVisible && PermissionUtils.canDrawOverlays(this)) {
                CallerOverlayService.showOverlay(this, cleanNumber)
            }

            // Allow call to ring normally while overlay identifies the caller
            val response = CallResponse.Builder()
                .setDisallowCall(false)
                .setRejectCall(false)
                .setSkipCallLog(false)
                .setSkipNotification(false)
                .build()

            respondToCall(callDetails, response)
        } catch (e: Exception) {
            Log.w("CallScreeningService", "Error during onScreenCall: ${e.message}", e)
        }
    }
}
