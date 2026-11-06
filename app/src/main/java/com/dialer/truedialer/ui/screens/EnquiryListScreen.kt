package com.dialer.truedialer.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dialer.truedialer.AppConfig
import com.dialer.truedialer.data.local.CallStatRecord
import com.dialer.truedialer.data.local.CallingReportMetrics
import com.dialer.truedialer.data.local.EnquiryDatabaseHelper
import com.dialer.truedialer.data.model.CenterUser
import com.dialer.truedialer.data.model.Enquiry
import com.dialer.truedialer.data.model.FollowUpReminder
import com.dialer.truedialer.data.repository.ContactsRepository
import com.dialer.truedialer.data.repository.EnquiryRepositoryProvider
import com.dialer.truedialer.service.scheduleAlarm
import com.dialer.truedialer.sync.CrmWebhookSync
import com.dialer.truedialer.sync.MongoAtlasSyncManager
import com.dialer.truedialer.ui.components.CopySelectionDialog
import com.dialer.truedialer.ui.components.CrmLoginView
import com.dialer.truedialer.ui.components.EnquiryBadge
import com.dialer.truedialer.ui.components.SaveContactDialog
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
fun EnquiryListScreen(
    onCallNumber: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val dbHelper = remember { EnquiryDatabaseHelper(context) }
    val enquiryRepo = remember { EnquiryRepositoryProvider.get(context) }
    val contactsRepo = remember { ContactsRepository(context) }

    var searchQuery by remember { mutableStateOf("") }
    var selectedStatusFilter by remember { mutableStateOf("All") }
    var enquiries by remember { mutableStateOf<List<Enquiry>>(emptyList()) }
    var pendingFollowUps by remember { mutableStateOf<List<FollowUpReminder>>(emptyList()) }
    var callStatsMap by remember { mutableStateOf<Map<String, CallStatRecord>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }

    // Calling Reports Period & Metrics
    var reportPeriod by rememberSaveable { mutableStateOf("TODAY") }
    var callingMetrics by remember { mutableStateOf(CallingReportMetrics()) }

    var isUserLoggedIn by remember { mutableStateOf(AppConfig.isLoggedIn) }
    var selectedEnquiryForSave by remember { mutableStateOf<Enquiry?>(null) }
    var selectedEnquiryForEdit by remember { mutableStateOf<Enquiry?>(null) }
    var selectedEnquiryForSchedule by remember { mutableStateOf<Enquiry?>(null) }
    var showAddEnquiryDialog by remember { mutableStateOf(false) }
    var showScheduleDialog by remember { mutableStateOf(false) }
    var showUserSwitcherDialog by remember { mutableStateOf(false) }
    var selectedUserFilter by remember { mutableStateOf("ALL") }
    var centerUsers by remember { mutableStateOf<List<CenterUser>>(emptyList()) }
    var selectedEnquiryForAssign by remember { mutableStateOf<Enquiry?>(null) }
    var selectedEnquiryForCopy by remember { mutableStateOf<Enquiry?>(null) }
    var isCloudSyncing by remember { mutableStateOf(false) }

    fun refreshEnquiries() {
        if (!AppConfig.isLoggedIn) {
            enquiries = emptyList()
            pendingFollowUps = emptyList()
            callStatsMap = emptyMap()
            isLoading = false
            return
        }
        coroutineScope.launch {
            isLoading = true
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val u = dbHelper.getCenterUsers()
                val e = if (searchQuery.isBlank()) enquiryRepo.getAllEnquiries() else enquiryRepo.searchEnquiries(searchQuery)
                val f = dbHelper.getPendingFollowUps(
                    forUserId = if (selectedUserFilter == "MY_LEADS") AppConfig.CURRENT_USER_ID else if (selectedUserFilter != "ALL") selectedUserFilter else null,
                    isManager = AppConfig.isCurrentUserManager && selectedUserFilter == "ALL"
                )
                val s = dbHelper.getAllCallStatsMap()
                val m = dbHelper.getCallingReportMetrics(reportPeriod)
                object {
                    val users = u
                    val enqs = e
                    val followUps = f
                    val stats = s
                    val callingMetrics = m
                }
            }
            centerUsers = result.users
            enquiries = result.enqs
            pendingFollowUps = result.followUps
            callStatsMap = result.stats
            callingMetrics = result.callingMetrics
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        isUserLoggedIn = AppConfig.isLoggedIn
        if (isUserLoggedIn) {
            refreshEnquiries()
        } else {
            enquiries = emptyList()
            isLoading = false
        }
    }

    LaunchedEffect(searchQuery) {
        if (isUserLoggedIn) {
            refreshEnquiries()
        }
    }

    LaunchedEffect(reportPeriod) {
        if (isUserLoggedIn) {
            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val updatedMetrics = dbHelper.getCallingReportMetrics(reportPeriod)
                callingMetrics = updatedMetrics
            }
        }
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

    val calendar = remember { java.util.Calendar.getInstance() }
    val todayYear = remember { calendar.get(java.util.Calendar.YEAR) }
    val todayDayOfYear = remember { calendar.get(java.util.Calendar.DAY_OF_YEAR) }
    val todayYmd = remember { String.format(java.util.Locale.US, "%04d-%02d-%02d", todayYear, calendar.get(java.util.Calendar.MONTH) + 1, calendar.get(java.util.Calendar.DAY_OF_MONTH)) }
    val todayDmy = remember { String.format(java.util.Locale.US, "%02d/%02d/%04d", calendar.get(java.util.Calendar.DAY_OF_MONTH), calendar.get(java.util.Calendar.MONTH) + 1, todayYear) }
    val todayDmyDash = remember { String.format(java.util.Locale.US, "%02d-%02d-%04d", calendar.get(java.util.Calendar.DAY_OF_MONTH), calendar.get(java.util.Calendar.MONTH) + 1, todayYear) }

    fun isDateToday(epochMs: Long?): Boolean {
        if (epochMs == null || epochMs <= 0) return false
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = epochMs }
        return cal.get(java.util.Calendar.YEAR) == todayYear &&
                cal.get(java.util.Calendar.DAY_OF_YEAR) == todayDayOfYear
    }

    fun isDemoSlotToday(demoSlot: String?, followUpDate: Long?, demoStatus: String?): Boolean {
        if (!demoSlot.isNullOrBlank()) {
            if (demoSlot.contains(todayYmd) || demoSlot.contains(todayDmy) || demoSlot.contains(todayDmyDash) || demoSlot.contains("today", ignoreCase = true)) {
                return true
            }
            if (!demoSlot.contains("-") && !demoSlot.contains("/")) {
                if (followUpDate == null || isDateToday(followUpDate)) {
                    return true
                }
            }
        }
        if (isDateToday(followUpDate) && (demoStatus.equals("Booked", true) || demoStatus.equals("Scheduled", true))) {
            return true
        }
        return false
    }

    val startOfToday = remember {
        java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val endOfToday = remember {
        java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 23)
            set(java.util.Calendar.MINUTE, 59)
            set(java.util.Calendar.SECOND, 59)
            set(java.util.Calendar.MILLISECOND, 999)
        }.timeInMillis
    }

    // Lead and customer profile enquiries displayed based on User ID logged in OR Center ID
    val scopedEnquiries = remember(enquiries, AppConfig.CURRENT_USER_ID, AppConfig.CENTER_ID) {
        enquiries.filter { enq ->
            val matchesUser = !enq.assignedTo.isNullOrBlank() && (
                enq.assignedTo == AppConfig.CURRENT_USER_ID ||
                enq.assignedTo.equals(AppConfig.CURRENT_USER_NAME, ignoreCase = true)
            )
            val matchesCenter = enq.centerId.isNullOrBlank() || enq.centerId == AppConfig.CENTER_ID
            matchesUser || matchesCenter
        }
    }

    // 1. Today's Scheduled Demos: Enquiries displayed based on demoSlot and if the date is current
    val todayScheduledDemoEnquiries = remember(scopedEnquiries, pendingFollowUps, searchQuery) {
        val list = scopedEnquiries.filter { enq ->
            val matchesDemoSlot = isDemoSlotToday(enq.demoSlot, enq.followUpDate, enq.demoStatus)
            val matchesFollowUpDemo = pendingFollowUps.any { fu ->
                PhoneNumberUtils.areNumbersEqual(fu.phoneNumber, enq.phoneNumber) &&
                (fu.note.contains("demo", ignoreCase = true) || fu.note.contains("slot", ignoreCase = true)) &&
                isDateToday(fu.scheduledTimeEpochMs)
            }
            matchesDemoSlot || matchesFollowUpDemo || (enq.status.equals("Demo Booked", true) && (enq.followUpDate == null || isDateToday(enq.followUpDate)))
        }
        if (searchQuery.isBlank()) list
        else list.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.phoneNumber.contains(searchQuery) ||
            (it.course?.contains(searchQuery, ignoreCase = true) == true) ||
            (it.demoSlot?.contains(searchQuery, ignoreCase = true) == true)
        }
    }

    // 2. Below the same session: Enquiries displayed based on followUpDate is today
    val todayFollowUpsBelowDemo = remember(scopedEnquiries, todayScheduledDemoEnquiries, pendingFollowUps, searchQuery) {
        val demoIds = todayScheduledDemoEnquiries.map { it.id }.toSet()
        val list = scopedEnquiries.filter { enq ->
            !demoIds.contains(enq.id) && (
                isDateToday(enq.followUpDate) ||
                pendingFollowUps.any { fu ->
                    PhoneNumberUtils.areNumbersEqual(fu.phoneNumber, enq.phoneNumber) &&
                    !fu.isCompleted &&
                    isDateToday(fu.scheduledTimeEpochMs)
                }
            )
        }
        if (searchQuery.isBlank()) list
        else list.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.phoneNumber.contains(searchQuery) ||
            (it.course?.contains(searchQuery, ignoreCase = true) == true) ||
            (it.notes?.contains(searchQuery, ignoreCase = true) == true)
        }
    }

    // 3. Due Follow-up Session: Enquiries displayed based on followUpDate is current in descending order
    val dueFollowUpEnquiries = remember(scopedEnquiries, pendingFollowUps, searchQuery) {
        val list = scopedEnquiries.filter { enq ->
            val hasFuDate = isDateToday(enq.followUpDate) || (enq.followUpDate != null && enq.followUpDate <= endOfToday && !enq.status.equals("Converted", true) && !enq.status.equals("Enrolled", true))
            val hasPending = pendingFollowUps.any { fu ->
                PhoneNumberUtils.areNumbersEqual(fu.phoneNumber, enq.phoneNumber) &&
                !fu.isCompleted &&
                fu.scheduledTimeEpochMs <= endOfToday
            }
            hasFuDate || hasPending
        }.sortedByDescending { enq ->
            val fuEpoch = pendingFollowUps.firstOrNull { PhoneNumberUtils.areNumbersEqual(it.phoneNumber, enq.phoneNumber) }?.scheduledTimeEpochMs
            maxOf(enq.followUpDate ?: 0L, fuEpoch ?: 0L)
        }
        if (searchQuery.isBlank()) list
        else list.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.phoneNumber.contains(searchQuery) ||
            (it.course?.contains(searchQuery, ignoreCase = true) == true) ||
            (it.notes?.contains(searchQuery, ignoreCase = true) == true)
        }
    }

    val filteredList = remember(scopedEnquiries, selectedStatusFilter, selectedUserFilter, AppConfig.CURRENT_USER_ID) {
        val byFilter = when {
            selectedStatusFilter.startsWith("Call Queue") -> scopedEnquiries.filter { it.inCallQueue }
            selectedStatusFilter.startsWith("★ Favourites") -> scopedEnquiries.filter { it.isFavorite }
            selectedStatusFilter == "All" -> scopedEnquiries
            else -> scopedEnquiries.filter { it.status.equals(selectedStatusFilter, ignoreCase = true) }
        }

        when (selectedUserFilter) {
            "MY_LEADS" -> byFilter.filter {
                val assigned = it.assignedTo
                assigned.isNullOrBlank() || assigned == AppConfig.CURRENT_USER_ID || assigned.equals(AppConfig.CURRENT_USER_NAME, ignoreCase = true)
            }
            "ALL" -> byFilter
            else -> byFilter.filter {
                it.assignedTo == selectedUserFilter || it.assignedTo?.contains(selectedUserFilter, ignoreCase = true) == true
            }
        }
    }

    val filterOptions = listOf(
        "All",
        "Call Queue (${scopedEnquiries.count { it.inCallQueue }})",
        "★ Favourites (${scopedEnquiries.count { it.isFavorite }})",
        "Interested",
        "Follow-up",
        "Demo Booked",
        "Converted",
        "Enrolled",
        "New Lead",
        "Call Later",
        "No Response",
        "Wrong Number",
        "Lost",
        "Spam"
    )

    if (!isUserLoggedIn) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = CoralRed, modifier = Modifier.size(20.dp))
                            Text("Leads & CRM (Session Locked)", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                    },
                    actions = {
                        TextButton(onClick = { showUserSwitcherDialog = true }) {
                            Icon(Icons.Default.AdminPanelSettings, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Managers")
                        }
                    }
                )
            },
            modifier = modifier
        ) { padding ->
            CrmLoginView(
                onLoginSuccess = { user ->
                    isUserLoggedIn = true
                    refreshEnquiries()
                },
                modifier = Modifier.padding(padding)
            )
        }

        if (showUserSwitcherDialog) {
            UserSwitcherDialog(
                onDismiss = { showUserSwitcherDialog = false },
                onUserSwitched = { user ->
                    showUserSwitcherDialog = false
                    isUserLoggedIn = AppConfig.isLoggedIn
                    refreshEnquiries()
                },
                onLogout = {
                    showUserSwitcherDialog = false
                    isUserLoggedIn = false
                    enquiries = emptyList()
                }
            )
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Leads & CRM", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                actions = {
                    IconButton(onClick = {
                        selectedEnquiryForSchedule = null
                        showScheduleDialog = true
                    }) {
                        Icon(Icons.Default.DateRange, contentDescription = "Schedule Demo", tint = TrueBlue)
                    }
                    IconButton(onClick = { showAddEnquiryDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Lead", tint = TrueBlue)
                    }
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                isCloudSyncing = true
                                Toast.makeText(context, "Syncing with MongoDB Atlas...", Toast.LENGTH_SHORT).show()
                                val res = MongoAtlasSyncManager.syncAll(context)
                                isCloudSyncing = false
                                refreshEnquiries()
                                Toast.makeText(context, if (res.success) "MongoDB: ${res.message}" else "Sync Failed: ${res.message}", Toast.LENGTH_LONG).show()
                            }
                        },
                        enabled = !isCloudSyncing
                    ) {
                        if (isCloudSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                        } else {
                            Icon(Icons.Default.CloudSync, contentDescription = "Sync to MongoDB Atlas", tint = EmeraldGreen)
                        }
                    }
                    UserProfileBadge(onClick = { showUserSwitcherDialog = true })
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
            // Clean Unified Header Card for Search & Filtering
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp
            ) {
                Column(modifier = Modifier.padding(bottom = 6.dp)) {
                    // Search Input
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        placeholder = { Text("Search by name, phone, course...", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Center & User Scope Filter
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Scope:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.outline
                        )
                        FilterChip(
                            selected = selectedUserFilter == "ALL",
                            onClick = {
                                selectedUserFilter = "ALL"
                                refreshEnquiries()
                            },
                            label = { Text("All Leads (${AppConfig.CENTER_NAME})", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = selectedUserFilter == "MY_LEADS",
                            onClick = {
                                selectedUserFilter = "MY_LEADS"
                                refreshEnquiries()
                            },
                            label = { Text("My Leads", fontSize = 11.sp) }
                        )
                        if (AppConfig.isCurrentUserManager) {
                            centerUsers.filter { !it.isManager }.forEach { staff ->
                                FilterChip(
                                    selected = selectedUserFilter == staff.name || selectedUserFilter == staff.id,
                                    onClick = {
                                        selectedUserFilter = staff.name
                                        refreshEnquiries()
                                    },
                                    label = { Text(staff.name.substringAfter("- ").trim(), fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    // Status & Queue Filter Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        filterOptions.forEach { status ->
                            val isQueue = status.startsWith("Call Queue")
                            val isFav = status.startsWith("★ Favourites")
                            FilterChip(
                                selected = selectedStatusFilter == status,
                                onClick = { selectedStatusFilter = status },
                                label = { Text(status, fontSize = 11.sp) },
                                colors = if (isQueue && selectedStatusFilter == status) {
                                    FilterChipDefaults.filterChipColors(selectedContainerColor = EmeraldGreen.copy(alpha = 0.2f))
                                } else if (isFav && selectedStatusFilter == status) {
                                    FilterChipDefaults.filterChipColors(selectedContainerColor = AmberOrange.copy(alpha = 0.2f))
                                } else {
                                    FilterChipDefaults.filterChipColors()
                                }
                            )
                        }
                    }
                }
            }

            // Call Queue Banner (if Call Queue is selected)
            if (selectedStatusFilter.startsWith("Call Queue") && filteredList.isNotEmpty()) {
                Surface(
                    color = EmeraldGreen.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val nextLead = filteredList.first()
                        Column(modifier = Modifier.weight(1f)) {
                            Text("CALL QUEUE ACTIVE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = EmeraldGreen)
                            Text("Next: ${nextLead.name} (${PhoneNumberUtils.formatDisplay(nextLead.phoneNumber)})", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Button(
                            onClick = { onCallNumber(nextLead.phoneNumber) },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Call Next", fontSize = 12.sp)
                        }
                    }
                }
            }

            // Results List
            Box(modifier = Modifier.fillMaxSize()) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 1. SECTION: Calling Reports & Lead Pipeline Dashboard
                        item {
                            CallingReportsCard(
                                metrics = callingMetrics,
                                selectedPeriod = reportPeriod,
                                onPeriodSelected = { period ->
                                    reportPeriod = period
                                }
                            )
                        }

                        // 2. SECTION: Today's Scheduled Demos (by demoSlot & current date) and below it Today's Follow-ups
                        item {
                            TodayScheduledDemosCard(
                                demoEnquiries = todayScheduledDemoEnquiries,
                                todayFollowUps = todayFollowUpsBelowDemo,
                                pendingFollowUps = pendingFollowUps,
                                onCallClick = onCallNumber,
                                onWhatsAppClick = { openWhatsAppDirectly(it.phoneNumber) },
                                onCompleteDemo = { enq ->
                                    coroutineScope.launch {
                                        dbHelper.updateEnquiryStatus(enq.id, "Demo Attended")
                                        val matchingFu = pendingFollowUps.filter { PhoneNumberUtils.areNumbersEqual(it.phoneNumber, enq.phoneNumber) }
                                        matchingFu.forEach { dbHelper.markFollowUpComplete(it.id) }
                                        Toast.makeText(context, "Demo marked as attended for ${enq.name}", Toast.LENGTH_SHORT).show()
                                        refreshEnquiries()
                                    }
                                },
                                onCompleteFollowUp = { enq ->
                                    coroutineScope.launch {
                                        dbHelper.updateEnquiryStatus(enq.id, "Followed Up")
                                        val matchingFu = pendingFollowUps.filter { PhoneNumberUtils.areNumbersEqual(it.phoneNumber, enq.phoneNumber) }
                                        matchingFu.forEach { dbHelper.markFollowUpComplete(it.id) }
                                        Toast.makeText(context, "Follow-up completed for ${enq.name}", Toast.LENGTH_SHORT).show()
                                        refreshEnquiries()
                                    }
                                },
                                onScheduleNewClick = {
                                    selectedEnquiryForSchedule = null
                                    showScheduleDialog = true
                                }
                            )
                        }

                        // 3. SECTION: Due Follow-ups (by followUpDate in descending order)
                        item {
                            TodayFollowUpsCard(
                                dueEnquiries = dueFollowUpEnquiries,
                                pendingFollowUps = pendingFollowUps,
                                onCallClick = onCallNumber,
                                onWhatsAppClick = { openWhatsAppDirectly(it.phoneNumber) },
                                onCompleteClick = { enq ->
                                    coroutineScope.launch {
                                        dbHelper.updateEnquiryStatus(enq.id, "Followed Up")
                                        val matchingFu = pendingFollowUps.filter { PhoneNumberUtils.areNumbersEqual(it.phoneNumber, enq.phoneNumber) }
                                        matchingFu.forEach { dbHelper.markFollowUpComplete(it.id) }
                                        Toast.makeText(context, "Follow-up marked completed for ${enq.name}", Toast.LENGTH_SHORT).show()
                                        refreshEnquiries()
                                    }
                                },
                                onScheduleNewClick = {
                                    selectedEnquiryForSchedule = null
                                    showScheduleDialog = true
                                }
                            )
                        }

                        // 4. Section Header for Leads
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp, bottom = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Leads & Customer Profiles (${filteredList.size})",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (selectedStatusFilter != "All") {
                                    Text(
                                        text = selectedStatusFilter,
                                        fontSize = 12.sp,
                                        color = TrueBlue,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        if (filteredList.isEmpty()) {
                            item {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = if (searchQuery.isNotBlank()) "No enquiries match \"$searchQuery\"" else "No leads found in this view.",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        } else {
                            items(filteredList, key = { it.id }) { item ->
                                val clean = PhoneNumberUtils.extractNationalNumber(item.phoneNumber)
                                val callStat = callStatsMap[clean] ?: callStatsMap[item.phoneNumber.replace("[^0-9]".toRegex(), "")]
                                EnquiryCard(
                                    enquiry = item,
                                    callStat = callStat,
                                    onCallClick = { onCallNumber(item.phoneNumber) },
                                    onWhatsAppClick = { openWhatsAppDirectly(item.phoneNumber) },
                                    onSaveContactClick = { selectedEnquiryForSave = item },
                                    onEditClick = { selectedEnquiryForEdit = item },
                                    onScheduleClick = {
                                        selectedEnquiryForSchedule = item
                                        showScheduleDialog = true
                                    },
                                    onAssignClick = {
                                        selectedEnquiryForAssign = item
                                    },
                                    onToggleFavorite = {
                                        coroutineScope.launch {
                                            dbHelper.toggleFavoriteEnquiry(item.id, !item.isFavorite)
                                            refreshEnquiries()
                                        }
                                    },
                                    onToggleCallQueue = {
                                        coroutineScope.launch {
                                            dbHelper.toggleCallQueue(item.id, !item.inCallQueue)
                                            refreshEnquiries()
                                        }
                                    },
                                    onCopyOptionsClick = {
                                        selectedEnquiryForCopy = item
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Long-Press Copy Selection Dialog
        selectedEnquiryForCopy?.let { item ->
            CopySelectionDialog(
                name = item.name,
                phoneNumber = item.phoneNumber,
                onDismiss = { selectedEnquiryForCopy = null }
            )
        }

        // Save Contact Dialog
        selectedEnquiryForSave?.let { item ->
            SaveContactDialog(
                initialPhoneNumber = item.phoneNumber,
                initialEnquiry = item,
                onDismiss = { selectedEnquiryForSave = null },
                onSaveDirectly = { name, phone, company, email ->
                    coroutineScope.launch {
                        val success = contactsRepo.saveContactDirectly(name, phone, company, email)
                        if (success) {
                            Toast.makeText(context, "Saved $name to device contacts & synced to database!", Toast.LENGTH_SHORT).show()
                            dbHelper.updateEnquiryNameByPhone(phone, name)
                            MongoAtlasSyncManager.updateLeadName(phone, name)
                            refreshEnquiries()
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

        // Edit Enquiry Dialog
        selectedEnquiryForEdit?.let { item ->
            EditEnquiryDialog(
                enquiry = item,
                onDismiss = { selectedEnquiryForEdit = null },
                onSave = { updated ->
                    coroutineScope.launch {
                        val saved = enquiryRepo.saveEnquiry(updated)
                        if (saved) {
                            if (updated.name != item.name) {
                                contactsRepo.updateContactName(updated.id, updated.name)
                                MongoAtlasSyncManager.updateLeadName(updated.phoneNumber, updated.name)
                            }
                            if (updated.status != "Wrong Number") {
                                MongoAtlasSyncManager.autoSyncLead(updated)
                                CrmWebhookSync.syncLead(updated)
                            }
                            Toast.makeText(context, "Lead profile updated & synced to database", Toast.LENGTH_SHORT).show()
                            refreshEnquiries()
                        }
                    }
                }
            )
        }

        // Add Enquiry Dialog
        if (showAddEnquiryDialog) {
            AddEnquiryDialog(
                onDismiss = { showAddEnquiryDialog = false },
                onAdd = { newEnquiry, saveToContacts ->
                    coroutineScope.launch {
                        if (saveToContacts) {
                            contactsRepo.saveContactDirectly(newEnquiry.name, newEnquiry.phoneNumber, newEnquiry.company, null)
                        }
                        val saved = enquiryRepo.saveEnquiry(newEnquiry)
                        if (saved) {
                            if (newEnquiry.status != "Wrong Number") {
                                MongoAtlasSyncManager.autoSyncLead(newEnquiry)
                                CrmWebhookSync.syncLead(newEnquiry)
                            }
                            Toast.makeText(context, "Lead saved & auto-synced to database!", Toast.LENGTH_SHORT).show()
                            refreshEnquiries()
                        }
                    }
                }
            )
        }

        // Schedule Demo / Follow-up Dialog
        if (showScheduleDialog) {
            ScheduleTaskDialog(
                initialEnquiry = selectedEnquiryForSchedule,
                enquiriesList = enquiries,
                onDismiss = {
                    showScheduleDialog = false
                    selectedEnquiryForSchedule = null
                },
                onSchedule = { reminder, person ->
                    dbHelper.saveFollowUp(reminder)
                    scheduleAlarm(context, reminder)
                    if (person != null) {
                        coroutineScope.launch {
                            val updated = person.copy(
                                demoStatus = if (reminder.note.contains("demo", ignoreCase = true)) "Booked" else person.demoStatus,
                                status = if (reminder.note.contains("demo", ignoreCase = true) && (person.status == "New" || person.status == "Contacted")) "Demo Booked" else person.status
                            )
                            dbHelper.insertOrUpdateEnquiry(updated)
                            refreshEnquiries()
                        }
                    }
                    Toast.makeText(context, "Task scheduled for ${reminder.customerName}!", Toast.LENGTH_SHORT).show()
                    refreshEnquiries()
                }
            )
        }

        // User & Profile Switcher Modal (Shared Phone)
        if (showUserSwitcherDialog) {
            UserSwitcherDialog(
                onDismiss = { showUserSwitcherDialog = false },
                onUserSwitched = {
                    showUserSwitcherDialog = false
                    isUserLoggedIn = AppConfig.isLoggedIn
                    refreshEnquiries()
                },
                onLogout = {
                    showUserSwitcherDialog = false
                    isUserLoggedIn = false
                    enquiries = emptyList()
                }
            )
        }

        // Lead Assignment Dialog
        selectedEnquiryForAssign?.let { lead ->
            AlertDialog(
                onDismissRequest = { selectedEnquiryForAssign = null },
                icon = { Icon(Icons.Default.AssignmentInd, contentDescription = null, tint = TrueBlue) },
                title = { Text("Assign Lead: ${lead.name}", fontSize = 17.sp, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Select a center staff member to assign this lead to:",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.heightIn(max = 240.dp)
                        ) {
                            items(centerUsers.filter { !it.isManager }) { staff ->
                                val isAssigned = lead.assignedTo == staff.name || lead.assignedTo == staff.id
                                Card(
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isAssigned) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val updated = lead.copy(assignedTo = staff.name)
                                            coroutineScope.launch {
                                                enquiryRepo.saveEnquiry(updated)
                                                CrmWebhookSync.syncLead(updated)
                                                refreshEnquiries()
                                                selectedEnquiryForAssign = null
                                                Toast.makeText(context, "Lead assigned to ${staff.name}", Toast.LENGTH_SHORT).show()
                                            }
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
                                            Text("${staff.role.displayName} • ${staff.id}", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                        }
                                        if (isAssigned) {
                                            Icon(Icons.Default.Check, contentDescription = "Assigned", tint = EmeraldGreen)
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { selectedEnquiryForAssign = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

/**
 * Lead Pipeline & Calling Reports Dashboard Card
 */
@Composable
fun CallingReportsCard(
    metrics: CallingReportMetrics,
    selectedPeriod: String,
    onPeriodSelected: (String) -> Unit
) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header Row: clickable to expand/collapse
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.BarChart, contentDescription = null, tint = TrueBlue, modifier = Modifier.size(18.dp))
                    Text(
                        text = "CALLING & PIPELINE REPORTS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TrueBlue
                    )
                }

                IconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = TrueBlue
                    )
                }
            }

            if (!isExpanded) {
                val periodLabel = when (selectedPeriod.uppercase()) {
                    "WEEKLY" -> "Weekly"
                    "MONTHLY" -> "Monthly"
                    else -> "Today"
                }
                // Collapsed Compact Summary (clean single line!)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isExpanded = true },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$periodLabel: ${metrics.totalCalls} Calls (${metrics.connectedCalls} Connected) • ${metrics.demoBookings} Demos",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Details",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TrueBlue
                    )
                }
            } else {
                // Period selection chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    listOf("Daily" to "TODAY", "Weekly" to "WEEKLY", "Monthly" to "MONTHLY").forEach { (label, key) ->
                        FilterChip(
                            selected = selectedPeriod == key,
                            onClick = { onPeriodSelected(key) },
                            label = { Text(label, fontSize = 10.sp) },
                            modifier = Modifier.height(28.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                }

                // Top Primary Metric Tiles (Total calls, Avg talk duration, Connected calls)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MetricTile(
                        title = "Total Calls",
                        value = "${metrics.totalCalls}",
                        color = TrueBlue,
                        modifier = Modifier.weight(1f)
                    )
                    MetricTile(
                        title = "Avg Duration",
                        value = CallRecordingUtils.formatDuration(metrics.avgDurationSeconds),
                        color = EmeraldGreen,
                        modifier = Modifier.weight(1f)
                    )
                    MetricTile(
                        title = "Connected",
                        value = "${metrics.connectedCalls}",
                        color = AmberOrange,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Call Direction Split (Outgoing, Incoming, Missed)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MetricSubTile(title = "Outgoing", value = "${metrics.outgoingCalls}", icon = Icons.Default.CallMade, tint = EmeraldGreen, modifier = Modifier.weight(1f))
                    MetricSubTile(title = "Incoming", value = "${metrics.incomingCalls}", icon = Icons.Default.CallReceived, tint = TrueBlue, modifier = Modifier.weight(1f))
                    MetricSubTile(title = "Missed", value = "${metrics.missedCalls}", icon = Icons.Default.CallMissed, tint = CoralRed, modifier = Modifier.weight(1f))
                }

                // Conversion & Pipeline Progress: 2x2 grid so nothing is crushed
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MetricSubTile(title = "Follow-ups", value = "${metrics.followUpsPending}", icon = Icons.Default.Alarm, tint = AmberOrange, modifier = Modifier.weight(1f))
                    MetricSubTile(title = "New Leads", value = "${metrics.newEnquiries}", icon = Icons.Default.PersonAdd, tint = TrueBlue, modifier = Modifier.weight(1f))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MetricSubTile(title = "Demos", value = "${metrics.demoBookings}", icon = Icons.Default.DateRange, tint = Color(0xFF8E24AA), modifier = Modifier.weight(1f))
                    MetricSubTile(title = "Converted", value = "${metrics.convertedCount}", icon = Icons.Default.CheckCircle, tint = EmeraldGreen, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun MetricTile(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
            Text(title, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun MetricSubTile(
    title: String,
    value: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(13.dp))
            Column {
                Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(title, fontSize = 9.sp, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EnquiryCard(
    enquiry: Enquiry,
    callStat: CallStatRecord? = null,
    onCallClick: () -> Unit,
    onWhatsAppClick: () -> Unit,
    onSaveContactClick: () -> Unit,
    onEditClick: () -> Unit,
    onScheduleClick: () -> Unit,
    onAssignClick: () -> Unit = {},
    onToggleFavorite: () -> Unit = {},
    onToggleCallQueue: () -> Unit = {},
    onCopyOptionsClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onEditClick,
                onLongClick = onCopyOptionsClick
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Name & Status Badge & Star/Queue Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = enquiry.name,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (enquiry.isFavorite) {
                            Text("★", fontSize = 14.sp, color = AmberOrange)
                        }
                    }
                    if (!enquiry.company.isNullOrBlank()) {
                        Text(
                            text = enquiry.company,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    EnquiryBadge(status = enquiry.status)

                    // Star / Favorite Toggle
                    IconButton(onClick = onToggleFavorite, modifier = Modifier.size(30.dp)) {
                        Icon(
                            imageVector = if (enquiry.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Favorite",
                            tint = if (enquiry.isFavorite) AmberOrange else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Call Queue Toggle
                    IconButton(onClick = onToggleCallQueue, modifier = Modifier.size(30.dp)) {
                        Icon(
                            imageVector = if (enquiry.inCallQueue) Icons.Default.PlaylistAddCheck else Icons.Default.PlaylistAdd,
                            contentDescription = "Call Queue",
                            tint = if (enquiry.inCallQueue) EmeraldGreen else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Phone Number
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onCopyOptionsClick() }
                    .padding(vertical = 2.dp)
            ) {
                Text(
                    text = PhoneNumberUtils.formatDisplay(enquiry.phoneNumber),
                    fontSize = 14.sp,
                    color = TrueBlue,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Extended CRM Fields: Course, Age Category, Lead Source, Demo/Admission Status
            if (!enquiry.course.isNullOrBlank() || !enquiry.ageCategory.isNullOrBlank() || !enquiry.leadSource.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    enquiry.course?.let {
                        Surface(shape = RoundedCornerShape(6.dp), color = TrueBlue.copy(alpha = 0.12f)) {
                            Text(text = "📚 $it", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TrueBlue, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                    enquiry.ageCategory?.let {
                        Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                            Text(text = it, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                        }
                    }
                    enquiry.leadSource?.let {
                        Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                            Text(text = "Source: $it", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                        }
                    }
                }
            }

            // Demo Status & Admission Status Tags
            if (!enquiry.demoStatus.isNullOrBlank() || !enquiry.admissionStatus.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    enquiry.demoStatus?.let {
                        Text(text = "Demo: $it", fontSize = 11.sp, color = Color(0xFF8E24AA), fontWeight = FontWeight.Medium)
                    }
                    enquiry.admissionStatus?.let {
                        Text(text = "• Admission: $it", fontSize = 11.sp, color = EmeraldGreen, fontWeight = FontWeight.Medium)
                    }
                }
            }

            // Remarks
            if (!enquiry.remarks.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Remarks: ${enquiry.remarks}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            } else if (!enquiry.notes.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = enquiry.notes,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Ring / Talk Time Call Stats
            if (callStat != null) {
                Spacer(modifier = Modifier.height(6.dp))
                val ringSec = callStat.ringDurationSeconds
                val talkStr = CallRecordingUtils.formatDuration(callStat.talkDurationSeconds)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(text = "🔔 Ring: ${ringSec}s", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = AmberOrange)
                        Text(text = "•", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = "💬 Talked: $talkStr", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = EmeraldGreen)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons Bar (Edit, Task, Save, WhatsApp Direct, Call)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(
                        onClick = onEditClick,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("Edit", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = onScheduleClick,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("Task", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = onSaveContactClick,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("Save", fontSize = 11.sp)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Direct WhatsApp Action Button
                    FilledIconButton(
                        onClick = onWhatsAppClick,
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF25D366)),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Open WhatsApp", tint = Color.White, modifier = Modifier.size(16.dp))
                    }

                    // Direct Call Action Button
                    FilledIconButton(
                        onClick = onCallClick,
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

@Composable
fun EditEnquiryDialog(
    enquiry: Enquiry,
    onDismiss: () -> Unit,
    onSave: (Enquiry) -> Unit
) {
    var name by remember { mutableStateOf(enquiry.name) }
    var phone by remember { mutableStateOf(enquiry.phoneNumber) }
    var company by remember { mutableStateOf(enquiry.company ?: "") }
    var status by remember { mutableStateOf(enquiry.status) }
    var remarks by remember { mutableStateOf(enquiry.remarks ?: enquiry.notes ?: "") }

    val courseOptions = listOf("Python Full Stack", "Data Analytics", "Java Full Stack", "Digital Marketing", "MERN Stack", "Flutter App Dev", "Cloud & DevOps", "UI/UX Design")
    var selectedCourse by remember { mutableStateOf(enquiry.course ?: "Python Full Stack") }

    val ageCategories = listOf("School", "College / Graduate", "Working Professional", "Job Seeker")
    var selectedAgeCategory by remember { mutableStateOf(enquiry.ageCategory ?: "College / Graduate") }

    val leadSources = listOf("Website", "Google", "Instagram", "Walk-in", "Referral")
    var selectedLeadSource by remember { mutableStateOf(enquiry.leadSource ?: "Website") }

    val demoStatuses = listOf("None", "Booked", "Attended", "Missed")
    var selectedDemoStatus by remember { mutableStateOf(enquiry.demoStatus ?: "None") }

    val admissionStatuses = listOf("Pending", "Enrolled", "Not Interested")
    var selectedAdmissionStatus by remember { mutableStateOf(enquiry.admissionStatus ?: "Pending") }

    val outcomes = listOf("Interested", "Not Interested", "Call Later", "No Response", "Number Busy", "Switched Off", "Demo Booked", "Converted", "Wrong Number")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Customer Profile & Lead Details") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Full Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = company,
                    onValueChange = { company = it },
                    label = { Text("Place / Branch") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("COURSE INTERESTED", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    courseOptions.forEach { course ->
                        FilterChip(
                            selected = selectedCourse == course,
                            onClick = { selectedCourse = course },
                            label = { Text(course, fontSize = 10.sp) }
                        )
                    }
                }

                Text("AGE CATEGORY", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ageCategories.forEach { cat ->
                        FilterChip(
                            selected = selectedAgeCategory == cat,
                            onClick = { selectedAgeCategory = cat },
                            label = { Text(cat, fontSize = 10.sp) }
                        )
                    }
                }

                Text("LEAD SOURCE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    leadSources.forEach { src ->
                        FilterChip(
                            selected = selectedLeadSource == src,
                            onClick = { selectedLeadSource = src },
                            label = { Text(src, fontSize = 10.sp) }
                        )
                    }
                }

                Text("LEAD OUTCOME / STATUS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    outcomes.forEach { s ->
                        FilterChip(
                            selected = status == s,
                            onClick = { status = s },
                            label = { Text(s, fontSize = 10.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = remarks,
                    onValueChange = { remarks = it },
                    label = { Text("Remarks & Notes") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && phone.isNotBlank()) {
                        val updated = enquiry.copy(
                            name = name.trim(),
                            phoneNumber = phone.trim(),
                            company = company.ifBlank { null },
                            course = selectedCourse,
                            ageCategory = selectedAgeCategory,
                            leadSource = selectedLeadSource,
                            demoStatus = if (selectedDemoStatus != "None") selectedDemoStatus else null,
                            admissionStatus = selectedAdmissionStatus,
                            status = status,
                            remarks = remarks.ifBlank { null }
                        )
                        onSave(updated)
                        onDismiss()
                    }
                }
            ) {
                Text("Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun AddEnquiryDialog(
    onDismiss: () -> Unit,
    onAdd: (Enquiry, Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var company by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("New") }
    var remarks by remember { mutableStateOf("") }
    var saveToContacts by remember { mutableStateOf(true) }

    val courseOptions = listOf("Python Full Stack", "Data Analytics", "Java Full Stack", "Digital Marketing", "MERN Stack", "Flutter App Dev", "Cloud & DevOps", "UI/UX Design")
    var selectedCourse by remember { mutableStateOf("Python Full Stack") }

    val ageCategories = listOf("School", "College / Graduate", "Working Professional", "Job Seeker")
    var selectedAgeCategory by remember { mutableStateOf("College / Graduate") }

    val leadSources = listOf("Website", "Google", "Instagram", "Walk-in", "Referral")
    var selectedLeadSource by remember { mutableStateOf("Website") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Customer / Lead") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = company,
                    onValueChange = { company = it },
                    label = { Text("Place / Branch") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("COURSE INTERESTED", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    courseOptions.forEach { course ->
                        FilterChip(
                            selected = selectedCourse == course,
                            onClick = { selectedCourse = course },
                            label = { Text(course, fontSize = 10.sp) }
                        )
                    }
                }

                Text("AGE CATEGORY", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ageCategories.forEach { cat ->
                        FilterChip(
                            selected = selectedAgeCategory == cat,
                            onClick = { selectedAgeCategory = cat },
                            label = { Text(cat, fontSize = 10.sp) }
                        )
                    }
                }

                Text("LEAD SOURCE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    leadSources.forEach { src ->
                        FilterChip(
                            selected = selectedLeadSource == src,
                            onClick = { selectedLeadSource = src },
                            label = { Text(src, fontSize = 10.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = remarks,
                    onValueChange = { remarks = it },
                    label = { Text("Remarks") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = saveToContacts, onCheckedChange = { saveToContacts = it })
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Also save to device phonebook", fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && phone.isNotBlank()) {
                        val newEnq = Enquiry(
                            id = "ENQ-${System.currentTimeMillis() % 100000}",
                            name = name.trim(),
                            phoneNumber = phone.trim(),
                            company = company.ifBlank { null },
                            course = selectedCourse,
                            ageCategory = selectedAgeCategory,
                            leadSource = selectedLeadSource,
                            status = status,
                            remarks = remarks.ifBlank { null },
                            assignedTo = AppConfig.CURRENT_USER_NAME
                        )
                        onAdd(newEnq, saveToContacts)
                        onDismiss()
                    }
                }
            ) {
                Text("Add Customer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun TodayScheduledDemosCard(
    demoEnquiries: List<Enquiry>,
    todayFollowUps: List<Enquiry>,
    pendingFollowUps: List<FollowUpReminder>,
    onCallClick: (String) -> Unit,
    onWhatsAppClick: (Enquiry) -> Unit,
    onCompleteDemo: (Enquiry) -> Unit,
    onCompleteFollowUp: (Enquiry) -> Unit,
    onScheduleNewClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header for Scheduled Demos
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = TrueBlue.copy(alpha = 0.2f),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = null,
                                tint = TrueBlue,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Today's Scheduled Demos (${demoEnquiries.size})",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Demos scheduled by demo slot for current date",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                TextButton(onClick = onScheduleNewClick) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("Schedule", fontSize = 12.sp)
                }
            }

            if (demoEnquiries.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No demos scheduled yet for today.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    demoEnquiries.forEach { demo ->
                        val matchingFu = pendingFollowUps.firstOrNull { PhoneNumberUtils.areNumbersEqual(it.phoneNumber, demo.phoneNumber) }
                        val slotDisplay = when {
                            !demo.demoSlot.isNullOrBlank() -> demo.demoSlot
                            matchingFu != null -> java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(matchingFu.scheduledTimeEpochMs))
                            demo.followUpDate != null -> java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(demo.followUpDate))
                            else -> "Scheduled Today"
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { onCompleteDemo(demo) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.RadioButtonUnchecked,
                                        contentDescription = "Complete Demo",
                                        tint = TrueBlue
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = demo.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = TrueBlue.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = "🎯 $slotDisplay",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TrueBlue,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = PhoneNumberUtils.formatDisplay(demo.phoneNumber),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (!demo.course.isNullOrBlank()) {
                                        Text(
                                            text = "Course: ${demo.course}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (!demo.notes.isNullOrBlank() && !demo.notes.contains("Imported from", true)) {
                                        Text(
                                            text = demo.notes,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    FilledIconButton(
                                        onClick = { onWhatsAppClick(demo) },
                                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = EmeraldGreen),
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "WhatsApp", tint = Color.White, modifier = Modifier.size(15.dp))
                                    }

                                    FilledIconButton(
                                        onClick = { onCallClick(demo.phoneNumber) },
                                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = TrueBlue),
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Call, contentDescription = "Call", tint = Color.White, modifier = Modifier.size(15.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Below the same section: Enquiries based on followUpDate is today
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                thickness = 1.dp,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Event,
                    contentDescription = null,
                    tint = AmberOrange,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Today's Follow-ups (${todayFollowUps.size})",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "• Follow-up date is today",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (todayFollowUps.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No other enquiries with follow-up date today.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    todayFollowUps.forEach { fuEnq ->
                        val matchingFu = pendingFollowUps.firstOrNull { PhoneNumberUtils.areNumbersEqual(it.phoneNumber, fuEnq.phoneNumber) }
                        val timeStr = when {
                            matchingFu != null -> java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(matchingFu.scheduledTimeEpochMs))
                            fuEnq.followUpDate != null -> java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(fuEnq.followUpDate))
                            else -> "Today"
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { onCompleteFollowUp(fuEnq) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.RadioButtonUnchecked,
                                        contentDescription = "Complete Follow-up",
                                        tint = AmberOrange
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = fuEnq.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = AmberOrange.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = "⏰ $timeStr",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = AmberOrange,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = PhoneNumberUtils.formatDisplay(fuEnq.phoneNumber),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (!fuEnq.course.isNullOrBlank()) {
                                        Text(
                                            text = "Course: ${fuEnq.course}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    FilledIconButton(
                                        onClick = { onWhatsAppClick(fuEnq) },
                                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = EmeraldGreen),
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "WhatsApp", tint = Color.White, modifier = Modifier.size(15.dp))
                                    }

                                    FilledIconButton(
                                        onClick = { onCallClick(fuEnq.phoneNumber) },
                                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = TrueBlue),
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Call, contentDescription = "Call", tint = Color.White, modifier = Modifier.size(15.dp))
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

@Composable
fun TodayFollowUpsCard(
    dueEnquiries: List<Enquiry>,
    pendingFollowUps: List<FollowUpReminder>,
    onCallClick: (String) -> Unit,
    onWhatsAppClick: (Enquiry) -> Unit,
    onCompleteClick: (Enquiry) -> Unit,
    onScheduleNewClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = AmberOrange.copy(alpha = 0.2f),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Alarm,
                                contentDescription = null,
                                tint = AmberOrange,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Due Follow-ups (${dueEnquiries.size})",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Current follow-ups • Sorted descending",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                TextButton(onClick = onScheduleNewClick) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("Add", fontSize = 12.sp)
                }
            }

            if (dueEnquiries.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "🎉 All follow-ups are completed for today!",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    dueEnquiries.forEach { enq ->
                        val matchingFu = pendingFollowUps.firstOrNull { PhoneNumberUtils.areNumbersEqual(it.phoneNumber, enq.phoneNumber) }
                        val fuEpoch = matchingFu?.scheduledTimeEpochMs ?: enq.followUpDate ?: 0L
                        val isOverdue = fuEpoch > 0 && fuEpoch < System.currentTimeMillis() - 15 * 60 * 1000L
                        val timeStr = if (fuEpoch > 0) {
                            java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(fuEpoch))
                        } else "Today"

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { onCompleteClick(enq) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.RadioButtonUnchecked,
                                        contentDescription = "Complete Follow-up",
                                        tint = if (isOverdue) AmberOrange else MaterialTheme.colorScheme.primary
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = enq.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (isOverdue) CoralRed.copy(alpha = 0.15f) else AmberOrange.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = if (isOverdue) "⚠️ Overdue ($timeStr)" else "⏰ Today $timeStr",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isOverdue) CoralRed else AmberOrange,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = PhoneNumberUtils.formatDisplay(enq.phoneNumber),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (!enq.course.isNullOrBlank()) {
                                        Text(
                                            text = "Course: ${enq.course}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    val noteText = matchingFu?.note ?: enq.remarks ?: enq.notes
                                    if (!noteText.isNullOrBlank() && !noteText.contains("Imported from", true)) {
                                        Text(
                                            text = noteText,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    FilledIconButton(
                                        onClick = { onWhatsAppClick(enq) },
                                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = EmeraldGreen),
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "WhatsApp", tint = Color.White, modifier = Modifier.size(15.dp))
                                    }

                                    FilledIconButton(
                                        onClick = { onCallClick(enq.phoneNumber) },
                                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = TrueBlue),
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Call, contentDescription = "Call", tint = Color.White, modifier = Modifier.size(15.dp))
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

@Composable
fun ScheduleTaskDialog(
    initialEnquiry: Enquiry? = null,
    enquiriesList: List<Enquiry> = emptyList(),
    onDismiss: () -> Unit,
    onSchedule: (FollowUpReminder, Enquiry?) -> Unit
) {
    var selectedEnquiry by remember { mutableStateOf<Enquiry?>(initialEnquiry) }
    var searchQuery by remember { mutableStateOf("") }
    var isSelectingPerson by remember { mutableStateOf(initialEnquiry == null) }
    var isManualEntry by remember { mutableStateOf(false) }

    var manualName by remember { mutableStateOf("") }
    var manualPhone by remember { mutableStateOf("") }

    var taskType by remember { mutableStateOf("Scheduled Demo") }
    var selectedTimeSlot by remember { mutableStateOf("Today (in 1 hour)") }
    var note by remember {
        mutableStateOf(
            if (taskType == "Scheduled Demo") {
                if (initialEnquiry?.course != null) "Demo session for ${initialEnquiry.course}" else "Demo session for customer"
            } else "Callback requested"
        )
    }

    val filteredList = remember(searchQuery, enquiriesList) {
        if (searchQuery.isBlank()) enquiriesList
        else {
            val q = searchQuery.trim().lowercase()
            enquiriesList.filter {
                it.name.lowercase().contains(q) ||
                it.phoneNumber.contains(q) ||
                (it.course?.lowercase()?.contains(q) == true) ||
                (it.company?.lowercase()?.contains(q) == true)
            }
        }
    }

    val timeSlots = listOf(
        "Today (in 1 hour)",
        "Today (in 3 hours)",
        "Today (5:00 PM)",
        "Tomorrow 10 AM",
        "In 3 Days",
        "Next Week"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (taskType == "Scheduled Demo") "Schedule Demo" else "Schedule Follow-up Task", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Task Type Selector (Demo vs Follow-up)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = taskType == "Scheduled Demo",
                        onClick = {
                            taskType = "Scheduled Demo"
                            val course = selectedEnquiry?.course ?: ""
                            note = if (course.isNotBlank()) "Demo session for $course" else "Demo session for customer"
                        },
                        label = { Text("🎯 Demo") }
                    )
                    FilterChip(
                        selected = taskType == "Follow-up Callback",
                        onClick = {
                            taskType = "Follow-up Callback"
                            note = "Callback requested"
                        },
                        label = { Text("⏰ Follow-up") }
                    )
                }

                // PERSON SELECTION SECTION (From Enquiry List)
                if (!isManualEntry) {
                    if (selectedEnquiry != null && !isSelectingPerson) {
                        // Selected Person Display Card
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = TrueBlue.copy(alpha = 0.1f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, TrueBlue.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "SELECTED PERSON",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TrueBlue
                                    )
                                    Text(
                                        text = selectedEnquiry!!.name,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = PhoneNumberUtils.formatDisplay(selectedEnquiry!!.phoneNumber),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    val detail = selectedEnquiry!!.course ?: selectedEnquiry!!.company
                                    if (!detail.isNullOrBlank()) {
                                        Text(
                                            text = "Course: $detail",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = EmeraldGreen
                                        )
                                    }
                                }

                                TextButton(
                                    onClick = {
                                        isSelectingPerson = true
                                        searchQuery = ""
                                    }
                                ) {
                                    Text("Change", fontSize = 12.sp)
                                }
                            }
                        }
                    } else {
                        // Picker View: Search & Select from Enquiry List
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Select Person from Enquiry List:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search by name, phone, course...", fontSize = 12.sp) },
                                singleLine = true,
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            )

                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 160.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (filteredList.isEmpty()) {
                                    item {
                                        Text(
                                            text = "No enquiries found matching search.",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.padding(8.dp)
                                        )
                                    }
                                } else {
                                    items(filteredList) { enq ->
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (selectedEnquiry?.id == enq.id) TrueBlue.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    selectedEnquiry = enq
                                                    isSelectingPerson = false
                                                    val course = enq.course ?: ""
                                                    if (taskType == "Scheduled Demo") {
                                                        note = if (course.isNotBlank()) "Demo session for $course" else "Demo session for customer"
                                                    }
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(enq.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                                    Text(PhoneNumberUtils.formatDisplay(enq.phoneNumber), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                                val courseOrStatus = enq.course ?: enq.status
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = TrueBlue.copy(alpha = 0.15f)
                                                ) {
                                                    Text(
                                                        text = courseOrStatus,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = TrueBlue,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            TextButton(
                                onClick = { isManualEntry = true },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("+ Enter details manually", fontSize = 11.sp)
                            }
                        }
                    }
                } else {
                    // Manual entry fallback
                    OutlinedTextField(
                        value = manualName,
                        onValueChange = { manualName = it },
                        label = { Text("Customer Name *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = manualPhone,
                        onValueChange = { manualPhone = it },
                        label = { Text("Phone Number *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    TextButton(
                        onClick = { isManualEntry = false },
                        modifier = Modifier.align(Alignment.Start)
                    ) {
                        Text("← Select from enquiry list instead", fontSize = 11.sp)
                    }
                }

                // Time selector
                Text("When:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    timeSlots.forEach { slot ->
                        FilterChip(
                            selected = selectedTimeSlot == slot,
                            onClick = { selectedTimeSlot = slot },
                            label = { Text(slot, fontSize = 11.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Agenda / Note") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            val hasValidPerson = if (isManualEntry) {
                manualName.isNotBlank() && manualPhone.isNotBlank()
            } else {
                selectedEnquiry != null
            }

            Button(
                enabled = hasValidPerson,
                onClick = {
                    val finalName = if (isManualEntry) manualName else selectedEnquiry!!.name
                    val finalPhone = if (isManualEntry) manualPhone else selectedEnquiry!!.phoneNumber

                    val delayMs = when (selectedTimeSlot) {
                        "Today (in 1 hour)" -> 3600_000L
                        "Today (in 3 hours)" -> 3 * 3600_000L
                        "Today (5:00 PM)" -> {
                            val cal = java.util.Calendar.getInstance()
                            cal.set(java.util.Calendar.HOUR_OF_DAY, 17)
                            cal.set(java.util.Calendar.MINUTE, 0)
                            (cal.timeInMillis - System.currentTimeMillis()).coerceAtLeast(1800_000L)
                        }
                        "Tomorrow 10 AM" -> 86400_000L
                        "In 3 Days" -> 3 * 86400_000L
                        "Next Week" -> 7 * 86400_000L
                        else -> 3600_000L
                    }
                    val finalNote = if (taskType == "Scheduled Demo" && !note.contains("demo", ignoreCase = true)) {
                        "Demo: $note"
                    } else note

                    val reminder = FollowUpReminder(
                        id = "REM-${System.currentTimeMillis()}",
                        phoneNumber = finalPhone,
                        customerName = finalName,
                        note = finalNote,
                        scheduledTimeEpochMs = System.currentTimeMillis() + delayMs,
                        isCompleted = false,
                        assignedUserId = AppConfig.CURRENT_USER_ID
                    )
                    onSchedule(reminder, if (isManualEntry) null else selectedEnquiry)
                    onDismiss()
                }
            ) {
                Text("Schedule")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
