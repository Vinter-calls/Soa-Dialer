package com.dialer.truedialer.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dialer.truedialer.AppConfig
import com.dialer.truedialer.data.local.EnquiryDatabaseHelper
import com.dialer.truedialer.data.model.MessageTemplate
import com.dialer.truedialer.service.CallerOverlayService
import com.dialer.truedialer.sync.CrmWebhookSync
import com.dialer.truedialer.sync.MongoAtlasSyncManager
import com.dialer.truedialer.ui.activities.PostCallActivity
import com.dialer.truedialer.ui.components.UserProfileBadge
import com.dialer.truedialer.ui.components.UserSwitcherDialog
import com.dialer.truedialer.ui.theme.AmberOrange
import com.dialer.truedialer.ui.theme.CoralRed
import com.dialer.truedialer.ui.theme.EmeraldGreen
import com.dialer.truedialer.ui.theme.TrueBlue
import com.dialer.truedialer.util.PermissionUtils
import com.dialer.truedialer.util.WhatsAppBusinessHelper
import kotlinx.coroutines.launch

data class CenterOption(val id: String, val name: String)

val AVAILABLE_CENTERS = listOf(
    CenterOption("67cd080a040cb823ea6c0744", "SOA-Whitefield"),
    CenterOption("67c00eb2073609b23054ca01", "SOA-Brookefield"),
    CenterOption("6a70cad580c43932de3f7c8d", "SOA-Store")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToPipeline: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val coroutineScope = rememberCoroutineScope()
    val dbHelper = remember { EnquiryDatabaseHelper(context) }

    // 1. User Profile State
    var showUserSwitcher by remember { mutableStateOf(false) }
    var activeUserName by remember { mutableStateOf(AppConfig.CURRENT_USER_NAME) }
    var activeUserRole by remember { mutableStateOf(AppConfig.CURRENT_USER_ROLE) }
    var selectedCenter by remember {
        mutableStateOf(
            AVAILABLE_CENTERS.firstOrNull { it.name.equals(AppConfig.CENTER_NAME, ignoreCase = true) || it.id == AppConfig.CENTER_ID }
                ?: CenterOption(AppConfig.CENTER_ID, AppConfig.CENTER_NAME)
        )
    }
    var centerDropdownExpanded by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }

    // 2. Notification Preferences
    var notifIncoming by remember { mutableStateOf(AppConfig.NOTIF_INCOMING_CALL) }
    var notifMissed by remember { mutableStateOf(AppConfig.NOTIF_MISSED_CALL) }
    var notifFollowUps by remember { mutableStateOf(AppConfig.NOTIF_FOLLOW_UPS) }
    var notifDailyTasks by remember { mutableStateOf(AppConfig.NOTIF_DAILY_TASKS) }

    // 3. Theme Selection
    var themeMode by remember { mutableStateOf(AppConfig.THEME_MODE) }

    // 4. Contact & Database Synchronisation
    var autoSyncMongo by remember { mutableStateOf(AppConfig.AUTO_SYNC_MONGODB) }
    var isSyncingMongo by remember { mutableStateOf(false) }
    var mongoSyncStatus by remember { mutableStateOf<String?>(null) }

    // 5. Call Recording Settings
    var autoCallRecording by remember { mutableStateOf(AppConfig.AUTO_CALL_RECORDING) }

    // 6. WhatsApp Message Template
    var whatsAppTemplate by remember { mutableStateOf(AppConfig.WHATSAPP_TEMPLATE) }

    // 7. CRM Webhook & Mock DB
    var useMockDb by remember { mutableStateOf(AppConfig.USE_MOCK_DATABASE) }
    var apiUrl by remember { mutableStateOf(AppConfig.BACKEND_API_BASE_URL) }
    var apiKey by remember { mutableStateOf(AppConfig.BACKEND_API_KEY) }
    var crmWebhookUrl by remember { mutableStateOf(AppConfig.CRM_WEBHOOK_URL) }
    var crmToken by remember { mutableStateOf(AppConfig.CRM_WEBHOOK_BEARER_TOKEN) }
    var webhookTestStatus by remember { mutableStateOf<String?>(null) }
    var isTestingWebhook by remember { mutableStateOf(false) }
    var autoRejectSpam by remember { mutableStateOf(AppConfig.AUTO_REJECT_SPAM) }
    var showPostCallDialog by remember { mutableStateOf(AppConfig.SHOW_POST_CALL_DIALOG) }

    val isDefaultDialer = remember { PermissionUtils.isDefaultDialer(context) }
    val hasOverlayPermission = remember { PermissionUtils.canDrawOverlays(context) }
    val isWABusinessInstalled = remember { WhatsAppBusinessHelper.isWhatsAppBusinessInstalled(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Settings", fontWeight = FontWeight.Bold) },
                actions = {
                    UserProfileBadge(onClick = { showUserSwitcher = true })
                }
            )
        },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ==========================================
            // 1. USER PROFILE MANAGEMENT
            // ==========================================
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Surface(shape = CircleShape, color = TrueBlue, modifier = Modifier.size(40.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.AccountCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                                }
                            }
                            Column {
                                Text("User Profile & Center", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text("Center: ${selectedCenter.name}", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                            }
                        }

                        Button(
                            onClick = { showUserSwitcher = true },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.SwitchAccount, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Switch User", fontSize = 12.sp)
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Logged In Employee:", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                Text(
                                    "$activeUserName (${if (activeUserRole == "MANAGER") "Center Manager" else "Staff / Telecaller"})",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (activeUserRole == "MANAGER") EmeraldGreen else TrueBlue
                                )
                            }
                            Text(
                                "ID: ${AppConfig.CURRENT_USER_ID}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    if (AppConfig.isCurrentUserManager) {
                        Text("Center Configuration (Manager Only):", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)

                        ExposedDropdownMenuBox(
                            expanded = centerDropdownExpanded,
                            onExpandedChange = { centerDropdownExpanded = it },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = selectedCenter.name,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Center Name") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = centerDropdownExpanded) },
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = centerDropdownExpanded,
                                onDismissRequest = { centerDropdownExpanded = false }
                            ) {
                                AVAILABLE_CENTERS.forEach { center ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = center.name,
                                                fontWeight = if (center.name == selectedCenter.name) FontWeight.Bold else FontWeight.Normal,
                                                color = if (center.name == selectedCenter.name) TrueBlue else MaterialTheme.colorScheme.onSurface
                                            )
                                        },
                                        onClick = {
                                            selectedCenter = center
                                            centerDropdownExpanded = false
                                            AppConfig.setCenterConfig(context, center.id, center.name)
                                            Toast.makeText(context, "Center set to ${center.name}", Toast.LENGTH_SHORT).show()
                                        },
                                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ==========================================
            // 2. NOTIFICATION PREFERENCES
            // ==========================================
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Notifications, contentDescription = null, tint = TrueBlue)
                        Text("Notification Preferences", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Incoming Call Caller-ID Popup", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text("Floating customer banner on incoming call", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                        }
                        Switch(
                            checked = notifIncoming,
                            onCheckedChange = {
                                notifIncoming = it
                                AppConfig.setNotificationPreferences(context, it, notifMissed, notifFollowUps, notifDailyTasks)
                            }
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Missed Call Instant Notifications", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text("Callback & WhatsApp quick actions for missed calls", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                        }
                        Switch(
                            checked = notifMissed,
                            onCheckedChange = {
                                notifMissed = it
                                AppConfig.setNotificationPreferences(context, notifIncoming, it, notifFollowUps, notifDailyTasks)
                            }
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Follow-Up & Reminder Alarms", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text("Remind employee at scheduled follow-up time", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                        }
                        Switch(
                            checked = notifFollowUps,
                            onCheckedChange = {
                                notifFollowUps = it
                                AppConfig.setNotificationPreferences(context, notifIncoming, notifMissed, it, notifDailyTasks)
                            }
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Daily Pending Tasks & Demo Alerts", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text("Summary alerts for today's pending calls and demos", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                        }
                        Switch(
                            checked = notifDailyTasks,
                            onCheckedChange = {
                                notifDailyTasks = it
                                AppConfig.setNotificationPreferences(context, notifIncoming, notifMissed, notifFollowUps, it)
                            }
                        )
                    }
                }
            }

            // ==========================================
            // 3. THEME SELECTION
            // ==========================================
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Palette, contentDescription = null, tint = TrueBlue)
                        Text("Theme Selection", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("SYSTEM" to "System Default", "LIGHT" to "Light", "DARK" to "Dark").forEach { (mode, label) ->
                            FilterChip(
                                selected = themeMode == mode,
                                onClick = {
                                    themeMode = mode
                                    AppConfig.setThemeMode(context, mode)
                                    Toast.makeText(context, "Theme set to $label", Toast.LENGTH_SHORT).show()
                                },
                                label = { Text(label, fontSize = 12.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // ==========================================
            // 4. CONTACT & DATABASE SYNCHRONISATION
            // ==========================================
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Contact & Lead Synchronisation", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("MongoDB Atlas • aethens.enquiries database", fontSize = 12.sp, color = EmeraldGreen, fontWeight = FontWeight.SemiBold)
                        }
                        Icon(Icons.Default.CloudSync, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(26.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-Sync Leads & Contacts", fontWeight = FontWeight.Medium)
                            Text("Automatically saves new leads and contact name updates to database", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                        }
                        Switch(
                            checked = autoSyncMongo,
                            onCheckedChange = {
                                autoSyncMongo = it
                                AppConfig.setAutoSyncMongodb(context, it)
                            }
                        )
                    }

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isSyncingMongo = true
                                mongoSyncStatus = "Syncing with MongoDB Atlas..."
                                val res = MongoAtlasSyncManager.syncAll(context)
                                isSyncingMongo = false
                                mongoSyncStatus = res.message
                                Toast.makeText(context, if (res.success) "MongoDB Sync Successful!" else "Sync Failed: ${res.message}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = !isSyncingMongo,
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isSyncingMongo) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Syncing with MongoDB Atlas...")
                        } else {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Sync Contacts & Leads to Database Now")
                        }
                    }

                    if (mongoSyncStatus != null) {
                        Text(
                            text = mongoSyncStatus!!,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (mongoSyncStatus!!.contains("error", ignoreCase = true) || mongoSyncStatus!!.contains("fail", ignoreCase = true)) CoralRed else EmeraldGreen
                        )
                    }
                }
            }

            // ==========================================
            // 5. CALL RECORDING SETTINGS
            // ==========================================
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Surface(
                                shape = CircleShape,
                                color = if (autoCallRecording) CoralRed else MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.FiberManualRecord, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                            }
                            Column {
                                Text("Call Recording Settings", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Text("Automatic conversation recording", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                        Switch(
                            checked = autoCallRecording,
                            onCheckedChange = { checked ->
                                if (checked && ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                                    if (activity != null) {
                                        ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.RECORD_AUDIO), 1002)
                                    }
                                }
                                autoCallRecording = checked
                                AppConfig.setAutoCallRecording(context, checked)
                            }
                        )
                    }
                    Text(
                        text = "Recordings are saved locally on device storage under Music/TrueDialer and can be played back or shared from Call Logs.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ==========================================
            // 6. WHATSAPP MESSAGE TEMPLATE
            // ==========================================
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Surface(
                                shape = CircleShape,
                                color = EmeraldGreen,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                            }
                            Column {
                                Text("In-Call WhatsApp Message", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Text("Message sent when tapping WhatsApp during call", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = whatsAppTemplate,
                        onValueChange = {
                            whatsAppTemplate = it
                            AppConfig.setWhatsAppTemplate(context, it)
                        },
                        label = { Text("WhatsApp Template") },
                        placeholder = { Text(AppConfig.DEFAULT_WHATSAPP_TEMPLATE) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4,
                        supportingText = {
                            Text("Supports dynamic tags: {name}, {company}, {requirement}, {phone}")
                        }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                whatsAppTemplate = AppConfig.DEFAULT_WHATSAPP_TEMPLATE
                                AppConfig.setWhatsAppTemplate(context, AppConfig.DEFAULT_WHATSAPP_TEMPLATE)
                                Toast.makeText(context, "Reset to default template", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Reset to Default", fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                AppConfig.setWhatsAppTemplate(context, whatsAppTemplate)
                                Toast.makeText(context, "WhatsApp template saved!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Save Template", fontSize = 12.sp)
                        }
                    }
                }
            }

            // ==========================================
            // 7. PRIVACY & PERMISSIONS
            // ==========================================
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = TrueBlue)
                        Text("Privacy & Permissions", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }

                    // Default Dialer
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Default Phone App", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text(if (isDefaultDialer) "✓ Soa Phone is active default dialer" else "Not set as default dialer", fontSize = 11.sp, color = if (isDefaultDialer) EmeraldGreen else AmberOrange)
                        }
                        if (!isDefaultDialer) {
                            TextButton(onClick = { (activity ?: context as? Activity)?.let { PermissionUtils.requestDefaultDialerRole(it) } }) {
                                Text("Set Default")
                            }
                        }
                    }

                    // Floating Overlay
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Draw Over Other Apps", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text(if (hasOverlayPermission) "✓ Permission granted" else "Required for caller ID popup", fontSize = 11.sp, color = if (hasOverlayPermission) EmeraldGreen else AmberOrange)
                        }
                        if (!hasOverlayPermission) {
                            TextButton(onClick = { PermissionUtils.requestOverlayPermission(context) }) {
                                Text("Enable")
                            }
                        }
                    }

                    // Battery Exemption
                    Button(
                        onClick = { PermissionUtils.requestIgnoreBatteryOptimizations(context) },
                        colors = ButtonDefaults.buttonColors(containerColor = TrueBlue),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Allow Unrestricted Background Running")
                    }

                    // App Settings
                    OutlinedButton(
                        onClick = { PermissionUtils.openAppSettings(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Open App System Settings / Permissions")
                    }
                }
            }

            // ==========================================
            // 7. LOGOUT & SESSION TERMINATION
            // ==========================================
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CoralRed.copy(alpha = 0.08f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Logout, contentDescription = null, tint = CoralRed)
                        Text("Logout Employee Session", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CoralRed)
                    }
                    Text(
                        "Your manager session remains logged in permanently on this device (no auto logout). Only manual logout will end this session.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { showLogoutConfirm = true },
                        colors = ButtonDefaults.buttonColors(containerColor = CoralRed),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Logout Active User")
                    }
                }
            }
        }

        // Logout Confirmation Dialog
        if (showLogoutConfirm) {
            AlertDialog(
                onDismissRequest = { showLogoutConfirm = false },
                title = { Text("Confirm Logout") },
                text = { Text("Are you sure you want to log out $activeUserName from this device?") },
                confirmButton = {
                    Button(
                        onClick = {
                            AppConfig.logout(context)
                            activeUserName = AppConfig.CURRENT_USER_NAME
                            activeUserRole = AppConfig.CURRENT_USER_ROLE
                            showLogoutConfirm = false
                            showUserSwitcher = true
                            Toast.makeText(context, "Logged out. Please log in as a Manager.", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CoralRed)
                    ) {
                        Text("Logout")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLogoutConfirm = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showUserSwitcher) {
            UserSwitcherDialog(
                onDismiss = { showUserSwitcher = false },
                onUserSwitched = { user ->
                    showUserSwitcher = false
                    activeUserName = user.name
                    activeUserRole = user.role.name
                    selectedCenter = AVAILABLE_CENTERS.firstOrNull { it.id == AppConfig.CENTER_ID }
                        ?: CenterOption(AppConfig.CENTER_ID, AppConfig.CENTER_NAME)
                },
                onLogout = {
                    showUserSwitcher = false
                    activeUserName = AppConfig.CURRENT_USER_NAME
                    activeUserRole = AppConfig.CURRENT_USER_ROLE
                }
            )
        }
    }
}
