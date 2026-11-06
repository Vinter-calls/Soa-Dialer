package com.dialer.truedialer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import com.dialer.truedialer.AppConfig
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.dialer.truedialer.R
import com.dialer.truedialer.data.local.EnquiryDatabaseHelper
import com.dialer.truedialer.data.model.Enquiry
import com.dialer.truedialer.data.repository.ContactsRepository
import com.dialer.truedialer.data.repository.EnquiryRepositoryProvider
import com.dialer.truedialer.ui.theme.CoralRed
import com.dialer.truedialer.ui.theme.EmeraldGreen
import com.dialer.truedialer.util.PhoneNumberUtils
import com.dialer.truedialer.util.WhatsAppBusinessHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class CallerOverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    companion object {
        const val EXTRA_PHONE_NUMBER = "extra_phone_number"
        const val ACTION_SHOW = "action_show"
        const val ACTION_DISMISS = "action_dismiss"
        private const val CHANNEL_ID = "caller_id_overlay_channel"
        private const val NOTIFICATION_ID = 2001

        private var activeOverlayView: ComposeView? = null
        private var isOverlayShowing = false
        private val mainHandler = Handler(Looper.getMainLooper())

        fun showOverlay(context: Context, phoneNumber: String) {
            if (ActiveCallManager.isInCallActivityVisible) {
                // InCallActivity is visible; do not spawn floating overlay
                return
            }
            try {
                val intent = Intent(context, CallerOverlayService::class.java).apply {
                    action = ACTION_SHOW
                    putExtra(EXTRA_PHONE_NUMBER, phoneNumber)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun dismissOverlay(context: Context) {
            mainHandler.post {
                try {
                    val view = activeOverlayView
                    if (view != null) {
                        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                        try {
                            wm?.removeViewImmediate(view)
                        } catch (e: Exception) {
                            wm?.removeView(view)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    activeOverlayView = null
                    isOverlayShowing = false
                }
            }

            try {
                val intent = Intent(context, CallerOverlayService::class.java)
                context.stopService(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildForegroundNotification())

        val action = intent?.action
        if (action == ACTION_DISMISS) {
            removeOverlay()
            stopSelf()
            return START_NOT_STICKY
        }

        if (ActiveCallManager.isInCallActivityVisible) {
            removeOverlay()
            stopSelf()
            return START_NOT_STICKY
        }

        val incomingExtra = intent?.getStringExtra(EXTRA_PHONE_NUMBER) ?: ""
        if (incomingExtra.isNotBlank() && incomingExtra != "Unknown Number") {
            ActiveCallManager.lastIncomingNumber = incomingExtra
        }
        val resolvedNum = if (incomingExtra.isNotBlank() && incomingExtra != "Unknown Number") {
            incomingExtra
        } else {
            ActiveCallManager.getCallPhoneNumber(ActiveCallManager.currentCall).ifBlank {
                ActiveCallManager.lastIncomingNumber ?: incomingExtra
            }
        }

        displayOverlay(resolvedNum)

        return START_NOT_STICKY
    }

    private fun displayOverlay(phoneNumber: String) {
        if (ActiveCallManager.isInCallActivityVisible) {
            removeOverlay()
            stopSelf()
            return
        }

        // Deduplication: If overlay is already active, DO NOT call windowManager.addView() again!
        if (isOverlayShowing && activeOverlayView != null) {
            if (phoneNumber.isNotBlank() && phoneNumber != "Unknown Number") {
                ActiveCallManager.lastIncomingNumber = phoneNumber
                ActiveCallManager.notifyCallUpdated()
            }
            return
        }

        removeOverlay()

        val contactsRepo = ContactsRepository(applicationContext)
        val enquiryRepo = EnquiryRepositoryProvider.get(applicationContext)
        val dbHelper = EnquiryDatabaseHelper(applicationContext)

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            val density = applicationContext.resources.displayMetrics.density
            y = (60 * density).toInt()
        }

        overlayView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@CallerOverlayService)
            setViewTreeSavedStateRegistryOwner(this@CallerOverlayService)

            setContent {
                val resolvedInitialPhone = if (phoneNumber.isNotBlank() && phoneNumber != "Unknown Number") {
                    phoneNumber
                } else {
                    ActiveCallManager.getCallPhoneNumber(ActiveCallManager.currentCall).ifBlank {
                        ActiveCallManager.lastIncomingNumber ?: phoneNumber
                    }
                }

                var currentPhone by remember { mutableStateOf(resolvedInitialPhone) }
                var enquiry by remember { mutableStateOf<Enquiry?>(null) }
                var contactName by remember { mutableStateOf<String?>(null) }
                var isSavedInContacts by remember { mutableStateOf(false) }
                var isSavedSuccess by remember { mutableStateOf(false) }
                var isSpam by remember { mutableStateOf(false) }

                DisposableEffect(Unit) {
                    val listener = {
                        val updated = ActiveCallManager.getCallPhoneNumber(ActiveCallManager.currentCall)
                            .ifBlank { ActiveCallManager.lastIncomingNumber ?: "" }
                        if (updated.isNotBlank() && updated != currentPhone && updated != "Unknown Number") {
                            currentPhone = updated
                        }
                    }
                    ActiveCallManager.addListener(listener)
                    onDispose {
                        ActiveCallManager.removeListener(listener)
                    }
                }

                LaunchedEffect(currentPhone) {
                    if (currentPhone.isNotBlank() && currentPhone != "Unknown Number") {
                        enquiry = enquiryRepo.searchEnquiryByPhone(currentPhone)
                        contactName = contactsRepo.getContactName(currentPhone)
                        isSavedInContacts = (contactName != null) || contactsRepo.isContactSaved(currentPhone)
                        isSpam = dbHelper.isNumberSpam(currentPhone)
                    }
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSpam) Color(0xFF450A0A) else Color(0xFF1E293B),
                    shadowElevation = 10.dp
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(if (isSpam) CoralRed else Color(0xFF0066FF), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isSpam) Icons.Default.Block else Icons.Default.Phone,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isSpam) "SUSPECTED SPAM CALLER" else "ENQUIRY CALLER ID",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSpam) CoralRed else Color(0xFF94A3B8)
                                )
                            }

                            IconButton(
                                onClick = {
                                    removeOverlay()
                                    stopSelf()
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color.LightGray
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Identified Contact Name or Enquiry or Unsaved Caller
                        val displayName = contactName
                            ?: enquiry?.name
                            ?: if (isSavedInContacts) "Saved Contact" else if (currentPhone.isNotBlank() && currentPhone != "Unknown Number") "Unsaved Caller" else "Incoming Call"

                        Text(
                            text = displayName,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )

                        Text(
                            text = if (currentPhone.isNotBlank() && currentPhone != "Unknown Number") PhoneNumberUtils.formatDisplay(currentPhone) else "Incoming Call...",
                            fontSize = 14.sp,
                            color = Color(0xFFCBD5E1)
                        )

                        // Enquiry details chip
                        if (enquiry != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                enquiry?.company?.let {
                                    SuggestionChip(
                                        onClick = {},
                                        label = { Text(it, color = Color.White, fontSize = 12.sp) },
                                        colors = SuggestionChipDefaults.suggestionChipColors(containerColor = Color(0xFF334155))
                                    )
                                }
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text(enquiry!!.status, color = Color.White, fontSize = 12.sp) },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = when (enquiry!!.status) {
                                            "Enrolled", "Converted" -> Color(0xFF16A34A)
                                            "Follow-up" -> Color(0xFFD97706)
                                            "Spam" -> CoralRed
                                            else -> Color(0xFF2563EB)
                                        }
                                    )
                                )
                            }

                            enquiry?.enquiryType?.let {
                                Text(
                                    text = "Interest: $it",
                                    fontSize = 13.sp,
                                    color = Color(0xFF38BDF8),
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            enquiry?.notes?.let {
                                Text(
                                    text = "Note: $it",
                                    fontSize = 12.sp,
                                    color = Color(0xFF94A3B8),
                                    maxLines = 2
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Primary Incoming Call Action Buttons: Decline (Red) & Answer (Green)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    ActiveCallManager.rejectCall(applicationContext)
                                    removeOverlay()
                                    stopSelf()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CoralRed),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CallEnd,
                                    contentDescription = "Decline Call",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Decline",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }

                            Button(
                                onClick = {
                                    ActiveCallManager.answerCall(applicationContext)
                                    removeOverlay()
                                    stopSelf()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Call,
                                    contentDescription = "Answer Call",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Answer",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }

                        // Secondary Actions Row: Save Contact + WhatsApp Business + Mark Spam
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!isSavedInContacts && !isSavedSuccess) {
                                Button(
                                    onClick = {
                                        serviceScope.launch {
                                            val nameToSave = enquiry?.name ?: "Enquiry Caller"
                                            val intent = contactsRepo.createSaveContactIntent(
                                                name = nameToSave,
                                                phoneNumber = currentPhone,
                                                company = enquiry?.company,
                                                email = enquiry?.email
                                            )
                                            applicationContext.startActivity(intent)
                                            isSavedSuccess = true
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PersonAdd,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Save Contact", fontSize = 12.sp)
                                }
                            }

                            // Quick WhatsApp Business Button (Feature 2)
                            FilledIconButton(
                                onClick = {
                                    val template = AppConfig.WHATSAPP_CALL_TEMPLATE.ifBlank { AppConfig.DEFAULT_WHATSAPP_CALL_TEMPLATE }
                                    val message = WhatsAppBusinessHelper.fillTemplate(template, enquiry, currentPhone)
                                    WhatsAppBusinessHelper.sendWhatsAppBusinessMessage(
                                        applicationContext,
                                        currentPhone,
                                        message
                                    )
                                },
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = EmeraldGreen),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(Icons.Default.Send, contentDescription = "WhatsApp", tint = Color.White, modifier = Modifier.size(18.dp))
                            }

                            // Mark Spam Toggle (Feature 6)
                            FilledIconButton(
                                onClick = {
                                    val newSpamState = !isSpam
                                    dbHelper.markAsSpam(currentPhone, newSpamState)
                                    isSpam = newSpamState
                                },
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = if (isSpam) CoralRed else Color(0xFF475569)),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(Icons.Default.Block, contentDescription = "Spam", tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }

        val newView = overlayView ?: return
        activeOverlayView = newView
        isOverlayShowing = true

        try {
            windowManager?.addView(newView, layoutParams)
        } catch (e: Exception) {
            e.printStackTrace()
            activeOverlayView = null
            isOverlayShowing = false
        }
    }

    private fun removeOverlay() {
        try {
            val viewToRemove = activeOverlayView ?: overlayView
            if (viewToRemove != null) {
                windowManager?.removeViewImmediate(viewToRemove)
            }
        } catch (e: Exception) {
            try {
                val fallbackView = activeOverlayView ?: overlayView
                if (fallbackView != null) {
                    windowManager?.removeView(fallbackView)
                }
            } catch (_: Exception) {}
        } finally {
            overlayView = null
            activeOverlayView = null
            isOverlayShowing = false
        }
    }

    private fun buildForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("True Enquiry Dialer Active")
            .setContentText("Screening and identifying calls...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Truecaller Enquiry Caller ID",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        removeOverlay()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
