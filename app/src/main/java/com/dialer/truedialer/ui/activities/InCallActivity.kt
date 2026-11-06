package com.dialer.truedialer.ui.activities

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.Call
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.dialer.truedialer.AppConfig
import com.dialer.truedialer.data.local.EnquiryDatabaseHelper
import com.dialer.truedialer.data.model.CenterUser
import com.dialer.truedialer.data.model.ContactRecord
import com.dialer.truedialer.data.model.Enquiry
import com.dialer.truedialer.data.repository.ContactsRepository
import com.dialer.truedialer.data.repository.EnquiryRepositoryProvider
import com.dialer.truedialer.service.ActiveCallManager
import com.dialer.truedialer.ui.components.EnquiryBadge
import com.dialer.truedialer.ui.components.SaveContactDialog
import com.dialer.truedialer.ui.components.SyncEnquiryDialog
import com.dialer.truedialer.ui.theme.CoralRed
import com.dialer.truedialer.ui.theme.EmeraldGreen
import com.dialer.truedialer.ui.theme.TrueBlue
import com.dialer.truedialer.util.CallRecorder
import com.dialer.truedialer.util.CallRecordingUtils
import com.dialer.truedialer.util.PhoneNumberUtils
import com.dialer.truedialer.util.WhatsAppBusinessHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class InCallActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PHONE_NUMBER = "extra_phone_number"
        const val EXTRA_IS_INCOMING = "extra_is_incoming"

        fun start(context: Context, phoneNumber: String, isIncoming: Boolean = false) {
            val intent = Intent(context, InCallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_PHONE_NUMBER, phoneNumber)
                putExtra(EXTRA_IS_INCOMING, isIncoming)
            }
            context.startActivity(intent)
        }
    }

    private lateinit var callRecorder: CallRecorder
    private val activePhoneNumberState = mutableStateOf("")
    private var isActivityFinished = false

    private fun finishActivitySafely(
        phone: String? = null,
        talkSeconds: Long = 0L,
        isIncoming: Boolean = false,
        wasMissed: Boolean = false
    ) {
        if (isActivityFinished) return
        isActivityFinished = true
        ActiveCallManager.isInCallActivityVisible = false
        if (callRecorder.isRecording) {
            try {
                val file = callRecorder.stopRecording()
                Toast.makeText(this, "Recording saved: ${file?.name}", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        com.dialer.truedialer.service.CallerOverlayService.dismissOverlay(this)

        val targetPhone = phone?.ifBlank { null }
            ?: activePhoneNumberState.value.ifBlank { null }
            ?: ActiveCallManager.lastIncomingNumber

        if (!targetPhone.isNullOrBlank() && targetPhone != "Unknown Number") {
            if (wasMissed && isIncoming) {
                if (AppConfig.NOTIF_MISSED_CALL) {
                    com.dialer.truedialer.service.MissedCallNotifier.showMissedCallNotification(
                        context = this,
                        phoneNumber = targetPhone
                    )
                }
            } else if (AppConfig.SHOW_POST_CALL_DIALOG) {
                try {
                    val postCallIntent = Intent(this, PostCallActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra(PostCallActivity.EXTRA_PHONE_NUMBER, targetPhone)
                        putExtra(PostCallActivity.EXTRA_CALL_DURATION, talkSeconds)
                        putExtra(PostCallActivity.EXTRA_CALL_TYPE, if (isIncoming) "INCOMING" else "OUTGOING")
                    }
                    startActivity(postCallIntent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // If the call was already cut before ringing or disconnected:
        if (ActiveCallManager.getCalls().isEmpty() && ActiveCallManager.currentCall == null) {
            com.dialer.truedialer.service.CallerOverlayService.dismissOverlay(this)
            finish()
            return
        }

        ActiveCallManager.isInCallActivityVisible = true
        com.dialer.truedialer.service.CallerOverlayService.dismissOverlay(this)

        val initialPhone = intent.getStringExtra(EXTRA_PHONE_NUMBER) ?: ""
        if (initialPhone.isNotBlank() && initialPhone != "Unknown Number") {
            ActiveCallManager.lastIncomingNumber = initialPhone
        }
        activePhoneNumberState.value = initialPhone
        val isIncoming = if (intent.hasExtra(EXTRA_IS_INCOMING)) {
            intent.getBooleanExtra(EXTRA_IS_INCOMING, false)
        } else {
            ActiveCallManager.isRinging()
        }
        callRecorder = CallRecorder(this)

        // Ensure incoming calls wake up screen and show over lockscreen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            keyguardManager?.requestDismissKeyguard(this, null)
        }
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        setContent {
            InCallScreen(
                initialPhoneNumber = initialPhone,
                phoneNumberState = activePhoneNumberState,
                initialIsIncoming = isIncoming,
                callRecorder = callRecorder,
                onAnswerCall = {
                    com.dialer.truedialer.service.CallNotificationManager.cancelIncomingCallNotification(this)
                    ActiveCallManager.answerCall(this)
                },
                onHangUp = {
                    com.dialer.truedialer.service.CallNotificationManager.cancelIncomingCallNotification(this)
                    ActiveCallManager.rejectCall(this)
                },
                onCallEnded = { phone, talkSec, wasMissed ->
                    com.dialer.truedialer.service.CallNotificationManager.cancelIncomingCallNotification(this)
                    finishActivitySafely(
                        phone = phone,
                        talkSeconds = talkSec,
                        isIncoming = isIncoming,
                        wasMissed = wasMissed
                    )
                }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        val newNum = intent.getStringExtra(EXTRA_PHONE_NUMBER)
        if (!newNum.isNullOrBlank() && newNum != "Unknown Number") {
            ActiveCallManager.lastIncomingNumber = newNum
            activePhoneNumberState.value = newNum
        }
    }

    override fun onResume() {
        super.onResume()
        ActiveCallManager.isInCallActivityVisible = true
        com.dialer.truedialer.service.CallerOverlayService.dismissOverlay(this)
    }

    override fun onPause() {
        super.onPause()
        ActiveCallManager.isInCallActivityVisible = false
    }

    override fun onDestroy() {
        ActiveCallManager.isInCallActivityVisible = false
        if (callRecorder.isRecording) {
            callRecorder.stopRecording()
        }
        try {
            val am = getSystemService(AUDIO_SERVICE) as? AudioManager
            am?.mode = AudioManager.MODE_NORMAL
            am?.isMicrophoneMute = false
        } catch (_: Exception) {}
        super.onDestroy()
    }
}

@Composable
fun InCallScreen(
    initialPhoneNumber: String,
    phoneNumberState: State<String>,
    initialIsIncoming: Boolean = false,
    callRecorder: CallRecorder,
    onAnswerCall: () -> Unit,
    onHangUp: () -> Unit,
    onCallEnded: (phone: String, talkSeconds: Long, wasMissed: Boolean) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val enquiryRepo = remember { EnquiryRepositoryProvider.get(context) }
    val contactsRepo = remember { ContactsRepository(context) }

    val resolvedInitial = if (initialPhoneNumber.isNotBlank() && initialPhoneNumber != "Unknown Number") {
        initialPhoneNumber
    } else {
        ActiveCallManager.getCallPhoneNumber(ActiveCallManager.currentCall).ifBlank {
            ActiveCallManager.lastIncomingNumber ?: initialPhoneNumber
        }
    }

    var currentPhone by remember { mutableStateOf(resolvedInitial) }
    var enquiry by remember { mutableStateOf<Enquiry?>(null) }
    var contactName by remember { mutableStateOf<String?>(null) }
    var isContactSaved by remember { mutableStateOf(false) }

    var showSaveContactDialog by remember { mutableStateOf(false) }
    var showSyncEnquiryDialog by remember { mutableStateOf(false) }
    var showAddCallDialog by remember { mutableStateOf(false) }
    var showTransferDialog by remember { mutableStateOf(false) }
    var ringSecondsElapsed by remember { mutableStateOf(0L) }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var isMuted by remember { mutableStateOf(ActiveCallManager.isMutedState || audioManager.isMicrophoneMute) }
    var isSpeakerOn by remember { mutableStateOf(ActiveCallManager.isSpeakerState || audioManager.isSpeakerphoneOn) }
    var isOnHold by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(callRecorder.isRecording) }
    var showInCallKeypad by remember { mutableStateOf(false) }
    var dtmfDigits by remember { mutableStateOf("") }

    // Multi-Call / Conference / Call Waiting State
    var callsCount by remember { mutableStateOf(ActiveCallManager.getCalls().size) }
    var heldCall by remember { mutableStateOf(ActiveCallManager.getHeldCall()) }
    var ringingCall by remember { mutableStateOf(ActiveCallManager.getRingingCall()) }
    var canMerge by remember { mutableStateOf(ActiveCallManager.canMerge()) }
    var isConference by remember { mutableStateOf(ActiveCallManager.isConference()) }

    // Track active call state
    var callState by remember {
        mutableStateOf(
            if (ActiveCallManager.currentCall != null) {
                ActiveCallManager.getCallState()
            } else if (initialIsIncoming) {
                Call.STATE_RINGING
            } else {
                Call.STATE_ACTIVE
            }
        )
    }
    var secondsElapsed by remember { mutableStateOf(0L) }
    var hasEverBeenAnswered by remember {
        mutableStateOf(!initialIsIncoming || ActiveCallManager.lastCallWasAnswered || (ActiveCallManager.currentCall?.state == Call.STATE_ACTIVE))
    }

    val isRinging = callState == Call.STATE_RINGING
    val isCallActive = callState == Call.STATE_ACTIVE
    val isOutgoing = callState == Call.STATE_DIALING || callState == Call.STATE_CONNECTING

    if (isCallActive) {
        hasEverBeenAnswered = true
        ActiveCallManager.lastCallWasAnswered = true
    }

    LaunchedEffect(phoneNumberState.value) {
        val num = phoneNumberState.value
        if (num.isNotBlank() && num != "Unknown Number" && num != currentPhone) {
            currentPhone = num
        }
    }

    DisposableEffect(Unit) {
        val dbHelper = EnquiryDatabaseHelper(context)
        var hasSavedMetrics = false
        fun saveCallMetrics() {
            if (hasSavedMetrics) return
            hasSavedMetrics = true
            if (currentPhone.isNotBlank() && currentPhone != "Unknown Number") {
                dbHelper.recordCallStats(
                    phoneNumber = currentPhone,
                    callType = if (initialIsIncoming && !hasEverBeenAnswered && secondsElapsed == 0L) "MISSED" else if (initialIsIncoming) "INCOMING" else "OUTGOING",
                    ringDurationSeconds = ringSecondsElapsed,
                    talkDurationSeconds = secondsElapsed
                )
            }
        }

        var isSessionEnded = false
        fun endCallSession() {
            if (isSessionEnded) return
            isSessionEnded = true
            ActiveCallManager.wasHandledByInCallService = true
            saveCallMetrics()
            val wasMissed = initialIsIncoming && !hasEverBeenAnswered && secondsElapsed == 0L
            onCallEnded(currentPhone, secondsElapsed, wasMissed)
        }

        val listener = {
            callsCount = ActiveCallManager.getCalls().size
            heldCall = ActiveCallManager.getHeldCall()
            ringingCall = ActiveCallManager.getRingingCall()
            canMerge = ActiveCallManager.canMerge()
            isConference = ActiveCallManager.isConference()
            isMuted = ActiveCallManager.isMutedState
            isSpeakerOn = ActiveCallManager.isSpeakerState
            val state = ActiveCallManager.getCallState()
            callState = state
            isOnHold = (state == Call.STATE_HOLDING || heldCall != null)

            val updatedPhone = ActiveCallManager.getCallPhoneNumber(ActiveCallManager.currentCall)
                .ifBlank { ActiveCallManager.lastIncomingNumber ?: "" }
            if (updatedPhone.isNotBlank() && updatedPhone != "Unknown Number" && updatedPhone != currentPhone) {
                currentPhone = updatedPhone
            }

            if (ActiveCallManager.getCalls().isEmpty() || state == Call.STATE_DISCONNECTED) {
                endCallSession()
            }
        }
        ActiveCallManager.addListener(listener)

        val call = ActiveCallManager.currentCall
        val callback = if (call != null) {
            callState = call.state
            if (call.state == Call.STATE_DISCONNECTED) {
                endCallSession()
            }
            object : Call.Callback() {
                override fun onStateChanged(call: Call, state: Int) {
                    callState = state
                    isOnHold = (state == Call.STATE_HOLDING || ActiveCallManager.getHeldCall() != null)
                    isMuted = ActiveCallManager.isMutedState
                    isSpeakerOn = ActiveCallManager.isSpeakerState
                    ActiveCallManager.notifyCallUpdated()
                    if (state == Call.STATE_DISCONNECTED || ActiveCallManager.getCalls().isEmpty()) {
                        endCallSession()
                    }
                }
            }.also { call.registerCallback(it) }
        } else {
            // Call was already cut before ringing or disconnected
            endCallSession()
            null
        }

        onDispose {
            ActiveCallManager.removeListener(listener)
            if (call != null && callback != null) {
                try {
                    call.unregisterCallback(callback)
                } catch (_: Exception) {}
            }
        }
    }

    LaunchedEffect(isCallActive) {
        if (isCallActive) {
            while (true) {
                delay(1000L)
                secondsElapsed++
            }
        }
    }

    LaunchedEffect(isRinging, isOutgoing) {
        if (isRinging || isOutgoing) {
            while (true) {
                delay(1000L)
                ringSecondsElapsed++
            }
        }
    }

    LaunchedEffect(isCallActive, currentPhone) {
        if (isCallActive && AppConfig.AUTO_CALL_RECORDING && !callRecorder.isRecording) {
            if (currentPhone.isNotBlank() && currentPhone != "Unknown Number") {
                val ok = callRecorder.startRecording(currentPhone)
                if (ok) {
                    isRecording = true
                    Toast.makeText(context, "Auto Call Recording started", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(currentPhone) {
        if (currentPhone.isNotBlank() && currentPhone != "Unknown Number") {
            enquiry = enquiryRepo.searchEnquiryByPhone(currentPhone)
            contactName = contactsRepo.getContactName(currentPhone)
            isContactSaved = (contactName != null) || contactsRepo.isContactSaved(currentPhone)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0F172A) // Samsung / Truecaller Dark Navy
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // TOP HEADER: Status & Caller Details
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 24.dp)
            ) {
                // Call Waiting Banner (if another call is ringing while on active call)
                if (ringingCall != null && (isCallActive || isOnHold)) {
                    val ringingNumber = ActiveCallManager.getCallPhoneNumber(ringingCall)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "CALL WAITING...",
                                    color = EmeraldGreen,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = PhoneNumberUtils.formatDisplay(ringingNumber),
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        ActiveCallManager.rejectRingingCall()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = CoralRed),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).height(36.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Icon(Icons.Default.CallEnd, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Decline", fontSize = 12.sp)
                                }
                                Button(
                                    onClick = {
                                        ActiveCallManager.holdAndAnswerRingingCall(context)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).height(36.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Hold & Answer", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                // Call on Hold Banner (with Swap & Merge actions)
                if (heldCall != null) {
                    val heldNumber = ActiveCallManager.getCallPhoneNumber(heldCall)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("ON HOLD", color = Color(0xFFF59E0B), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text(PhoneNumberUtils.formatDisplay(heldNumber), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilledTonalButton(
                                    onClick = { ActiveCallManager.swapCalls() },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.SwapCalls, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Swap", fontSize = 11.sp)
                                }
                                Button(
                                    onClick = {
                                        val merged = ActiveCallManager.mergeCalls()
                                        if (merged) {
                                            Toast.makeText(context, "Calls merged into conference", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.CallMerge, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Merge", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                // Call Status Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = when {
                        isConference -> Color(0xFF8B5CF6).copy(alpha = 0.2f)
                        isRinging -> EmeraldGreen.copy(alpha = 0.2f)
                        isCallActive -> TrueBlue.copy(alpha = 0.2f)
                        else -> Color(0xFF334155)
                    },
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Text(
                        text = when {
                            isConference -> "CONFERENCE (MERGED)"
                            isRinging -> "INCOMING CALL"
                            isCallActive -> "CALL IN PROGRESS"
                            isOutgoing -> "OUTGOING CALL"
                            else -> "CONNECTED"
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isConference -> Color(0xFFA78BFA)
                            isRinging -> EmeraldGreen
                            isCallActive -> Color(0xFF60A5FA)
                            else -> Color(0xFF94A3B8)
                        },
                        letterSpacing = 1.2.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }

                // Contact Name or Enquiry Name or Formatted Phone
                val displayName = contactName
                    ?: enquiry?.name
                    ?: if (currentPhone.isNotBlank() && currentPhone != "Unknown Number") PhoneNumberUtils.formatDisplay(currentPhone) else "Incoming Call"

                Text(
                    text = displayName,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Phone Number
                Text(
                    text = if (currentPhone.isNotBlank() && currentPhone != "Unknown Number") PhoneNumberUtils.formatDisplay(currentPhone) else "Unknown Number",
                    fontSize = 16.sp,
                    color = Color(0xFFCBD5E1)
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Real-Time Unsaved Number Identification ("both in database and in in call")
                if (enquiry == null && !isContactSaved && currentPhone.isNotBlank() && currentPhone != "Unknown Number") {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFD97706).copy(alpha = 0.25f),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PersonAdd,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "UNSAVED CALLER",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFF59E0B)
                            )
                        }
                    }

                    Text(
                        text = "Not in Contacts or Enquiry DB",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { showSaveContactDialog = true },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Save Contact", fontSize = 11.sp)
                        }

                        FilledTonalButton(
                            onClick = { showSyncEnquiryDialog = true },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add to Enquiry", fontSize = 11.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Enquiry Details Card
                if (enquiry != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        EnquiryBadge(status = enquiry!!.status)
                        if (!enquiry?.company.isNullOrBlank()) {
                            Text(
                                text = enquiry!!.company!!,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    if (!enquiry?.enquiryType.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Requirement: ${enquiry!!.enquiryType}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF38BDF8)
                        )
                    }

                    if (!enquiry?.notes.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = enquiry!!.notes!!,
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8),
                            maxLines = 2
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Call Duration Timer (only once answered)
                if (isCallActive) {
                    Text(
                        text = CallRecordingUtils.formatStopwatch(secondsElapsed),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                } else if (isRinging) {
                    Text(
                        text = "Ringing...",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = EmeraldGreen
                    )
                } else {
                    Text(
                        text = "Calling...",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF94A3B8)
                    )
                }

                // Recording Indicator
                if (isRecording) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(CoralRed, CircleShape)
                        )
                        Text(
                            text = "REC",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = CoralRed
                        )
                    }
                }
            }

            // MIDDLE SECTION: IN-CALL ACTION BUTTONS (Mute, Speaker, Keypad, Hold, Record, WhatsApp)
            // Visible once call is connected or outgoing
            AnimatedVisibility(
                visible = !isRinging,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                if (showInCallKeypad) {
                    InCallKeypadView(
                        dialedDigits = dtmfDigits,
                        onDigitClick = { digit ->
                            dtmfDigits += digit
                            val call = ActiveCallManager.currentCall
                            if (call != null && digit.isNotBlank()) {
                                try {
                                    call.playDtmfTone(digit.first())
                                    coroutineScope.launch {
                                        delay(150L)
                                        call.stopDtmfTone()
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        },
                        onClearClick = {
                            if (dtmfDigits.isNotEmpty()) {
                                dtmfDigits = dtmfDigits.dropLast(1)
                            }
                        },
                        onHideKeypad = {
                            showInCallKeypad = false
                        }
                    )
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Row 1: Mute, Speaker, Keypad
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            InCallActionButton(
                                icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                label = if (isMuted) "Unmute" else "Mute",
                                isActive = isMuted,
                                activeColor = CoralRed,
                                onClick = {
                                    isMuted = ActiveCallManager.toggleMute(context)
                                }
                            )

                            InCallActionButton(
                                icon = if (isSpeakerOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeMute,
                                label = "Speaker",
                                isActive = isSpeakerOn,
                                activeColor = TrueBlue,
                                onClick = {
                                    isSpeakerOn = ActiveCallManager.toggleSpeaker(context)
                                }
                            )

                            InCallActionButton(
                                icon = Icons.Default.Dialpad,
                                label = "Keypad",
                                isActive = showInCallKeypad,
                                onClick = {
                                    showInCallKeypad = !showInCallKeypad
                                }
                            )
                        }

                        // Row 2: Hold, Record, WhatsApp
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            InCallActionButton(
                                icon = if (isOnHold) Icons.Default.PlayArrow else Icons.Default.Pause,
                                label = if (isOnHold) "Resume" else "Hold",
                                isActive = isOnHold,
                                onClick = {
                                    val call = ActiveCallManager.currentCall
                                    if (isOnHold) {
                                        call?.unhold()
                                        isOnHold = false
                                    } else {
                                        call?.hold()
                                        isOnHold = true
                                    }
                                }
                            )

                            InCallActionButton(
                                icon = Icons.Default.FiberManualRecord,
                                label = if (isRecording) "Stop Rec" else "Record",
                                isActive = isRecording,
                                activeColor = CoralRed,
                                onClick = {
                                    if (!isRecording) {
                                        val success = callRecorder.startRecording(currentPhone)
                                        if (success) {
                                            isRecording = true
                                            Toast.makeText(context, "Call recording started", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Recording failed (Check Mic permission)", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        val file = callRecorder.stopRecording()
                                        isRecording = false
                                        Toast.makeText(context, "Saved to ${file?.name}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            )

                            InCallActionButton(
                                icon = Icons.AutoMirrored.Filled.Send,
                                label = "WhatsApp",
                                isActive = false,
                                activeColor = EmeraldGreen,
                                onClick = {
                                    val template = AppConfig.WHATSAPP_TEMPLATE.ifBlank { AppConfig.DEFAULT_WHATSAPP_TEMPLATE }
                                    val formattedMessage = WhatsAppBusinessHelper.fillTemplate(template, enquiry, currentPhone)
                                    Toast.makeText(context, "WhatsApp: \"$formattedMessage\"", Toast.LENGTH_SHORT).show()
                                    WhatsAppBusinessHelper.sendWhatsAppBusinessMessage(
                                        context,
                                        currentPhone,
                                        formattedMessage
                                    )
                                }
                            )
                        }

                        // Row 3: Add Call, Transfer, and conditional Swap & Merge (only visible with multiple calls)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            InCallActionButton(
                                icon = Icons.Default.Add,
                                label = "Add call",
                                isActive = false,
                                activeColor = EmeraldGreen,
                                onClick = {
                                    showAddCallDialog = true
                                }
                            )

                            InCallActionButton(
                                icon = Icons.Default.PhoneForwarded,
                                label = "Transfer",
                                isActive = false,
                                activeColor = TrueBlue,
                                onClick = {
                                    showTransferDialog = true
                                }
                            )

                            // Swap key: only visible after adding multiple calls or when a call is on hold
                            if (callsCount > 1 || heldCall != null) {
                                InCallActionButton(
                                    icon = Icons.Default.SwapCalls,
                                    label = "Swap",
                                    isActive = heldCall != null,
                                    onClick = {
                                        val ok = ActiveCallManager.swapCalls()
                                        if (!ok) {
                                            Toast.makeText(context, "No held call to swap with", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }

                            // Merge key: only visible after adding multiple calls or conference possible
                            if (callsCount > 1 || canMerge || isConference) {
                                InCallActionButton(
                                    icon = Icons.Default.CallMerge,
                                    label = "Merge",
                                    isActive = isConference || canMerge,
                                    activeColor = EmeraldGreen,
                                    onClick = {
                                        val ok = ActiveCallManager.mergeCalls()
                                        if (ok) {
                                            Toast.makeText(context, "Calls merged into conference", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Merge requires at least 2 active/held calls", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // BOTTOM BAR: ANSWER vs DECLINE / END CALL BUTTONS
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isRinging) {
                    // INCOMING CALL: TWO LARGE BUTTONS (DECLINE RED + ANSWER GREEN)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Decline Button (Red)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FloatingActionButton(
                                onClick = onHangUp,
                                containerColor = CoralRed,
                                contentColor = Color.White,
                                shape = CircleShape,
                                modifier = Modifier.size(76.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CallEnd,
                                    contentDescription = "Decline",
                                    modifier = Modifier.size(38.dp)
                                )
                            }
                            Text(
                                text = "Decline",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }

                        // Answer Button (Green)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FloatingActionButton(
                                onClick = {
                                    onAnswerCall()
                                    callState = Call.STATE_ACTIVE
                                },
                                containerColor = EmeraldGreen,
                                contentColor = Color.White,
                                shape = CircleShape,
                                modifier = Modifier.size(76.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Call,
                                    contentDescription = "Answer",
                                    modifier = Modifier.size(38.dp)
                                )
                            }
                            Text(
                                text = "Answer",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                } else {
                    // CONNECTED OR OUTGOING: SINGLE RED END CALL BUTTON
                    FloatingActionButton(
                        onClick = onHangUp,
                        containerColor = CoralRed,
                        contentColor = Color.White,
                        shape = CircleShape,
                        modifier = Modifier.size(72.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = "End Call",
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }
        }

        // Save Contact In-Call Dialog
        if (showSaveContactDialog) {
            SaveContactDialog(
                initialPhoneNumber = currentPhone,
                initialEnquiry = enquiry,
                onDismiss = { showSaveContactDialog = false },
                onSaveDirectly = { name, phone, comp, em ->
                    coroutineScope.launch {
                        val success = contactsRepo.saveContactDirectly(name, phone, comp, em)
                        if (success) {
                            isContactSaved = true
                            Toast.makeText(context, "Saved $name to contacts & synced to database!", Toast.LENGTH_SHORT).show()
                            showSaveContactDialog = false
                        } else {
                            context.startActivity(contactsRepo.createSaveContactIntent(name, phone, comp, em))
                            showSaveContactDialog = false
                        }
                    }
                },
                onOpenSystemEditor = { name, phone, comp, em ->
                    context.startActivity(contactsRepo.createSaveContactIntent(name, phone, comp, em))
                    showSaveContactDialog = false
                }
            )
        }

        // Add to Enquiry In-Call Dialog
        if (showSyncEnquiryDialog) {
            SyncEnquiryDialog(
                initialPhoneNumber = currentPhone,
                initialName = enquiry?.name,
                onDismiss = { showSyncEnquiryDialog = false },
                onSync = { newEnq ->
                    coroutineScope.launch {
                        val saved = enquiryRepo.saveEnquiry(newEnq)
                        if (saved) {
                            enquiry = newEnq
                            Toast.makeText(context, "Added to Enquiry Database!", Toast.LENGTH_SHORT).show()
                            showSyncEnquiryDialog = false
                        }
                    }
                }
            )
        }

        // Add Call Dialog
        if (showAddCallDialog) {
            AddCallDialog(
                onDismiss = { showAddCallDialog = false },
                onCall = { targetNumber ->
                    showAddCallDialog = false
                    val clean = PhoneNumberUtils.normalize(targetNumber)
                    val uri = Uri.parse("tel:$clean")
                    val intent = Intent(Intent.ACTION_CALL, uri).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Could not place call: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        // Transfer Call Dialog (Feature 10)
        if (showTransferDialog) {
            TransferCallDialog(
                currentPhoneNumber = currentPhone,
                enquiry = enquiry,
                onDismiss = { showTransferDialog = false },
                onDeflectTransfer = { targetNumber ->
                    showTransferDialog = false
                    val ok = ActiveCallManager.transferCall(targetNumber)
                    if (ok) {
                        Toast.makeText(context, "Call deflected/transferred to $targetNumber", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Direct deflection unavailable. Calling staff...", Toast.LENGTH_SHORT).show()
                        val clean = PhoneNumberUtils.normalize(targetNumber)
                        val uri = Uri.parse("tel:$clean")
                        val intent = Intent(Intent.ACTION_CALL, uri).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not call: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onConsultativeTransfer = { targetNumber ->
                    showTransferDialog = false
                    val clean = PhoneNumberUtils.normalize(targetNumber)
                    val uri = Uri.parse("tel:$clean")
                    val intent = Intent(Intent.ACTION_CALL, uri).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Could not call: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                },
                onReassignLead = { staff ->
                    showTransferDialog = false
                    coroutineScope.launch {
                        val current = enquiry ?: Enquiry(
                            id = "ENQ-${System.currentTimeMillis() % 100000}",
                            name = contactName ?: "Lead",
                            phoneNumber = currentPhone,
                            assignedTo = staff.name
                        )
                        val updated = current.copy(assignedTo = staff.name)
                        enquiryRepo.saveEnquiry(updated)
                        com.dialer.truedialer.sync.CrmWebhookSync.syncLead(updated)
                        enquiry = updated
                        Toast.makeText(context, "Lead reassigned to ${staff.name}", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }
}

@Composable
fun InCallActionButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    activeColor: Color = TrueBlue,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        FilledIconButton(
            onClick = onClick,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (isActive) activeColor else Color(0xFF334155)
            ),
            modifier = Modifier.size(60.dp),
            shape = CircleShape
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
        }
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color(0xFFCBD5E1),
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun AddCallDialog(
    onDismiss: () -> Unit,
    onCall: (String) -> Unit
) {
    val context = LocalContext.current
    val contactsRepo = remember { ContactsRepository(context) }
    var numberInput by remember { mutableStateOf("") }
    var matchingContacts by remember { mutableStateOf<List<ContactRecord>>(emptyList()) }

    LaunchedEffect(numberInput) {
        matchingContacts = if (numberInput.isNotBlank()) {
            contactsRepo.getContacts(numberInput).take(4)
        } else {
            contactsRepo.getContacts("").take(4)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Add Call", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                OutlinedTextField(
                    value = numberInput,
                    onValueChange = { numberInput = it },
                    label = { Text("Phone number or name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (matchingContacts.isNotEmpty()) {
                    Text(
                        "SUGGESTED CONTACTS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        matchingContacts.forEach { contact ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onCall(contact.phoneNumber) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(contact.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text(PhoneNumberUtils.formatDisplay(contact.phoneNumber), fontSize = 12.sp, color = TrueBlue)
                                    }
                                    Icon(Icons.Default.Call, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        if (numberInput.isNotBlank()) {
                            onCall(numberInput)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Call Now")
                }
            }
        }
    }
}

@Composable
fun TransferCallDialog(
    currentPhoneNumber: String,
    enquiry: Enquiry?,
    onDismiss: () -> Unit,
    onDeflectTransfer: (String) -> Unit,
    onConsultativeTransfer: (String) -> Unit,
    onReassignLead: (CenterUser) -> Unit
) {
    val context = LocalContext.current
    val dbHelper = remember { EnquiryDatabaseHelper(context) }
    var staffList by remember { mutableStateOf<List<CenterUser>>(emptyList()) }
    var customNumber by remember { mutableStateOf("") }
    var selectedStaff by remember { mutableStateOf<CenterUser?>(null) }

    LaunchedEffect(Unit) {
        staffList = dbHelper.getCenterUsers().filter { it.name != AppConfig.CURRENT_USER_NAME }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Transfer Call", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = PhoneNumberUtils.formatDisplay(currentPhoneNumber),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Text(
                    text = "SELECT TEAM MEMBER",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                if (staffList.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        staffList.forEach { staff ->
                            val isSelected = selectedStaff == staff
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedStaff = staff
                                        customNumber = staff.phone
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(Icons.Default.Person, contentDescription = null, tint = TrueBlue)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(staff.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("${staff.role.displayName} • ${PhoneNumberUtils.formatDisplay(staff.phone)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (isSelected) {
                                        Icon(Icons.Default.Check, contentDescription = "Selected", tint = EmeraldGreen)
                                    }
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = customNumber,
                    onValueChange = { customNumber = it },
                    label = { Text("Or enter phone number / extension") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Action 1: Blind / Direct Transfer
                Button(
                    onClick = {
                        val num = customNumber.ifBlank { selectedStaff?.phone ?: "" }
                        if (num.isNotBlank()) {
                            onDeflectTransfer(num)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TrueBlue),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PhoneForwarded, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Transfer Call (Direct / Deflect)")
                }

                // Action 2: Consultative Transfer
                OutlinedButton(
                    onClick = {
                        val num = customNumber.ifBlank { selectedStaff?.phone ?: "" }
                        if (num.isNotBlank()) {
                            onConsultativeTransfer(num)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Consult & Transfer (Hold & Dial)")
                }

                // Action 3: Reassign CRM Lead Ownership
                if (selectedStaff != null) {
                    TextButton(
                        onClick = {
                            selectedStaff?.let { onReassignLead(it) }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reassign Lead Ownership to ${selectedStaff!!.name}")
                    }
                }
            }
        }
    }
}

data class KeypadKey(val digit: String, val letters: String)

@Composable
fun InCallKeypadView(
    dialedDigits: String,
    onDigitClick: (String) -> Unit,
    onClearClick: () -> Unit,
    onHideKeypad: () -> Unit
) {
    val keys = listOf(
        listOf(KeypadKey("1", ""), KeypadKey("2", "ABC"), KeypadKey("3", "DEF")),
        listOf(KeypadKey("4", "GHI"), KeypadKey("5", "JKL"), KeypadKey("6", "MNO")),
        listOf(KeypadKey("7", "PQRS"), KeypadKey("8", "TUV"), KeypadKey("9", "WXYZ")),
        listOf(KeypadKey("*", ""), KeypadKey("0", "+"), KeypadKey("#", ""))
    )

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Dialed digits display row
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF0F172A),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (dialedDigits.isEmpty()) "DTMF Touchpad" else dialedDigits,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (dialedDigits.isEmpty()) Color.Gray else Color.White
                    )
                    if (dialedDigits.isNotEmpty()) {
                        IconButton(onClick = onClearClick, modifier = Modifier.size(24.dp)) {
                            Icon(
                                Icons.AutoMirrored.Filled.Backspace,
                                contentDescription = "Backspace",
                                tint = Color.LightGray,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Keypad Grid: 4 rows x 3 columns
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                for (row in keys) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (item in row) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF334155),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .clickable { onDigitClick(item.digit) }
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = item.digit,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    if (item.letters.isNotBlank()) {
                                        Text(
                                            text = item.letters,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = onHideKeypad,
                colors = ButtonDefaults.buttonColors(containerColor = TrueBlue),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
            ) {
                Icon(Icons.Default.Dialpad, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Hide Keypad", fontSize = 13.sp)
            }
        }
    }
}
