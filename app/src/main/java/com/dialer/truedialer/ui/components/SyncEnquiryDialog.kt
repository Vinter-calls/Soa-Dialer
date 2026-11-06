package com.dialer.truedialer.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.dialer.truedialer.data.model.Enquiry
import com.dialer.truedialer.ui.theme.TrueBlue

@Composable
fun SyncEnquiryDialog(
    initialPhoneNumber: String,
    initialName: String?,
    onDismiss: () -> Unit,
    onSync: (Enquiry) -> Unit
) {
    var name by remember { mutableStateOf(initialName ?: "") }
    var phone by remember { mutableStateOf(initialPhoneNumber) }
    var company by remember { mutableStateOf("") }
    var enquiryType by remember { mutableStateOf("General Enquiry") }
    var status by remember { mutableStateOf("New Lead") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Sync to Enquiry Database",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Customer Name *") },
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
                    label = { Text("Company / Organization") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = enquiryType,
                    onValueChange = { enquiryType = it },
                    label = { Text("Requirement / Enquiry Type") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(6.dp))

                Button(
                    onClick = {
                        if (name.isNotBlank() && phone.isNotBlank()) {
                            val enq = Enquiry(
                                id = "ENQ-${System.currentTimeMillis() % 10000}",
                                name = name,
                                phoneNumber = phone,
                                company = company.ifBlank { null },
                                enquiryType = enquiryType.ifBlank { null },
                                status = status
                            )
                            onSync(enq)
                            onDismiss()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TrueBlue),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sync Now")
                }
            }
        }
    }
}
