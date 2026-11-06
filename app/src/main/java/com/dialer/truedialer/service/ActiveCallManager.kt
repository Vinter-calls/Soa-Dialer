package com.dialer.truedialer.service

import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import android.telephony.TelephonyManager
import com.dialer.truedialer.util.PhoneNumberUtils
import java.util.concurrent.CopyOnWriteArrayList

object ActiveCallManager {
    private val activeCalls = CopyOnWriteArrayList<Call>()
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    var lastIncomingNumber: String? = null
    var isInCallActivityVisible: Boolean = false
    var wasHandledByInCallService: Boolean = false
    var lastCallWasAnswered: Boolean = false
    var isOutgoingCall: Boolean = false

    fun isInCallServiceRunning(): Boolean = DialerInCallService.instance != null || activeCalls.isNotEmpty()

    var isMutedState: Boolean = false
    var isSpeakerState: Boolean = false

    fun updateAudioState(isMuted: Boolean, isSpeaker: Boolean) {
        isMutedState = isMuted
        isSpeakerState = isSpeaker
        notifyCallUpdated()
    }

    fun toggleMute(context: Context): Boolean {
        val newMuted = !isMutedState
        isMutedState = newMuted
        try {
            DialerInCallService.instance?.setMuted(newMuted)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.isMicrophoneMute = newMuted
        } catch (e: Exception) {
            e.printStackTrace()
        }
        notifyCallUpdated()
        return newMuted
    }

    fun toggleSpeaker(context: Context): Boolean {
        val newSpeaker = !isSpeakerState
        isSpeakerState = newSpeaker
        try {
            val targetRoute = if (newSpeaker) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_EARPIECE
            DialerInCallService.instance?.setAudioRoute(targetRoute)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (audioManager != null) {
                audioManager.mode = AudioManager.MODE_IN_CALL
                audioManager.isSpeakerphoneOn = newSpeaker
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        notifyCallUpdated()
        return newSpeaker
    }

    var currentCall: Call? = null
        get() = field ?: activeCalls.firstOrNull { it.state == Call.STATE_ACTIVE }
            ?: activeCalls.firstOrNull { it.state == Call.STATE_HOLDING }
            ?: activeCalls.firstOrNull { it.state == Call.STATE_RINGING }
            ?: activeCalls.firstOrNull()

    fun addCall(call: Call) {
        wasHandledByInCallService = true
        isOutgoingCall = (call.state != Call.STATE_RINGING)
        if (call.state == Call.STATE_ACTIVE) {
            lastCallWasAnswered = true
        } else if (call.state == Call.STATE_RINGING) {
            lastCallWasAnswered = false
        }
        if (!activeCalls.contains(call)) {
            activeCalls.add(call)
        }
        currentCall = call
        notifyCallUpdated()
    }

    fun removeCall(call: Call) {
        activeCalls.remove(call)
        if (activeCalls.isEmpty()) {
            currentCall = null
            isMutedState = false
            isSpeakerState = false
            try {
                DialerInCallService.instance?.let { ctx ->
                    val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                    am?.mode = AudioManager.MODE_NORMAL
                    am?.isMicrophoneMute = false
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else if (currentCall == call) {
            currentCall = activeCalls.firstOrNull { it.state == Call.STATE_ACTIVE }
                ?: activeCalls.firstOrNull { it.state == Call.STATE_HOLDING }
                ?: activeCalls.firstOrNull()
        }
        notifyCallUpdated()
    }

    fun getCalls(): List<Call> = activeCalls.toList()

    fun getActiveCall(): Call? =
        activeCalls.firstOrNull { it.state == Call.STATE_ACTIVE }
            ?: if (currentCall?.state == Call.STATE_ACTIVE) currentCall else null

    fun getHeldCall(): Call? =
        activeCalls.firstOrNull { it.state == Call.STATE_HOLDING }
            ?: if (currentCall?.state == Call.STATE_HOLDING) currentCall else null

    fun getRingingCall(): Call? =
        activeCalls.firstOrNull { it.state == Call.STATE_RINGING }
            ?: if (currentCall?.state == Call.STATE_RINGING) currentCall else null

    fun hasMultipleCalls(): Boolean = activeCalls.size > 1

    fun canDeflect(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    fun transferCall(targetNumber: String): Boolean {
        val call = currentCall ?: return false
        val clean = PhoneNumberUtils.normalize(targetNumber)
        val uri = Uri.parse("tel:$clean")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                call.deflect(uri)
                return true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return false
    }

    fun isConference(): Boolean {
        val call = getActiveCall() ?: currentCall
        return call?.details?.hasProperty(Call.Details.PROPERTY_CONFERENCE) == true ||
                (call?.children?.isNotEmpty() == true)
    }

    fun canMerge(): Boolean {
        val active = getActiveCall()
        val held = getHeldCall()
        if (active != null && held != null) return true
        if (activeCalls.size >= 2) return true
        return active?.details?.can(Call.Details.CAPABILITY_MERGE_CONFERENCE) == true
    }

    fun mergeCalls(): Boolean {
        try {
            val active = getActiveCall()
            val held = getHeldCall()
            if (active != null && held != null) {
                active.conference(held)
                notifyCallUpdated()
                return true
            }
            if (activeCalls.size >= 2) {
                activeCalls[0].conference(activeCalls[1])
                notifyCallUpdated()
                return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    fun swapCalls(): Boolean {
        try {
            val active = getActiveCall()
            val held = getHeldCall()
            if (active != null && held != null) {
                active.hold()
                held.unhold()
                currentCall = held
                notifyCallUpdated()
                return true
            }
            val first = activeCalls.getOrNull(0)
            val second = activeCalls.getOrNull(1)
            if (first != null && second != null) {
                if (first.state == Call.STATE_ACTIVE) {
                    first.hold()
                    second.unhold()
                    currentCall = second
                } else {
                    second.hold()
                    first.unhold()
                    currentCall = first
                }
                notifyCallUpdated()
                return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    fun holdAndAnswerRingingCall(context: Context): Boolean {
        val ringing = getRingingCall()
        val active = getActiveCall()
        if (active != null && active.state == Call.STATE_ACTIVE) {
            try {
                active.hold()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (ringing != null) {
            try {
                ringing.answer(VideoProfile.STATE_AUDIO_ONLY)
                currentCall = ringing
                notifyCallUpdated()
                return true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return answerCall(context)
    }

    fun rejectRingingCall(): Boolean {
        val ringing = getRingingCall()
        if (ringing != null) {
            try {
                ringing.reject(false, null)
                activeCalls.remove(ringing)
                notifyCallUpdated()
                return true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return false
    }

    fun getPhoneNumberFromDetails(details: Call.Details?): String {
        if (details == null) return ""

        // 1. Direct handle
        val handle = details.handle
        if (handle != null) {
            val ssp = handle.schemeSpecificPart
            if (!ssp.isNullOrBlank() && ssp != "null") {
                val clean = PhoneNumberUtils.normalize(ssp)
                if (clean.isNotBlank()) return clean
            }
            val uriStr = handle.toString()
            if (uriStr.startsWith("tel:", ignoreCase = true)) {
                val clean = PhoneNumberUtils.normalize(uriStr.substring(4))
                if (clean.isNotBlank()) return clean
            }
            if (uriStr.isNotBlank() && uriStr != "null" && uriStr.any { it.isDigit() }) {
                val clean = PhoneNumberUtils.normalize(uriStr)
                if (clean.isNotBlank()) return clean
            }
        }

        // 2. Gateway original address
        val gateway = details.gatewayInfo?.originalAddress?.schemeSpecificPart
        if (!gateway.isNullOrBlank()) {
            val clean = PhoneNumberUtils.normalize(gateway)
            if (clean.isNotBlank()) return clean
        }

        // 3. Intent Extras
        val intentExtras = details.intentExtras
        if (intentExtras != null) {
            val num1 = intentExtras.getString(TelephonyManager.EXTRA_INCOMING_NUMBER)
            if (!num1.isNullOrBlank()) return PhoneNumberUtils.normalize(num1)
            val num2 = intentExtras.getString("android.telecom.extra.INCOMING_NUMBER")
            if (!num2.isNullOrBlank()) return PhoneNumberUtils.normalize(num2)
            val num3 = intentExtras.getString("incoming_number")
            if (!num3.isNullOrBlank()) return PhoneNumberUtils.normalize(num3)
        }

        // 4. Details Extras
        val extras = details.extras
        if (extras != null) {
            val num1 = extras.getString(TelephonyManager.EXTRA_INCOMING_NUMBER)
            if (!num1.isNullOrBlank()) return PhoneNumberUtils.normalize(num1)
            val num2 = extras.getString("android.telecom.extra.INCOMING_NUMBER")
            if (!num2.isNullOrBlank()) return PhoneNumberUtils.normalize(num2)
            val num3 = extras.getString("incoming_number")
            if (!num3.isNullOrBlank()) return PhoneNumberUtils.normalize(num3)
            val num4 = extras.getString("phone_number")
            if (!num4.isNullOrBlank()) return PhoneNumberUtils.normalize(num4)
        }

        // 5. Caller display name if numeric
        val callerName = details.callerDisplayName
        if (!callerName.isNullOrBlank() && callerName.any { it.isDigit() }) {
            return PhoneNumberUtils.normalize(callerName)
        }

        return ""
    }

    fun getCallPhoneNumber(call: Call?): String {
        if (call == null) return lastIncomingNumber ?: ""
        val resolved = getPhoneNumberFromDetails(call.details)
        if (resolved.isNotBlank()) {
            lastIncomingNumber = resolved
            return resolved
        }
        return lastIncomingNumber ?: ""
    }

    fun isRinging(): Boolean {
        return getRingingCall() != null || currentCall?.state == Call.STATE_RINGING
    }

    fun isOutgoing(): Boolean {
        val state = currentCall?.state
        return state == Call.STATE_DIALING || state == Call.STATE_CONNECTING
    }

    fun isActive(): Boolean {
        return getActiveCall() != null || currentCall?.state == Call.STATE_ACTIVE
    }

    fun getCallState(): Int {
        return currentCall?.state ?: Call.STATE_DISCONNECTED
    }

    fun answerCall(context: Context): Boolean {
        lastCallWasAnswered = true
        val ringing = getRingingCall() ?: currentCall
        if (ringing != null) {
            try {
                ringing.answer(VideoProfile.STATE_AUDIO_ONLY)
                notifyCallUpdated()
                return true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Fallback using TelecomManager if permitted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                telecomManager?.acceptRingingCall()
                notifyCallUpdated()
                return true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return false
    }

    private var isRejecting = false

    fun rejectCall(context: Context): Boolean {
        if (isRejecting) return false
        isRejecting = true
        try {
            val ringing = getRingingCall()
            if (ringing != null) {
                try {
                    ringing.reject(false, null)
                    activeCalls.remove(ringing)
                    notifyCallUpdated()
                    return true
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val call = currentCall
            if (call != null) {
                try {
                    if (call.state == Call.STATE_RINGING) {
                        call.reject(false, null)
                    } else if (call.state != Call.STATE_DISCONNECTED) {
                        call.disconnect()
                    }
                    activeCalls.remove(call)
                    notifyCallUpdated()
                    return true
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                    telecomManager?.endCall()
                    notifyCallUpdated()
                    return true
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            return false
        } finally {
            isRejecting = false
        }
    }

    fun addListener(listener: () -> Unit) {
        if (!listeners.contains(listener)) listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun notifyCallUpdated() {
        listeners.forEach {
            try {
                it.invoke()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
