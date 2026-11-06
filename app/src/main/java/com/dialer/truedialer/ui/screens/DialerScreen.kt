package com.dialer.truedialer.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dialer.truedialer.data.model.Enquiry
import com.dialer.truedialer.data.repository.ContactsRepository
import com.dialer.truedialer.data.repository.EnquiryRepositoryProvider
import com.dialer.truedialer.ui.components.CallerIdCard
import com.dialer.truedialer.ui.components.CopySelectionDialog
import com.dialer.truedialer.ui.components.DialerPasteDialog
import com.dialer.truedialer.ui.components.Dialpad
import com.dialer.truedialer.ui.components.EnquiryBadge
import com.dialer.truedialer.ui.components.SaveContactDialog
import com.dialer.truedialer.ui.theme.EmeraldGreen
import com.dialer.truedialer.ui.theme.TrueBlue
import com.dialer.truedialer.util.ClipboardUtils
import com.dialer.truedialer.util.PermissionUtils
import com.dialer.truedialer.util.PhoneNumberUtils
import kotlinx.coroutines.launch

data class DialerMatch(
    val name: String,
    val phoneNumber: String,
    val company: String? = null,
    val status: String? = null,
    val isEnquiry: Boolean = false
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DialerScreen(
    modifier: Modifier = Modifier,
    initialDialNumber: String = ""
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val enquiryRepo = remember { EnquiryRepositoryProvider.get() }
    val contactsRepo = remember { ContactsRepository(context) }

    var inputNumber by remember { mutableStateOf(initialDialNumber) }
    var matchedEnquiry by remember { mutableStateOf<Enquiry?>(null) }
    var matchedContactName by remember { mutableStateOf<String?>(null) }
    var matchingNumbers by remember { mutableStateOf<List<DialerMatch>>(emptyList()) }
    var isContactSaved by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showPasteDialog by remember { mutableStateOf(false) }
    var selectedMatchForCopy by remember { mutableStateOf<DialerMatch?>(null) }

    // Live search contacts and enquiries as user dials digits
    LaunchedEffect(inputNumber) {
        if (inputNumber.isNotBlank()) {
            val (combined, enqMatch, savedName, isSaved) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val contacts = contactsRepo.getContacts(inputNumber).take(10)
                val enquiries = enquiryRepo.searchEnquiries(inputNumber).take(10)
                val list = mutableListOf<DialerMatch>()
                val seenNumbers = mutableSetOf<String>()

                // 1. Saved device contacts FIRST - Authoritative saved name
                contacts.forEach { c ->
                    val national = PhoneNumberUtils.extractNationalNumber(c.phoneNumber)
                    val cleanDigits = c.phoneNumber.replace("[^0-9]".toRegex(), "")
                    val key = if (national.isNotBlank()) national else cleanDigits
                    if (key.isNotBlank() && seenNumbers.add(key)) {
                        val enq = enquiries.firstOrNull { PhoneNumberUtils.areNumbersEqual(it.phoneNumber, c.phoneNumber) }
                        list.add(
                            DialerMatch(
                                name = c.name,
                                phoneNumber = c.phoneNumber,
                                company = enq?.company,
                                status = enq?.status,
                                isEnquiry = (enq != null)
                            )
                        )
                    }
                }

                // 2. Enquiries that are not in device contacts
                enquiries.forEach { enq ->
                    val national = PhoneNumberUtils.extractNationalNumber(enq.phoneNumber)
                    val cleanDigits = enq.phoneNumber.replace("[^0-9]".toRegex(), "")
                    val key = if (national.isNotBlank()) national else cleanDigits
                    if (key.isNotBlank() && seenNumbers.add(key)) {
                        list.add(
                            DialerMatch(
                                name = enq.name,
                                phoneNumber = enq.phoneNumber,
                                company = enq.company,
                                status = enq.status,
                                isEnquiry = true
                            )
                        )
                    }
                }

                var matchedEnq: Enquiry? = null
                var nameSaved: String? = null
                var contactSaved = false

                if (inputNumber.length >= 3) {
                    matchedEnq = enquiryRepo.searchEnquiryByPhone(inputNumber)
                    nameSaved = contactsRepo.getContactName(inputNumber)
                    contactSaved = (nameSaved != null) || contactsRepo.isContactSaved(inputNumber)
                }

                data class DialerSearchResult(
                    val list: List<DialerMatch>,
                    val enq: Enquiry?,
                    val name: String?,
                    val saved: Boolean
                )
                DialerSearchResult(list, matchedEnq, nameSaved, contactSaved)
            }

            matchingNumbers = combined
            matchedEnquiry = enqMatch
            matchedContactName = savedName
            isContactSaved = isSaved
        } else {
            matchingNumbers = emptyList()
            matchedEnquiry = null
            matchedContactName = null
            isContactSaved = false
        }
    }

    fun makeCall(number: String) {
        if (number.isBlank()) return
        val clean = PhoneNumberUtils.normalize(number)
        com.dialer.truedialer.service.ActiveCallManager.isOutgoingCall = true
        com.dialer.truedialer.service.ActiveCallManager.lastIncomingNumber = clean
        val uri = Uri.parse("tel:$clean")
        val intent = Intent(Intent.ACTION_CALL, uri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            if (PermissionUtils.hasPermissions(context, arrayOf(android.Manifest.permission.CALL_PHONE))) {
                context.startActivity(intent)
            } else {
                val dialIntent = Intent(Intent.ACTION_DIAL, uri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(dialIntent)
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Could not initiate call: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun handleCallButton() {
        if (inputNumber.isBlank()) {
            // Redial: Tapping Call on an empty dialer loads the last dialled number
            coroutineScope.launch {
                try {
                    val foundNumber = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        var num: String? = null
                        val cursor = context.contentResolver.query(
                            android.provider.CallLog.Calls.CONTENT_URI,
                            arrayOf(android.provider.CallLog.Calls.NUMBER),
                            "${android.provider.CallLog.Calls.TYPE} = ?",
                            arrayOf(android.provider.CallLog.Calls.OUTGOING_TYPE.toString()),
                            "${android.provider.CallLog.Calls.DATE} DESC LIMIT 1"
                        )
                        cursor?.use {
                            if (it.moveToFirst()) {
                                num = it.getString(0)
                            }
                        }
                        if (num.isNullOrBlank()) {
                            val fallbackCursor = context.contentResolver.query(
                                android.provider.CallLog.Calls.CONTENT_URI,
                                arrayOf(android.provider.CallLog.Calls.NUMBER),
                                null,
                                null,
                                "${android.provider.CallLog.Calls.DATE} DESC LIMIT 1"
                            )
                            fallbackCursor?.use {
                                if (it.moveToFirst()) {
                                    num = it.getString(0)
                                }
                            }
                        }
                        num
                    }
                    if (!foundNumber.isNullOrBlank()) {
                        inputNumber = foundNumber
                    } else {
                        Toast.makeText(context, "No recent call to redial", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Cannot read call log for redial: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            makeCall(inputNumber)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Section: Dialled Number Display & Matching Numbers List / Caller ID
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(14.dp))

                // Big Phone Number Text Display with long-press paste/copy/clear options
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = androidx.compose.ui.graphics.Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .combinedClickable(
                            onClick = {
                                // Tap when empty suggests long-press to paste
                                if (inputNumber.isEmpty()) {
                                    val clip = ClipboardUtils.getClipboardText(context)
                                    val digits = ClipboardUtils.extractPhoneDigits(clip)
                                    if (digits.isNotBlank()) {
                                        showPasteDialog = true
                                    }
                                }
                            },
                            onLongClick = {
                                showPasteDialog = true
                            }
                        )
                ) {
                    Text(
                        text = if (inputNumber.isEmpty()) "Enter number" else PhoneNumberUtils.formatDisplay(inputNumber),
                        fontSize = if (inputNumber.length > 12) 28.sp else 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (inputNumber.isEmpty()) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                else MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // When matching contacts or enquiries exist, display interactive matching list
                if (matchingNumbers.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(matchingNumbers, key = { it.phoneNumber + it.name }) { match ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            inputNumber = match.phoneNumber
                                        },
                                        onLongClick = {
                                            selectedMatchForCopy = match
                                        }
                                    )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = match.name,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            if (match.isEnquiry && !match.status.isNullOrBlank()) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                EnquiryBadge(status = match.status)
                                            }
                                        }
                                        if (!match.company.isNullOrBlank()) {
                                            Text(
                                                text = match.company,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            text = PhoneNumberUtils.formatDisplay(match.phoneNumber),
                                            fontSize = 13.sp,
                                            color = TrueBlue,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    IconButton(
                                        onClick = { makeCall(match.phoneNumber) },
                                        modifier = Modifier.size(38.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Call,
                                            contentDescription = "Call",
                                            tint = EmeraldGreen,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else if (inputNumber.length >= 3) {
                    // Truecaller-style Enquiry Lookup / Unsaved Contact Card
                    CallerIdCard(
                        phoneNumber = inputNumber,
                        enquiry = matchedEnquiry,
                        isContactSaved = isContactSaved,
                        contactName = matchedContactName,
                        onSaveContactClick = { showSaveDialog = true }
                    )
                }
            }

            // Bottom Section: Dialpad (0-9, *, #, Backspace, Call)
            Dialpad(
                onDigitClick = { digit -> inputNumber += digit },
                onBackspaceClick = {
                    if (inputNumber.isNotEmpty()) {
                        inputNumber = inputNumber.dropLast(1)
                    }
                },
                onBackspaceLongClick = { inputNumber = "" },
                onCallClick = { handleCallButton() },
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // Long-Press Paste & Dialer Options Dialog
        if (showPasteDialog) {
            DialerPasteDialog(
                currentInputNumber = inputNumber,
                onPaste = { digits -> inputNumber = digits },
                onClear = { inputNumber = "" },
                onDismiss = { showPasteDialog = false }
            )
        }

        // Long-Press Copy Selection Dialog for Matching Search Results
        selectedMatchForCopy?.let { match ->
            CopySelectionDialog(
                name = match.name,
                phoneNumber = match.phoneNumber,
                onDismiss = { selectedMatchForCopy = null }
            )
        }

        // Save Contact Dialog
        if (showSaveDialog) {
            SaveContactDialog(
                initialPhoneNumber = inputNumber,
                initialEnquiry = matchedEnquiry,
                onDismiss = { showSaveDialog = false },
                onSaveDirectly = { name, phone, company, email ->
                    coroutineScope.launch {
                        val success = contactsRepo.saveContactDirectly(name, phone, company, email)
                        if (success) {
                            Toast.makeText(context, "Saved $name to contacts & synced to database!", Toast.LENGTH_SHORT).show()
                            isContactSaved = true
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
    }
}
