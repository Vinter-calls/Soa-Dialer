package com.dialer.truedialer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.dialer.truedialer.data.model.CallRecord
import com.dialer.truedialer.ui.theme.EmeraldGreen
import com.dialer.truedialer.ui.theme.TrueBlue
import com.dialer.truedialer.util.PhoneNumberUtils

@Composable
fun ContactDetailDialog(
    callRecord: CallRecord,
    onDismiss: () -> Unit,
    onCallClick: () -> Unit,
    onWhatsAppClick: () -> Unit,
    onSaveClick: () -> Unit
) {
    val context = LocalContext.current
    val enquiry = callRecord.matchedEnquiry
    val displayName = (if (callRecord.isContactSaved && !callRecord.cachedName.isNullOrBlank()) callRecord.cachedName else null)
        ?: callRecord.cachedName
        ?: enquiry?.name
        ?: PhoneNumberUtils.formatDisplay(callRecord.phoneNumber)

    var showCopyDialog by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Contact & Lead Details",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                HorizontalDivider()

                // Name & Phone
                Text(
                    text = displayName,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Phone number (tap or long-press to open Copy Options)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            showCopyDialog = true
                        }
                        .padding(vertical = 2.dp)
                ) {
                    Text(
                        text = PhoneNumberUtils.formatDisplay(callRecord.phoneNumber),
                        fontSize = 15.sp,
                        color = TrueBlue,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (enquiry != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        EnquiryBadge(status = enquiry.status)
                        if (!enquiry.company.isNullOrBlank()) {
                            Text(
                                text = enquiry.company,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (!enquiry.enquiryType.isNullOrBlank()) {
                        Text(
                            text = "Requirement: ${enquiry.enquiryType}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (!enquiry.budget.isNullOrBlank()) {
                        Text(
                            text = "Budget: ${enquiry.budget}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = EmeraldGreen
                        )
                    }

                    if (!enquiry.email.isNullOrBlank()) {
                        Text(
                            text = "Email: ${enquiry.email}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (!enquiry.notes.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Notes: ${enquiry.notes}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Text(
                        text = if (callRecord.isContactSaved) "Saved in device contacts" else "Unsaved number (Not in enquiry list)",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Action Buttons: Save, WhatsApp, Call
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!callRecord.isContactSaved) {
                        OutlinedButton(
                            onClick = {
                                onSaveClick()
                                onDismiss()
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save", fontSize = 12.sp)
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            onWhatsAppClick()
                            onDismiss()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = EmeraldGreen),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("WhatsApp", fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            onCallClick()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TrueBlue),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1.2f)
                    ) {
                        Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Call", fontSize = 12.sp)
                    }
                }
            }
        }

        if (showCopyDialog) {
            CopySelectionDialog(
                name = displayName,
                phoneNumber = callRecord.phoneNumber,
                onDismiss = { showCopyDialog = false }
            )
        }
    }
}
