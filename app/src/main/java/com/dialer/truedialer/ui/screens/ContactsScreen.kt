package com.dialer.truedialer.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.dialer.truedialer.data.local.EnquiryDatabaseHelper
import com.dialer.truedialer.data.model.ContactRecord
import com.dialer.truedialer.data.model.Enquiry
import com.dialer.truedialer.data.repository.ContactsRepository
import com.dialer.truedialer.data.repository.EnquiryRepositoryProvider
import com.dialer.truedialer.sync.CrmWebhookSync
import com.dialer.truedialer.sync.MongoAtlasSyncManager
import com.dialer.truedialer.ui.components.CopySelectionDialog
import com.dialer.truedialer.ui.components.EnquiryBadge
import com.dialer.truedialer.ui.components.SyncEnquiryDialog
import com.dialer.truedialer.ui.theme.AmberOrange
import com.dialer.truedialer.ui.theme.CoralRed
import com.dialer.truedialer.ui.theme.EmeraldGreen
import com.dialer.truedialer.ui.theme.TrueBlue
import com.dialer.truedialer.util.PhoneNumberUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    onCallNumber: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val contactsRepo = remember { ContactsRepository(context) }
    val enquiryRepo = remember { EnquiryRepositoryProvider.get(context) }
    val dbHelper = remember { EnquiryDatabaseHelper(context) }

    var searchQuery by remember { mutableStateOf("") }
    var contacts by remember { mutableStateOf<List<ContactRecord>>(emptyList()) }
    var enquiriesMap by remember { mutableStateOf<Map<String, Enquiry>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }

    // Selected Contact Detail Dialog
    var selectedContact by remember { mutableStateOf<ContactRecord?>(null) }
    var selectedContactForCopy by remember { mutableStateOf<ContactRecord?>(null) }

    // Direct in-app Edit & Delete Contact states
    var contactToEdit by remember { mutableStateOf<ContactRecord?>(null) }
    var contactToDelete by remember { mutableStateOf<ContactRecord?>(null) }

    // Sync to Enquiry Dialog
    var syncContactForEnquiry by remember { mutableStateOf<ContactRecord?>(null) }

    // Filter: "ALL", "FAVOURITES", "ENQUIRY"
    var selectedFilter by remember { mutableStateOf("ALL") }

    fun loadData() {
        coroutineScope.launch {
            isLoading = true
            val (contactsList, map) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val list = contactsRepo.getContacts(searchQuery)
                val m = mutableMapOf<String, Enquiry>()
                try {
                    val allEnquiries = enquiryRepo.getAllEnquiries()
                    allEnquiries.forEach { enq ->
                        val cleanDigits = enq.phoneNumber.replace("[^0-9]".toRegex(), "")
                        val national = PhoneNumberUtils.extractNationalNumber(enq.phoneNumber)
                        if (cleanDigits.isNotBlank()) {
                            m[cleanDigits] = enq
                        }
                        if (national.isNotBlank()) {
                            m[national] = enq
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                Pair(list, m)
            }
            contacts = contactsList
            enquiriesMap = map
            isLoading = false
        }
    }

    LaunchedEffect(searchQuery) {
        loadData()
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

    val filteredContacts = remember(contacts, enquiriesMap, selectedFilter) {
        when (selectedFilter) {
            "FAVOURITES" -> contacts.filter { it.isStarred }
            "ENQUIRY" -> contacts.filter { contact ->
                val clean = contact.phoneNumber.replace("[^0-9]".toRegex(), "")
                val national = PhoneNumberUtils.extractNationalNumber(contact.phoneNumber)
                enquiriesMap.containsKey(clean) || enquiriesMap.containsKey(national)
            }
            else -> contacts
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Phone Contacts", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text(
                            text = "${filteredContacts.size} contacts found",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val intent = contactsRepo.createSaveContactIntent("", "")
                        context.startActivity(intent)
                    }) {
                        Icon(Icons.Default.PersonAdd, contentDescription = "Add Contact")
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
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                placeholder = { Text("Search by name or number...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // Filter Chips (All, Favourites, With Enquiry)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == "ALL",
                    onClick = { selectedFilter = "ALL" },
                    label = { Text("All (${contacts.size})", fontSize = 12.sp) }
                )
                val favCount = remember(contacts) { contacts.count { it.isStarred } }
                FilterChip(
                    selected = selectedFilter == "FAVOURITES",
                    onClick = { selectedFilter = "FAVOURITES" },
                    label = { Text("★ Favourites ($favCount)", fontSize = 12.sp) },
                    colors = if (selectedFilter == "FAVOURITES") FilterChipDefaults.filterChipColors(selectedContainerColor = AmberOrange.copy(alpha = 0.2f)) else FilterChipDefaults.filterChipColors()
                )
                val enquiryMatchedCount = remember(contacts, enquiriesMap) {
                    contacts.count { c ->
                        val clean = c.phoneNumber.replace("[^0-9]".toRegex(), "")
                        val national = PhoneNumberUtils.extractNationalNumber(c.phoneNumber)
                        enquiriesMap.containsKey(clean) || enquiriesMap.containsKey(national)
                    }
                }
                FilterChip(
                    selected = selectedFilter == "ENQUIRY",
                    onClick = { selectedFilter = "ENQUIRY" },
                    label = { Text("With Enquiry ($enquiryMatchedCount)", fontSize = 12.sp) }
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else if (filteredContacts.isEmpty()) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Contacts,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = when (selectedFilter) {
                                "FAVOURITES" -> "No favourite contacts marked yet"
                                "ENQUIRY" -> "No contacts matched in enquiry database"
                                else -> "No contacts found"
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (selectedFilter == "FAVOURITES") "Star any contact to add to quick favourites" else "Tap + to add a new contact",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp, top = 4.dp)
                    ) {
                        items(filteredContacts, key = { it.id + it.phoneNumber }) { contact ->
                            val clean = contact.phoneNumber.replace("[^0-9]".toRegex(), "")
                            val national = PhoneNumberUtils.extractNationalNumber(contact.phoneNumber)
                            val matchedEnquiry = enquiriesMap[national] ?: enquiriesMap[clean]

                            ContactRowItem(
                                contact = contact,
                                matchedEnquiry = matchedEnquiry,
                                onRowClick = {
                                    selectedContact = contact
                                },
                                onCallClick = {
                                    onCallNumber(contact.phoneNumber)
                                },
                                onWhatsAppClick = {
                                    openWhatsAppDirectly(contact.phoneNumber)
                                },
                                onToggleStar = {
                                    coroutineScope.launch {
                                        contactsRepo.toggleContactStarred(contact.id, !contact.isStarred)
                                        loadData()
                                    }
                                },
                                onLongClick = {
                                    selectedContactForCopy = contact
                                }
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                            )
                        }
                    }
                }
            }
        }
    }

    // Contact Quick View Dialog with in-app Edit, Delete, Star, Call, WhatsApp
    selectedContact?.let { contact ->
        val clean = contact.phoneNumber.replace("[^0-9]".toRegex(), "")
        val national = PhoneNumberUtils.extractNationalNumber(contact.phoneNumber)
        val matchedEnquiry = enquiriesMap[national] ?: enquiriesMap[clean]

        Dialog(onDismissRequest = { selectedContact = null }) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                modifier = Modifier.size(48.dp),
                                shape = CircleShape,
                                color = TrueBlue
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = contact.name.firstOrNull()?.uppercase() ?: "?",
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = contact.name,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable {
                                            selectedContactForCopy = contact
                                        }
                                        .padding(vertical = 2.dp)
                                ) {
                                    Text(
                                        text = PhoneNumberUtils.formatDisplay(contact.phoneNumber),
                                        fontSize = 14.sp,
                                        color = TrueBlue,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                coroutineScope.launch {
                                    contactsRepo.toggleContactStarred(contact.id, !contact.isStarred)
                                    selectedContact = contact.copy(isStarred = !contact.isStarred)
                                    loadData()
                                }
                            }) {
                                Icon(
                                    imageVector = if (contact.isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = "Star",
                                    tint = if (contact.isStarred) AmberOrange else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { selectedContact = null }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        }
                    }

                    // Matched Enquiry Section
                    if (matchedEnquiry != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "AETHENS ENQUIRY",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TrueBlue
                                    )
                                    EnquiryBadge(status = matchedEnquiry.status)
                                }
                                matchedEnquiry.course?.let {
                                    Text(text = "Course: $it", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                }
                                matchedEnquiry.company?.let {
                                    Text(text = "Place / Company: $it", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                matchedEnquiry.notes?.let {
                                    Text(text = "Notes: $it", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                syncContactForEnquiry = contact
                                selectedContact = null
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.AssignmentInd, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Link / Add to Enquiry Pipeline", fontSize = 13.sp)
                        }
                    }

                    // In-App Edit & Delete Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                contactToEdit = contact
                                selectedContact = null
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Edit", fontSize = 13.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                contactToDelete = contact
                                selectedContact = null
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CoralRed),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Delete", fontSize = 13.sp)
                        }
                    }

                    // Action Buttons Row: Direct WhatsApp & Call
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                openWhatsAppDirectly(contact.phoneNumber)
                                selectedContact = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("WhatsApp", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                onCallNumber(contact.phoneNumber)
                                selectedContact = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Call", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Direct In-App Contact Edit Dialog
    contactToEdit?.let { contact ->
        var editedName by remember { mutableStateOf(contact.name) }
        AlertDialog(
            onDismissRequest = { contactToEdit = null },
            title = { Text("Edit Contact", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Update name in phonebook and sync across database:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = editedName,
                        onValueChange = { editedName = it },
                        label = { Text("Full Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Phone: ${contact.phoneNumber}", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editedName.isNotBlank()) {
                            coroutineScope.launch {
                                val updated = contactsRepo.updateContactName(contact.id, editedName.trim())
                                dbHelper.updateEnquiryNameByPhone(contact.phoneNumber, editedName.trim())
                                MongoAtlasSyncManager.updateLeadName(contact.phoneNumber, editedName.trim())
                                if (updated) {
                                    Toast.makeText(context, "Contact updated & synced to database!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Contact updated in database!", Toast.LENGTH_SHORT).show()
                                }
                                contactToEdit = null
                                loadData()
                            }
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { contactToEdit = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Direct In-App Contact Delete Dialog
    contactToDelete?.let { contact ->
        AlertDialog(
            onDismissRequest = { contactToDelete = null },
            title = { Text("Delete Contact", fontWeight = FontWeight.Bold, color = CoralRed) },
            text = {
                Text("Are you sure you want to delete '${contact.name}' (${contact.phoneNumber}) from device contacts?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            val deleted = contactsRepo.deleteContact(contact.id)
                            if (deleted) {
                                Toast.makeText(context, "Contact deleted from phonebook", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Could not delete contact", Toast.LENGTH_SHORT).show()
                            }
                            contactToDelete = null
                            loadData()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CoralRed)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { contactToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Sync to Enquiry Pipeline Dialog
    syncContactForEnquiry?.let { contact ->
        SyncEnquiryDialog(
            initialPhoneNumber = contact.phoneNumber,
            initialName = contact.name,
            onDismiss = { syncContactForEnquiry = null },
            onSync = { newEnq ->
                coroutineScope.launch {
                    val saved = enquiryRepo.saveEnquiry(newEnq)
                    if (saved) {
                        MongoAtlasSyncManager.autoSyncLead(newEnq)
                        CrmWebhookSync.syncLead(newEnq)
                        Toast.makeText(context, "Lead saved & synced to database!", Toast.LENGTH_SHORT).show()
                        loadData()
                    }
                    syncContactForEnquiry = null
                }
            }
        )
    }

    // Long-Press Copy Selection Dialog (Name / Phone / Both)
    selectedContactForCopy?.let { contact ->
        CopySelectionDialog(
            name = contact.name,
            phoneNumber = contact.phoneNumber,
            onDismiss = { selectedContactForCopy = null }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContactRowItem(
    contact: ContactRecord,
    matchedEnquiry: Enquiry?,
    onRowClick: () -> Unit,
    onCallClick: () -> Unit,
    onWhatsAppClick: () -> Unit,
    onToggleStar: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onRowClick() },
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(44.dp),
            shape = CircleShape,
            color = if (matchedEnquiry != null) TrueBlue else MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = contact.name.firstOrNull()?.uppercase() ?: "?",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (matchedEnquiry != null) Color.White else MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = contact.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (matchedEnquiry != null) {
                    EnquiryBadge(status = matchedEnquiry.status)
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = PhoneNumberUtils.formatDisplay(contact.phoneNumber),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (matchedEnquiry?.course != null) {
                Text(
                    text = matchedEnquiry.course,
                    fontSize = 11.sp,
                    color = TrueBlue,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Star / Favorite Action Button
        IconButton(
            onClick = onToggleStar,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = if (contact.isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = "Favorite",
                tint = if (contact.isStarred) AmberOrange else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }

        // WhatsApp Direct Action Button
        IconButton(
            onClick = onWhatsAppClick,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "WhatsApp",
                tint = EmeraldGreen,
                modifier = Modifier.size(20.dp)
            )
        }

        // Call Action Button
        FilledIconButton(
            onClick = onCallClick,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = EmeraldGreen),
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Call,
                contentDescription = "Call",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
