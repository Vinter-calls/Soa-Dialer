package com.dialer.truedialer.service

import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import com.dialer.truedialer.ui.activities.InCallActivity

class DialerInCallService : InCallService() {

    companion object {
        var instance: DialerInCallService? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        if (instance == this) {
            instance = null
        }
        CallNotificationManager.cancelIncomingCallNotification(this)
        super.onDestroy()
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        super.onCallAudioStateChanged(audioState)
        ActiveCallManager.updateAudioState(
            isMuted = audioState.isMuted,
            isSpeaker = (audioState.route == CallAudioState.ROUTE_SPEAKER)
        )
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        instance = this
        if (call.state == Call.STATE_DISCONNECTED) {
            return
        }
        ActiveCallManager.addCall(call)
        val cleanNumber = ActiveCallManager.getCallPhoneNumber(call)
        if (cleanNumber.isNotBlank()) {
            ActiveCallManager.lastIncomingNumber = cleanNumber
        }

        // Initialize audio state from InCallService
        callAudioState?.let { audioState ->
            ActiveCallManager.updateAudioState(
                isMuted = audioState.isMuted,
                isSpeaker = (audioState.route == CallAudioState.ROUTE_SPEAKER)
            )
        }

        val isIncoming = call.state == Call.STATE_RINGING
        val displayNumber = cleanNumber.ifBlank { ActiveCallManager.lastIncomingNumber ?: "Unknown Number" }

        if (isIncoming) {
            CallNotificationManager.showIncomingCallNotification(this, displayNumber)
        }

        InCallActivity.start(this, displayNumber, isIncoming = isIncoming)

        call.registerCallback(object : Call.Callback() {
            override fun onDetailsChanged(call: Call, details: Call.Details) {
                super.onDetailsChanged(call, details)
                if (call.state == Call.STATE_DISCONNECTED) return
                val resolvedNumber = ActiveCallManager.getPhoneNumberFromDetails(details)
                    .ifBlank { ActiveCallManager.getCallPhoneNumber(call) }
                if (resolvedNumber.isNotBlank() && resolvedNumber != "Unknown Number") {
                    val numberChanged = (resolvedNumber != ActiveCallManager.lastIncomingNumber)
                    ActiveCallManager.lastIncomingNumber = resolvedNumber
                    if (call.state == Call.STATE_RINGING && numberChanged) {
                        CallNotificationManager.showIncomingCallNotification(this@DialerInCallService, resolvedNumber)
                    }
                    InCallActivity.start(this@DialerInCallService, resolvedNumber, isIncoming = (call.state == Call.STATE_RINGING))
                }
                ActiveCallManager.notifyCallUpdated()
            }

            override fun onStateChanged(call: Call, state: Int) {
                ActiveCallManager.notifyCallUpdated()
                if (state == Call.STATE_ACTIVE) {
                    ActiveCallManager.lastCallWasAnswered = true
                    CallNotificationManager.cancelIncomingCallNotification(this@DialerInCallService)
                } else if (state == Call.STATE_DISCONNECTED) {
                    CallNotificationManager.cancelIncomingCallNotification(this@DialerInCallService)
                    ActiveCallManager.removeCall(call)
                    if (ActiveCallManager.getCalls().isEmpty()) {
                        CallerOverlayService.dismissOverlay(this@DialerInCallService)
                    }
                }
            }
        })
        ActiveCallManager.notifyCallUpdated()
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        CallNotificationManager.cancelIncomingCallNotification(this)
        ActiveCallManager.removeCall(call)
        if (ActiveCallManager.getCalls().isEmpty()) {
            CallerOverlayService.dismissOverlay(this)
        }
        ActiveCallManager.notifyCallUpdated()
    }
}
