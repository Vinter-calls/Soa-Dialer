package com.dialer.truedialer.ui.components

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.window.Dialog
import com.dialer.truedialer.AppConfig
import com.dialer.truedialer.data.local.EnquiryDatabaseHelper
import com.dialer.truedialer.data.model.Enquiry
import com.dialer.truedialer.data.model.FollowUpReminder
import com.dialer.truedialer.data.repository.ContactsRepository
import com.dialer.truedialer.data.repository.EnquiryRepositoryProvider
import com.dialer.truedialer.service.FollowUpNotificationReceiver
import com.dialer.truedialer.sync.CrmWebhookSync
import com.dialer.truedialer.sync.MongoAtlasSyncManager
import com.dialer.truedialer.ui.theme.AmberOrange
import com.dialer.truedialer.ui.theme.CoralRed
import com.dialer.truedialer.ui.theme.EmeraldGreen
import com.dialer.truedialer.ui.theme.TrueBlue
import com.dialer.truedialer.util.CallRecordingUtils
import com.dialer.truedialer.util.PhoneNumberUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostCallDialog(
    phoneNumber: String,
    callDurationSeconds: Long = 0,
    callType: String = "INCOMING",
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val repo = remember { EnquiryRepositoryProvider.get(context) }
    val contactsRepo = remember { ContactsRepository(context) }
    val dbHelper = remember { EnquiryDatabaseHelper(context) }

    var enquiry by remember { mutableStateOf<Enquiry?>(null) }
    var isContactSaved by remember { mutableStateOf(false) }

    // Required Call Outcomes
    val outcomes = listOf(
        "Interested",
        "Not Interested",
        "Call Later",
        "No Response",
        "Number Busy",
        "Switched Off",
        "Demo Booked",
        "Converted",
        "Wrong Number"
    )
    var selectedOutcome by remember { mutableStateOf("Interested") }

    // Customer Profile & Extended Enquiry Fields
    var contactNameInput by remember { mutableStateOf("") }
    var companyInput by remember { mutableStateOf("") }
    var remarksInput by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }

    val courseOptions = listOf(
        "Python Full Stack",
        "Data Analytics",
        "Java Full Stack",
        "Digital Marketing",
        "MERN Stack",
        "Flutter App Dev",
        "Cloud & DevOps",
        "UI/UX Design"
    )
    var selectedCourse by remember { mutableStateOf("Python Full Stack") }

    val ageCategories = listOf("School", "College / Graduate", "Working Professional", "Job Seeker")
    var selectedAgeCategory by remember { mutableStateOf("College / Graduate") }

    val leadSources = listOf("Website", "Google", "Instagram", "Walk-in", "Referral")
    var selectedLeadSource by remember { mutableStateOf("Website") }

    val demoStatuses = listOf("None", "Booked", "Attended", "Missed")
    var selectedDemoStatus by remember { mutableStateOf("None") }

    val admissionStatuses = listOf("Pending", "Enrolled", "Not Interested")
    var selectedAdmissionStatus by remember { mutableStateOf("Pending") }

    var followUpPreset by remember { mutableStateOf("Tomorrow 10 AM") }

    LaunchedEffect(phoneNumber) {
        val savedName = contactsRepo.getContactName(phoneNumber)
        val found = repo.searchEnquiryByPhone(phoneNumber)
        enquiry = found
        isContactSaved = (savedName != null) || contactsRepo.isContactSaved(phoneNumber)

        contactNameInput = savedName ?: found?.name ?: ""
        companyInput = found?.company ?: ""
        remarksInput = found?.remarks ?: ""
        noteText = found?.notes ?: ""

        found?.status?.let { st ->
            if (outcomes.contains(st)) selectedOutcome = st
        }
        found?.course?.let { if (courseOptions.contains(it)) selectedCourse = it }
        found?.ageCategory?.let { if (ageCategories.contains(it)) selectedAgeCategory = it }
        found?.leadSource?.let { if (leadSources.contains(it)) selectedLeadSource = it }
        found?.demoStatus?.let { if (demoStatuses.contains(it)) selectedDemoStatus = it }
        found?.admissionStatus?.let { if (admissionStatuses.contains(it)) selectedAdmissionStatus = it }
    }

    fun openWhatsAppDirectly() {
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

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .heightIn(max = 680.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Call Outcome & Customer Profile",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val formattedDuration = CallRecordingUtils.formatDuration(callDurationSeconds)
                        Text(
                            text = "${PhoneNumberUtils.formatDisplay(phoneNumber)} • $formattedDuration",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                // Call Outcome Selection (Mandatory)
                Text(
                    text = "SELECT CALL OUTCOME",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    outcomes.forEach { outcome ->
                        val isWrongNumber = outcome == "Wrong Number"
                        val isSelected = selectedOutcome == outcome
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedOutcome = outcome },
                            label = { Text(outcome, fontSize = 11.sp) },
                            colors = if (isWrongNumber && isSelected) {
                                FilterChipDefaults.filterChipColors(selectedContainerColor = CoralRed.copy(alpha = 0.25f))
                            } else if (outcome == "Converted" && isSelected) {
                                FilterChipDefaults.filterChipColors(selectedContainerColor = EmeraldGreen.copy(alpha = 0.25f))
                            } else {
                                FilterChipDefaults.filterChipColors()
                            }
                        )
                    }
                }

                if (selectedOutcome == "Wrong Number") {
                    Surface(
                        color = CoralRed.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "⚠️ 'Wrong Number' will be tagged locally only and will NOT sync to database.",
                            fontSize = 11.sp,
                            color = CoralRed,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                // Customer Profile Details Card
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "CUSTOMER PROFILE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TrueBlue
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = contactNameInput,
                                onValueChange = { contactNameInput = it },
                                label = { Text("Name", fontSize = 12.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = companyInput,
                                onValueChange = { companyInput = it },
                                label = { Text("Place / Branch", fontSize = 12.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Course Interested
                        Text("COURSE INTERESTED", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            courseOptions.forEach { course ->
                                FilterChip(
                                    selected = selectedCourse == course,
                                    onClick = { selectedCourse = course },
                                    label = { Text(course, fontSize = 11.sp) }
                                )
                            }
                        }

                        // Age Category
                        Text("AGE CATEGORY", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ageCategories.forEach { ageCat ->
                                FilterChip(
                                    selected = selectedAgeCategory == ageCat,
                                    onClick = { selectedAgeCategory = ageCat },
                                    label = { Text(ageCat, fontSize = 11.sp) }
                                )
                            }
                        }

                        // Lead Source & Demo Status Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("LEAD SOURCE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    leadSources.forEach { src ->
                                        FilterChip(
                                            selected = selectedLeadSource == src,
                                            onClick = { selectedLeadSource = src },
                                            label = { Text(src, fontSize = 10.sp) }
                                        )
                                    }
                                }
                            }
                        }

                        // Demo Status & Admission Status
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("DEMO STATUS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    demoStatuses.forEach { demo ->
                                        FilterChip(
                                            selected = selectedDemoStatus == demo,
                                            onClick = { selectedDemoStatus = demo },
                                            label = { Text(demo, fontSize = 10.sp) }
                                        )
                                    }
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text("ADMISSION", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    admissionStatuses.forEach { adm ->
                                        FilterChip(
                                            selected = selectedAdmissionStatus == adm,
                                            onClick = { selectedAdmissionStatus = adm },
                                            label = { Text(adm, fontSize = 10.sp) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Remarks & Call Notes
                OutlinedTextField(
                    value = remarksInput,
                    onValueChange = { remarksInput = it },
                    label = { Text("Customer Remarks", fontSize = 12.sp) },
                    placeholder = { Text("e.g. Inquiring for morning batch, budget flexible...") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Call Discussion Notes", fontSize = 12.sp) },
                    placeholder = { Text("Key discussion points...") },
                    modifier = Modifier.fillMaxWidth()
                )

                // Follow-Up Scheduling
                Text("SCHEDULE NEXT CALL / FOLLOW-UP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                val reminderPresets = listOf("In 1 Hour", "Tomorrow 10 AM", "In 3 Days", "Next Week")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    reminderPresets.forEach { r ->
                        FilterChip(
                            selected = followUpPreset == r,
                            onClick = { followUpPreset = r },
                            label = { Text(r, fontSize = 11.sp) }
                        )
                    }
                }

                // Action Buttons: WhatsApp Integration (Direct) & Save
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { openWhatsAppDirectly() },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = EmeraldGreen),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("WhatsApp", fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                val finalName = contactNameInput.ifBlank { enquiry?.name ?: if (isContactSaved) "Contact" else "Lead" }
                                val finalCompany = companyInput.ifBlank { enquiry?.company ?: "School of Athens" }

                                // 1. Save Call Note
                                if (noteText.isNotBlank()) {
                                    dbHelper.addCallNote(phoneNumber, noteText)
                                }

                                // 2. If name was updated, update phonebook and sync across DB
                                if (!isContactSaved && contactNameInput.isNotBlank()) {
                                    contactsRepo.saveContactDirectly(finalName, phoneNumber, finalCompany, null)
                                } else if (isContactSaved && contactNameInput.isNotBlank() && contactNameInput != enquiry?.name) {
                                    dbHelper.updateEnquiryNameByPhone(phoneNumber, finalName)
                                }

                                val delayMs = when (followUpPreset) {
                                    "In 1 Hour" -> 3600_000L
                                    "Tomorrow 10 AM" -> 86400_000L
                                    "In 3 Days" -> 3 * 86400_000L
                                    "Next Week" -> 7 * 86400_000L
                                    else -> 86400_000L
                                }
                                val triggerEpochMs = System.currentTimeMillis() + delayMs

                                val updatedEnquiry = (enquiry ?: Enquiry(
                                    id = "ENQ-${System.currentTimeMillis() % 100000}",
                                    name = finalName,
                                    phoneNumber = phoneNumber,
                                    company = finalCompany,
                                    status = selectedOutcome,
                                    notes = noteText.ifBlank { null },
                                    assignedTo = AppConfig.CURRENT_USER_NAME
                                )).copy(
                                    name = finalName,
                                    phoneNumber = phoneNumber,
                                    company = finalCompany,
                                    status = selectedOutcome,
                                    course = selectedCourse,
                                    ageCategory = selectedAgeCategory,
                                    leadSource = selectedLeadSource,
                                    demoStatus = if (selectedDemoStatus != "None") selectedDemoStatus else null,
                                    admissionStatus = selectedAdmissionStatus,
                                    followUpDate = triggerEpochMs,
                                    remarks = remarksInput.ifBlank { null },
                                    notes = if (noteText.isNotBlank()) noteText else enquiry?.notes,
                                    assignedTo = enquiry?.assignedTo ?: AppConfig.CURRENT_USER_NAME
                                )

                                // Save locally
                                repo.saveEnquiry(updatedEnquiry)

                                if (selectedOutcome == "Wrong Number") {
                                    // Rule: "Wrong Number - no need to sync to database"
                                    Toast.makeText(context, "Saved locally as Wrong Number (skipped cloud sync)", Toast.LENGTH_SHORT).show()
                                } else {
                                    // Auto-sync new / updated lead to MongoDB Atlas
                                    MongoAtlasSyncManager.autoSyncLead(updatedEnquiry)
                                    CrmWebhookSync.syncLead(updatedEnquiry)
                                    Toast.makeText(context, "Lead saved & synced to database!", Toast.LENGTH_SHORT).show()
                                }

                                // Schedule Reminder
                                val reminder = FollowUpReminder(
                                    id = "REM-${System.currentTimeMillis()}",
                                    phoneNumber = phoneNumber,
                                    customerName = updatedEnquiry.name,
                                    note = remarksInput.ifBlank { noteText.ifBlank { "Follow-up regarding $selectedCourse" } },
                                    scheduledTimeEpochMs = triggerEpochMs,
                                    assignedUserId = AppConfig.CURRENT_USER_ID
                                )
                                dbHelper.saveFollowUp(reminder)
                                scheduleAlarm(context, reminder)

                                // Log Call Event
                                CrmWebhookSync.syncCallEvent(
                                    phoneNumber = phoneNumber,
                                    callDurationSeconds = callDurationSeconds,
                                    callType = callType,
                                    enquiry = updatedEnquiry,
                                    postCallNote = noteText,
                                    scheduledFollowUp = triggerEpochMs
                                )

                                onDismiss()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TrueBlue),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save & Finish", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

fun scheduleAlarm(context: Context, reminder: FollowUpReminder) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
    val intent = Intent(context, FollowUpNotificationReceiver::class.java).apply {
        putExtra(FollowUpNotificationReceiver.EXTRA_PHONE_NUMBER, reminder.phoneNumber)
        putExtra(FollowUpNotificationReceiver.EXTRA_CUSTOMER_NAME, reminder.customerName)
        putExtra(FollowUpNotificationReceiver.EXTRA_NOTE, reminder.note)
        putExtra(FollowUpNotificationReceiver.EXTRA_REMINDER_ID, reminder.id)
    }

    val pendingIntent = PendingIntent.getBroadcast(
        context,
        reminder.id.hashCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    try {
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            reminder.scheduledTimeEpochMs,
            pendingIntent
        )
    } catch (e: SecurityException) {
        alarmManager.set(AlarmManager.RTC_WAKEUP, reminder.scheduledTimeEpochMs, pendingIntent)
    }
}
