package com.dialer.truedialer.ui.screens

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.dialer.truedialer.data.local.CallStatRecord
import com.dialer.truedialer.data.local.CallStatSummary
import com.dialer.truedialer.data.local.EnquiryDatabaseHelper
import com.dialer.truedialer.data.model.CallRecord
import com.dialer.truedialer.data.model.CallType
import com.dialer.truedialer.data.model.Enquiry
import com.dialer.truedialer.data.model.FollowUpReminder
import com.dialer.truedialer.data.repository.CallLogRepository
import com.dialer.truedialer.data.repository.ContactsRepository
import com.dialer.truedialer.data.repository.EnquiryRepositoryProvider
import com.dialer.truedialer.ui.components.CrmLoginView
import com.dialer.truedialer.ui.components.TemplateChooserDialog
import com.dialer.truedialer.ui.components.UserProfileBadge
import com.dialer.truedialer.ui.components.UserSwitcherDialog
import com.dialer.truedialer.ui.theme.AmberOrange
import com.dialer.truedialer.ui.theme.CoralRed
import com.dialer.truedialer.ui.theme.EmeraldGreen
import com.dialer.truedialer.ui.theme.TrueBlue
import com.dialer.truedialer.util.CallRecordingUtils
import com.dialer.truedialer.util.PhoneNumberUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    onCallNumber: (String) -> Unit,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val dbHelper = remember { EnquiryDatabaseHelper(context) }
    val repo = remember { EnquiryRepositoryProvider.get(context) }
    val contactsRepo = remember { ContactsRepository(context) }
    val callLogRepo = remember { CallLogRepository(context, contactsRepo, repo) }

    var enquiries by remember { mutableStateOf<List<Enquiry>>(emptyList()) }
    var callLogs by remember { mutableStateOf<List<CallRecord>>(emptyList()) }
    var isUserLoggedIn by remember { mutableStateOf(AppConfig.isLoggedIn) }
    var monthlyCallLogs by remember { mutableStateOf<List<CallRecord>>(emptyList()) }
    var pendingFollowUps by remember { mutableStateOf<List<FollowUpReminder>>(emptyList()) }
    var callStatsMap by remember { mutableStateOf<Map<String, CallStatRecord>>(emptyMap()) }
    var callAnalytics by remember { mutableStateOf<CallStatSummary?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showMonthlyCallsDialog by remember { mutableStateOf(false) }
    var showUserSwitcher by remember { mutableStateOf(false) }

    var templateChooserPhone by remember { mutableStateOf<String?>(null) }
    var templateChooserEnquiry by remember { mutableStateOf<Enquiry?>(null) }
    var selectedRecordForPlayback by remember { mutableStateOf<Pair<String, java.io.File?>?>(null) }

    fun refreshData() {
        if (!AppConfig.isLoggedIn) {
            enquiries = emptyList()
            isLoading = false
            return
        }
        coroutineScope.launch {
            isLoading = true
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val e = repo.getAllEnquiries()
                val c = callLogRepo.getRecentCalls(100)
                val m = callLogRepo.getCallLogsForPastMonth()
                val f = dbHelper.getPendingFollowUps()
                val s = dbHelper.getAllCallStatsMap()
                val a = dbHelper.getLeadCallAnalytics()
                object {
                    val enquiries = e
                    val callLogs = c
                    val monthlyCallLogs = m
                    val pendingFollowUps = f
                    val callStatsMap = s
                    val callAnalytics = a
                }
            }
            enquiries = result.enquiries
            callLogs = result.callLogs
            monthlyCallLogs = result.monthlyCallLogs
            pendingFollowUps = result.pendingFollowUps
            callStatsMap = result.callStatsMap
            callAnalytics = result.callAnalytics
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        isUserLoggedIn = AppConfig.isLoggedIn
        if (isUserLoggedIn) {
            refreshData()
        } else {
            enquiries = emptyList()
            isLoading = false
        }
    }

    val todayCallsCount = remember(callLogs) {
        val startOfToday = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        callLogs.count { it.timestamp >= startOfToday }
    }
    val totalCalls = callLogs.size
    val totalEnquiries = enquiries.size
    val newLeadsCount = enquiries.count { it.status.equals("New Lead", true) }
    val followUpsCount = enquiries.count { it.status.equals("Follow-up", true) }
    val enrolledCount = enquiries.count { it.status.equals("Enrolled", true) || it.status.equals("Converted", true) }
    val lostCount = enquiries.count { it.status.equals("Lost", true) }
    val spamCount = enquiries.count { it.status.equals("Spam", true) }

    val enrollmentRate = if (totalEnquiries > 0) (enrolledCount * 100) / totalEnquiries else 0

    if (!isUserLoggedIn) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Lead Pipeline & Analytics (Locked)", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    }
                )
            },
            modifier = modifier
        ) { padding ->
            CrmLoginView(
                onLoginSuccess = {
                    isUserLoggedIn = true
                    refreshData()
                },
                modifier = Modifier.padding(padding)
            )
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lead Pipeline & Analytics", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    UserProfileBadge(onClick = { showUserSwitcher = true })
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = { refreshData() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        modifier = modifier
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // SECTION 1: Key Metrics Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        title = "Today's Calls",
                        value = "$todayCallsCount",
                        subtext = "Tap for 30-day breakdown",
                        icon = Icons.Default.Call,
                        accentColor = TrueBlue,
                        modifier = Modifier.weight(1f),
                        onClick = { showMonthlyCallsDialog = true }
                    )
                    MetricCard(
                        title = "Enrolled Leads",
                        value = "$enrolledCount",
                        subtext = "$enrollmentRate% enrolled",
                        icon = Icons.Default.CheckCircle,
                        accentColor = EmeraldGreen,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        title = "Total Leads",
                        value = "$totalEnquiries",
                        subtext = "$newLeadsCount new leads",
                        icon = Icons.Default.AssignmentInd,
                        accentColor = AmberOrange,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Follow-ups Due",
                        value = "${pendingFollowUps.size}",
                        subtext = if (pendingFollowUps.isNotEmpty()) "Action required" else "All caught up",
                        icon = Icons.Default.Schedule,
                        accentColor = if (pendingFollowUps.isNotEmpty()) CoralRed else EmeraldGreen,
                        modifier = Modifier.weight(1f)
                    )
                }

                // SECTION 1.5: Ringing & Talk Time Analytics (Pipeline)
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Pipeline Call & Ringing Time",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                tint = TrueBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        val totalTalkSec = callAnalytics?.totalTalkSeconds ?: 0L
                        val totalRingSec = callAnalytics?.totalRingSeconds ?: 0L
                        val avgRingSec = callAnalytics?.avgRingSeconds ?: 0L
                        val avgTalkSec = callAnalytics?.avgTalkSeconds ?: 0L

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Total Talked", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = CallRecordingUtils.formatDuration(totalTalkSec),
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = EmeraldGreen
                                    )
                                    Text("Avg: ${avgTalkSec}s / call", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Total Ringed", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = CallRecordingUtils.formatDuration(totalRingSec),
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AmberOrange
                                    )
                                    Text("Avg: ${avgRingSec}s / call", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                // SECTION 2: Pipeline Funnel Breakdown
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Pipeline Stage Distribution",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        PipelineProgressBar(label = "New Leads", count = newLeadsCount, total = totalEnquiries, color = TrueBlue)
                        PipelineProgressBar(label = "In Follow-up", count = followUpsCount, total = totalEnquiries, color = AmberOrange)
                        PipelineProgressBar(label = "Enrolled", count = enrolledCount, total = totalEnquiries, color = EmeraldGreen)
                        PipelineProgressBar(label = "Lost / Closed", count = lostCount, total = totalEnquiries, color = Color.Gray)
                        PipelineProgressBar(label = "Spam / Blocked", count = spamCount, total = totalEnquiries, color = CoralRed)
                    }
                }

                // SECTION 3: Recent Calls & Call Duration
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Recent Calls & Duration (${callLogs.size})",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        if (callLogs.isEmpty()) {
                            Text(
                                text = "No recent call logs available.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                callLogs.take(15).forEach { call ->
                                    val clean = PhoneNumberUtils.extractNationalNumber(call.phoneNumber)
                                    val stat = callStatsMap[clean] ?: callStatsMap[call.phoneNumber.replace("[^0-9]".toRegex(), "")]
                                    val talkSec = if (call.durationSeconds > 0) call.durationSeconds else (stat?.talkDurationSeconds ?: 0L)
                                    val ringSec = stat?.ringDurationSeconds ?: 0L
                                    val durationStr = CallRecordingUtils.formatDuration(talkSec)
                                    val displayName = (if (call.isContactSaved && !call.cachedName.isNullOrBlank()) call.cachedName else null)
                                        ?: call.cachedName
                                        ?: call.matchedEnquiry?.name
                                        ?: PhoneNumberUtils.formatDisplay(call.phoneNumber)
                                    val recordingFile = CallRecordingUtils.findCallRecording(context, call.phoneNumber)

                                    val callDetailText = if (ringSec > 0) {
                                        "${PhoneNumberUtils.formatDisplay(call.phoneNumber)} • 🔔 Ring: ${ringSec}s • 💬 Talk: $durationStr"
                                    } else {
                                        "${PhoneNumberUtils.formatDisplay(call.phoneNumber)} • 💬 Talk: $durationStr"
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = displayName,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 15.sp
                                                )
                                                Text(
                                                    text = callDetailText,
                                                    fontSize = 12.sp,
                                                    color = TrueBlue,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }

                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                if (recordingFile != null) {
                                                    FilledIconButton(
                                                        onClick = { selectedRecordForPlayback = Pair(displayName, recordingFile) },
                                                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = EmeraldGreen),
                                                        modifier = Modifier.size(34.dp)
                                                    ) {
                                                        Icon(Icons.Default.PlayArrow, contentDescription = "Play Recording", tint = Color.White, modifier = Modifier.size(16.dp))
                                                    }
                                                }

                                                FilledIconButton(
                                                    onClick = { onCallNumber(call.phoneNumber) },
                                                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = TrueBlue),
                                                    modifier = Modifier.size(34.dp)
                                                ) {
                                                    Icon(Icons.Default.Call, contentDescription = "Call", tint = Color.White, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // WhatsApp Business Template Chooser Dialog
        templateChooserPhone?.let { phone ->
            TemplateChooserDialog(
                phoneNumber = phone,
                enquiry = templateChooserEnquiry,
                onDismiss = {
                    templateChooserPhone = null
                    templateChooserEnquiry = null
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

        // Monthly Call Breakdown Dialog (Past 30 Days)
        if (showMonthlyCallsDialog) {
            MonthlyCallBreakdownDialog(
                callLogs = monthlyCallLogs.ifEmpty { callLogs },
                onDismiss = { showMonthlyCallsDialog = false }
            )
        }

        // User & Profile Switcher Modal
        if (showUserSwitcher) {
            UserSwitcherDialog(
                onDismiss = { showUserSwitcher = false },
                onUserSwitched = {
                    showUserSwitcher = false
                    isUserLoggedIn = AppConfig.isLoggedIn
                    refreshData()
                },
                onLogout = {
                    showUserSwitcher = false
                    isUserLoggedIn = false
                    enquiries = emptyList()
                }
            )
        }
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    subtext: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick = onClick ?: {},
        enabled = onClick != null,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(accentColor.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                }
                if (onClick != null) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "View Details",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(text = value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(text = title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (subtext != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = subtext, fontSize = 11.sp, color = accentColor, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
fun MonthlyCallBreakdownDialog(
    callLogs: List<CallRecord>,
    onDismiss: () -> Unit
) {
    val dateFormat = remember { java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()) }
    val dayFormat = remember { java.text.SimpleDateFormat("EEEE", java.util.Locale.getDefault()) }
    val dateKeyFormat = remember { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()) }

    val todayKey = remember { dateKeyFormat.format(java.util.Date()) }
    val yesterdayKey = remember {
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
        dateKeyFormat.format(cal.time)
    }

    // Group calls by date key for past 30 days
    val dailyBreakdown = remember(callLogs) {
        val map = linkedMapOf<String, MutableList<CallRecord>>()
        val cal = java.util.Calendar.getInstance()
        for (i in 0..29) {
            val key = dateKeyFormat.format(cal.time)
            map[key] = mutableListOf()
            cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
        }
        for (call in callLogs) {
            val key = dateKeyFormat.format(java.util.Date(call.timestamp))
            if (map.containsKey(key)) {
                map[key]?.add(call)
            } else {
                map[key] = mutableListOf(call)
            }
        }
        map.map { (key, calls) ->
            val firstCallDate = calls.firstOrNull()?.let { java.util.Date(it.timestamp) }
                ?: try { dateKeyFormat.parse(key) } catch (_: Exception) { null }
                ?: java.util.Date()
            val incoming = calls.count { it.type == CallType.INCOMING }
            val outgoing = calls.count { it.type == CallType.OUTGOING }
            val missed = calls.count { it.type == CallType.MISSED || it.type == CallType.REJECTED }
            val totalSeconds = calls.sumOf { it.durationSeconds }
            DailyCallStat(
                dateKey = key,
                displayDate = when (key) {
                    todayKey -> "Today (${dateFormat.format(firstCallDate)})"
                    yesterdayKey -> "Yesterday (${dateFormat.format(firstCallDate)})"
                    else -> "${dayFormat.format(firstCallDate)}, ${dateFormat.format(firstCallDate)}"
                },
                totalCalls = calls.size,
                incoming = incoming,
                outgoing = outgoing,
                missed = missed,
                durationSeconds = totalSeconds
            )
        }
    }

    val maxCallsInDay = dailyBreakdown.maxOfOrNull { it.totalCalls }?.coerceAtLeast(1) ?: 1
    val totalMonthCalls = dailyBreakdown.sumOf { it.totalCalls }
    val totalMonthTalkSec = dailyBreakdown.sumOf { it.durationSeconds }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("30-Day Call History", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$totalMonthCalls total calls • ${CallRecordingUtils.formatDuration(totalMonthTalkSec)} total talk time",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(dailyBreakdown) { day ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (day.totalCalls > 0) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = day.displayDate,
                                    fontSize = 13.sp,
                                    fontWeight = if (day.totalCalls > 0) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (day.totalCalls > 0) MaterialTheme.colorScheme.onSurface else Color.Gray
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (day.totalCalls > 0) TrueBlue else Color.LightGray.copy(alpha = 0.4f)
                                ) {
                                    Text(
                                        text = "${day.totalCalls} calls",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (day.totalCalls > 0) Color.White else Color.DarkGray,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            if (day.totalCalls > 0) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "🟢 ${day.incoming} In  •  🔵 ${day.outgoing} Out  •  🔴 ${day.missed} Missed",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (day.durationSeconds > 0) {
                                        Text(
                                            text = CallRecordingUtils.formatDuration(day.durationSeconds),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = EmeraldGreen
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { day.totalCalls.toFloat() / maxCallsInDay },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp),
                                    color = TrueBlue,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

data class DailyCallStat(
    val dateKey: String,
    val displayDate: String,
    val totalCalls: Int,
    val incoming: Int,
    val outgoing: Int,
    val missed: Int,
    val durationSeconds: Long
)

@Composable
fun PipelineProgressBar(label: String, count: Int, total: Int, color: Color) {
    val progress = if (total > 0) count.toFloat() / total.toFloat() else 0f
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text(text = "$count", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}
