package com.dialer.truedialer.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
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
import com.dialer.truedialer.data.model.Enquiry
import com.dialer.truedialer.data.repository.EnquiryRepositoryProvider
import com.dialer.truedialer.sync.CrmWebhookSync
import com.dialer.truedialer.ui.theme.EmeraldGreen
import com.dialer.truedialer.ui.theme.TrueBlue
import kotlinx.coroutines.launch

@Composable
fun SaveContactDialog(
    initialPhoneNumber: String,
    initialEnquiry: Enquiry? = null,
    onDismiss: () -> Unit,
    onSaveDirectly: (name: String, phone: String, company: String?, email: String?) -> Unit,
    onOpenSystemEditor: (name: String, phone: String, company: String?, email: String?) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val enquiryRepo = remember { EnquiryRepositoryProvider.get(context) }

    var name by remember { mutableStateOf(initialEnquiry?.name ?: "") }
    var phone by remember { mutableStateOf(initialPhoneNumber) }
    var company by remember { mutableStateOf(initialEnquiry?.company ?: "") }
    var email by remember { mutableStateOf(initialEnquiry?.email ?: "") }
    var isAlreadyInEnquiry by remember { mutableStateOf(false) }

    LaunchedEffect(phone) {
        if (phone.isNotBlank()) {
            val existing = enquiryRepo.searchEnquiryByPhone(phone)
            isAlreadyInEnquiry = (existing != null)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Save to Contacts",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (initialEnquiry != null) {
                    Text(
                        text = "Auto-filled from enquiry database (${initialEnquiry.status})",
                        fontSize = 12.sp,
                        color = TrueBlue
                    )
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Full Name") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number") },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = company,
                    onValueChange = { company = it },
                    label = { Text("Company (Optional)") },
                    leadingIcon = { Icon(Icons.Default.Business, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email (Optional)") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Auto-sync status banner
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = EmeraldGreen.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = EmeraldGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isAlreadyInEnquiry) "Linked to existing enquiry database record" else "Auto-syncs as lead to database upon save",
                            fontSize = 12.sp,
                            color = EmeraldGreen,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (name.isNotBlank() && phone.isNotBlank()) {
                                coroutineScope.launch {
                                    // 1. Save to Device Contacts
                                    onSaveDirectly(name, phone, company.ifBlank { null }, email.ifBlank { null })

                                    // 2. Auto-sync to Enquiry / Lead database
                                    val syncedEnquiry = (initialEnquiry ?: Enquiry(
                                        id = "ENQ-${System.currentTimeMillis() % 100000}",
                                        name = name,
                                        phoneNumber = phone,
                                        company = company.ifBlank { null },
                                        email = email.ifBlank { null },
                                        status = "New Lead"
                                    )).copy(
                                        name = name,
                                        phoneNumber = phone,
                                        company = company.ifBlank { null },
                                        email = email.ifBlank { null }
                                    )
                                    enquiryRepo.saveEnquiry(syncedEnquiry)
                                    CrmWebhookSync.syncLead(syncedEnquiry)
                                    Toast.makeText(context, "Saved & auto-synced to database!", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save & Sync to DB", fontWeight = FontWeight.SemiBold)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel")
                        }

                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    val enqToSync = (initialEnquiry ?: Enquiry(
                                        id = "ENQ-${System.currentTimeMillis() % 100000}",
                                        name = name,
                                        phoneNumber = phone,
                                        company = company.ifBlank { null },
                                        email = email.ifBlank { null },
                                        status = "New Lead"
                                    )).copy(
                                        name = name,
                                        phoneNumber = phone,
                                        company = company.ifBlank { null },
                                        email = email.ifBlank { null }
                                    )
                                    enquiryRepo.saveEnquiry(enqToSync)
                                    CrmWebhookSync.syncLead(enqToSync)
                                }
                                onOpenSystemEditor(name, phone, company.ifBlank { null }, email.ifBlank { null })
                                onDismiss()
                            }
                        ) {
                            Text("Open Editor")
                        }
                    }
                }
            }
        }
    }
}
