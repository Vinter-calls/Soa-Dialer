package com.dialer.truedialer.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dialer.truedialer.data.model.CallRecord
import com.dialer.truedialer.data.model.CallType
import com.dialer.truedialer.data.model.Enquiry
import com.dialer.truedialer.data.repository.CallLogRepository
import com.dialer.truedialer.data.repository.ContactsRepository
import com.dialer.truedialer.data.repository.EnquiryRepositoryProvider
import com.dialer.truedialer.ui.components.ContactDetailDialog
import com.dialer.truedialer.ui.components.CopySelectionDialog
import com.dialer.truedialer.ui.components.EnquiryBadge
import com.dialer.truedialer.ui.components.SaveContactDialog
import com.dialer.truedialer.ui.components.SyncEnquiryDialog
import com.dialer.truedialer.ui.components.TemplateChooserDialog
import com.dialer.truedialer.ui.theme.CoralRed
import com.dialer.truedialer.ui.theme.EmeraldGreen
import com.dialer.truedialer.ui.theme.TrueBlue
import com.dialer.truedialer.util.ClipboardUtils
import com.dialer.truedialer.util.PhoneNumberUtils
import kotlinx.coroutines.launch

enum class RecentCallFilter(val label: String) {
    ALL("All"),
    UNSAVED("Unsaved"),
    ENQUIRY("Enquiry"),
    MISSED("Missed")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentsScreen(
    onCallNumber: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val enquiryRepo = remember { EnquiryRepositoryProvider.get(context) }
    val contactsRepo = remember { ContactsRepository(context) }
    val callLogRepo = remember { CallLogRepository(context, contactsRepo, enquiryRepo) }

    var callLogs by remember { mutableStateOf<List<CallRecord>>(emptyList()) }
    var currentFilter by remember { mutableStateOf(RecentCallFilter.ALL) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedRecordForSave by remember { mutableStateOf<CallRecord?>(null) }
    var selectedRecordForWhatsApp by remember { mutableStateOf<CallRecord?>(null) }
    var selectedRecordForDetails by remember { mutableStateOf<CallRecord?>(null) }
    var selectedRecordForSync by remember { mutableStateOf<CallRecord?>(null) }
    var selectedRecordForPlayback by remember { mutableStateOf<Pair<String, java.io.File?>?>(null) }
    var selectedCallForCopy by remember { mutableStateOf<Pair<String, String>?>(null) }

    val startOfToday = remember {
        java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun openWhatsAppDirectly(phoneNumber: String) {
        val clean = phoneNumber.replace("[^0-9]".toRegex(), "")
        val targetNumber = if (clean.length == 10) "91$clean" else clean
        val uri = Uri.parse("https://wa.me/$targetNumber")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open WhatsApp: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun refreshLogs() {
        coroutineScope.launch {
            isLoading = true
            try {
                callLogs = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    callLogRepo.getRecentCalls()
                }
            } catch (_: Exception) {
                callLogs = emptyList()
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshLogs()
    }

    val unsavedCalls = remember(callLogs) { callLogs.filter { !it.isContactSaved || it.matchedEnquiry == null } }
    val enquiryCalls = remember(callLogs) { callLogs.filter { it.matchedEnquiry != null } }
    val missedCalls = remember(callLogs) { callLogs.filter { it.type == CallType.MISSED } }

    val filteredCalls = remember(callLogs, currentFilter) {
        when (currentFilter) {
            RecentCallFilter.ALL -> callLogs
            RecentCallFilter.UNSAVED -> unsavedCalls
            RecentCallFilter.ENQUIRY -> enquiryCalls
            RecentCallFilter.MISSED -> missedCalls
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recent Calls", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                val synced = com.dialer.truedialer.sync.CrmWebhookSync.syncCallHistoryBatch(callLogs.take(50))
                                if (synced) {
                                    Toast.makeText(context, "Call history synchronized to CRM!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Call history logged locally (Configure Webhook in Settings for Cloud CRM)", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = "Sync Call History")
                    }
                    TextButton(onClick = { refreshLogs() }) {
                        Text("Refresh")
                    }
                }
            )
        },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Filter Tabs Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = currentFilter == RecentCallFilter.ALL,
                    onClick = { currentFilter = RecentCallFilter.ALL },
                    label = { Text("All (${callLogs.size})", fontSize = 12.sp) }
                )

                FilterChip(
                    selected = currentFilter == RecentCallFilter.UNSAVED,
                    onClick = { currentFilter = RecentCallFilter.UNSAVED },
                    label = { Text("Unsaved (${unsavedCalls.size})", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFD97706).copy(alpha = 0.25f),
                        selectedLabelColor = Color(0xFFD97706)
                    )
                )

                FilterChip(
                    selected = currentFilter == RecentCallFilter.ENQUIRY,
                    onClick = { currentFilter = RecentCallFilter.ENQUIRY },
                    label = { Text("Enquiry (${enquiryCalls.size})", fontSize = 12.sp) }
                )

                FilterChip(
                    selected = currentFilter == RecentCallFilter.MISSED,
                    onClick = { currentFilter = RecentCallFilter.MISSED },
                    label = { Text("Missed (${missedCalls.size})", fontSize = 12.sp) }
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else if (filteredCalls.isEmpty()) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (currentFilter == RecentCallFilter.UNSAVED) "No unsaved numbers in recent calls" else "No recent call history found",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Grant Call Log permission in Settings if prompted",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(filteredCalls, key = { it.id }) { call ->
                            RecentCallItem(
                                call = call,
                                onCallClick = { onCallNumber(call.phoneNumber) },
                                onSaveClick = { selectedRecordForSave = call },
                                onWhatsAppClick = { openWhatsAppDirectly(call.phoneNumber) },
                                onDetailsClick = { selectedRecordForDetails = call },
                                onPlayRecording = { name, file -> selectedRecordForPlayback = Pair(name, file) },
                                onCopyOptionsClick = { name, phone -> selectedCallForCopy = Pair(name, phone) }
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            }
        }

        // Save Contact Dialog
        selectedRecordForSave?.let { record ->
            SaveContactDialog(
                initialPhoneNumber = record.phoneNumber,
                initialEnquiry = record.matchedEnquiry,
                onDismiss = { selectedRecordForSave = null },
                onSaveDirectly = { name, phone, company, email ->
                    coroutineScope.launch {
                        val success = contactsRepo.saveContactDirectly(name, phone, company, email)
                        if (success) {
                            Toast.makeText(context, "Saved $name to contacts & synced to database!", Toast.LENGTH_SHORT).show()
                            refreshLogs()
                        } else {
                            context.startActivity(contactsRepo.createSaveContactIntent(name, phone, company, email))
                        }
                    }
                },
                onOpenSystemEditor = { name, phone, company, email ->
                    context.startActivity(contactsRepo.createSaveContactIntent(name, phone, company, email))
                }
            )
        }

        // Contact & Lead Details Dialog
        selectedRecordForDetails?.let { record ->
            ContactDetailDialog(
                callRecord = record,
                onDismiss = { selectedRecordForDetails = null },
                onCallClick = { onCallNumber(record.phoneNumber) },
                onWhatsAppClick = { openWhatsAppDirectly(record.phoneNumber) },
                onSaveClick = { selectedRecordForSave = record }
            )
        }

        // Sync to Enquiry Database Dialog
        selectedRecordForSync?.let { record ->
            SyncEnquiryDialog(
                initialPhoneNumber = record.phoneNumber,
                initialName = record.cachedName ?: record.matchedEnquiry?.name,
                onDismiss = { selectedRecordForSync = null },
                onSync = { newEnq ->
                    coroutineScope.launch {
                        val saved = enquiryRepo.saveEnquiry(newEnq)
                        if (saved) {
                            Toast.makeText(context, "Synced to Enquiry Database!", Toast.LENGTH_SHORT).show()
                            refreshLogs()
                        }
                    }
                }
            )
        }

        // Call Recording Player Dialog
        selectedRecordForPlayback?.let { pair ->
            com.dialer.truedialer.ui.components.CallRecordingPlayerDialog(
                displayName = pair.first,
                recordingFile = pair.second,
                onDismiss = { selectedRecordForPlayback = null }
            )
        }

        // Long-Press Copy Selection Dialog (Name / Phone / Both)
        selectedCallForCopy?.let { pair ->
            CopySelectionDialog(
                name = pair.first,
                phoneNumber = pair.second,
                onDismiss = { selectedCallForCopy = null }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecentCallItem(
    call: CallRecord,
    onCallClick: () -> Unit,
    onSaveClick: () -> Unit,
    onWhatsAppClick: () -> Unit,
    onDetailsClick: () -> Unit,
    onPlayRecording: (String, java.io.File) -> Unit,
    onCopyOptionsClick: (String, String) -> Unit
) {
    val context = LocalContext.current
    val relativeTime = DateUtils.getRelativeTimeSpanString(
        call.timestamp,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE
    ).toString()
    val durationText = when {
        call.type == CallType.MISSED -> "Missed"
        call.type == CallType.REJECTED || call.type == CallType.BLOCKED -> "Declined"
        call.durationSeconds > 0 -> com.dialer.truedialer.util.CallRecordingUtils.formatDuration(call.durationSeconds)
        else -> "Unanswered"
    }
    val recordingFile = remember(call.phoneNumber) { com.dialer.truedialer.util.CallRecordingUtils.findCallRecording(context, call.phoneNumber) }
    // Display hierarchy:
    // 1. Name and ID number from MongoDB database
    // 2. If not in DB: contact name from phone contacts
    // 3. If contact name not available: contact phone number
    val displayName = when {
        call.matchedEnquiry != null -> call.matchedEnquiry.displayNameWithId
        !call.cachedName.isNullOrBlank() -> call.cachedName
        else -> PhoneNumberUtils.formatDisplay(call.phoneNumber)
    }

    val isCompletelyUnsaved = !call.isContactSaved && call.matchedEnquiry == null
    val isSavedOnlyInPhone = call.isContactSaved && call.matchedEnquiry == null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .combinedClickable(
                onClick = { onDetailsClick() },
                onLongClick = {
                    onCopyOptionsClick(displayName, call.phoneNumber)
                }
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Call Type Icon
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    when (call.type) {
                        CallType.MISSED, CallType.REJECTED -> CoralRed.copy(alpha = 0.15f)
                        CallType.INCOMING -> TrueBlue.copy(alpha = 0.15f)
                        CallType.OUTGOING -> EmeraldGreen.copy(alpha = 0.15f)
                        else -> Color.LightGray.copy(alpha = 0.2f)
                    },
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when (call.type) {
                    CallType.MISSED -> Icons.AutoMirrored.Filled.CallMissed
                    CallType.REJECTED, CallType.BLOCKED -> Icons.Default.Block
                    CallType.INCOMING -> Icons.AutoMirrored.Filled.CallReceived
                    CallType.OUTGOING -> Icons.AutoMirrored.Filled.CallMade
                    else -> Icons.Default.Call
                },
                contentDescription = null,
                tint = when (call.type) {
                    CallType.MISSED, CallType.REJECTED -> CoralRed
                    CallType.INCOMING -> TrueBlue
                    CallType.OUTGOING -> EmeraldGreen
                    else -> Color.DarkGray
                },
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Caller Details
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable { onDetailsClick() },
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = displayName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (call.type == CallType.MISSED || call.matchedEnquiry?.status == "Spam") CoralRed else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (isCompletelyUnsaved) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFD97706).copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "UNSAVED",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFD97706),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                } else if (call.matchedEnquiry != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    EnquiryBadge(status = call.matchedEnquiry.status)
                } else if (isSavedOnlyInPhone) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = EmeraldGreen.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "CONTACT",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldGreen,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            val extraDetail = when {
                !call.matchedEnquiry?.company.isNullOrBlank() -> " • ${call.matchedEnquiry.company}"
                !call.matchedEnquiry?.course.isNullOrBlank() -> " • ${call.matchedEnquiry.course}"
                else -> ""
            }

            Text(
                text = "$relativeTime • $durationText$extraDetail",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Action Buttons: Play Recording, Save, Call, WhatsApp
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            if (recordingFile != null) {
                IconButton(
                    onClick = { onPlayRecording(displayName, recordingFile) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play Recording",
                        tint = EmeraldGreen,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (!call.isContactSaved) {
                IconButton(
                    onClick = onSaveClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PersonAdd,
                        contentDescription = "Save Contact",
                        tint = TrueBlue,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            IconButton(
                onClick = onCallClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = "Call",
                    tint = TrueBlue,
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = onWhatsAppClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "WhatsApp",
                    tint = EmeraldGreen,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
