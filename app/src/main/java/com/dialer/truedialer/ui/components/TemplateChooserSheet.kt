package com.dialer.truedialer.ui.components

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Send
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
import com.dialer.truedialer.data.local.EnquiryDatabaseHelper
import com.dialer.truedialer.data.model.Enquiry
import com.dialer.truedialer.data.model.MessageTemplate
import com.dialer.truedialer.ui.theme.EmeraldGreen
import com.dialer.truedialer.util.WhatsAppBusinessHelper

@Composable
fun TemplateChooserDialog(
    phoneNumber: String,
    enquiry: Enquiry?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val dbHelper = remember { EnquiryDatabaseHelper(context) }
    var templates by remember { mutableStateOf<List<MessageTemplate>>(emptyList()) }
    var selectedTemplate by remember { mutableStateOf<MessageTemplate?>(null) }
    var customMessage by remember { mutableStateOf("") }

    var attachedMediaUri by remember { mutableStateOf<Uri?>(null) }
    var attachedMediaName by remember { mutableStateOf<String?>(null) }
    var attachedMimeType by remember { mutableStateOf<String>("*/*") }
    var showAddLinkDialog by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            attachedMediaUri = uri
            attachedMimeType = "image/*"
            attachedMediaName = getFileNameFromUri(context, uri)
        }
    }

    val docPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            attachedMediaUri = uri
            attachedMimeType = "application/pdf"
            attachedMediaName = getFileNameFromUri(context, uri)
        }
    }

    val isWhatsAppBusiness = remember { WhatsAppBusinessHelper.isWhatsAppBusinessInstalled(context) }

    LaunchedEffect(Unit) {
        val tmpls = dbHelper.getTemplates()
        templates = tmpls
        if (tmpls.isNotEmpty()) {
            selectedTemplate = tmpls.first()
            customMessage = WhatsAppBusinessHelper.fillTemplate(tmpls.first().body, enquiry, phoneNumber)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (isWhatsAppBusiness) "WhatsApp Business Message" else "WhatsApp Message",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "To: ${enquiry?.name ?: phoneNumber}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "SELECT TEMPLATE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                // Template Chips / List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.35f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(templates) { tmpl ->
                        val isSelected = selectedTemplate?.id == tmpl.id
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedTemplate = tmpl
                                    customMessage = WhatsAppBusinessHelper.fillTemplate(tmpl.body, enquiry, phoneNumber)
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        selectedTemplate = tmpl
                                        customMessage = WhatsAppBusinessHelper.fillTemplate(tmpl.body, enquiry, phoneNumber)
                                    }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = tmpl.title,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = tmpl.body,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Attachments Action Row: Documents, Images, Links
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AssistChip(
                        onClick = { imagePickerLauncher.launch("image/*") },
                        label = { Text("Image", fontSize = 11.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                    )

                    AssistChip(
                        onClick = { docPickerLauncher.launch("*/*") },
                        label = { Text("Document", fontSize = 11.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                    )

                    AssistChip(
                        onClick = { showAddLinkDialog = true },
                        label = { Text("Add Link", fontSize = 11.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                    )
                }

                // Attached File Preview Chip
                if (attachedMediaUri != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = EmeraldGreen.copy(alpha = 0.15f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = if (attachedMimeType.startsWith("image")) Icons.Default.Image else Icons.Default.AttachFile,
                                    contentDescription = null,
                                    tint = EmeraldGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = attachedMediaName ?: "Attached File",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                            }
                            IconButton(
                                onClick = {
                                    attachedMediaUri = null
                                    attachedMediaName = null
                                    attachedMimeType = "*/*"
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.Gray, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "EDIT MESSAGE BEFORE SENDING",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = customMessage,
                    onValueChange = { customMessage = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.45f),
                    placeholder = { Text("Type custom message, include links or attachments...") }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Send Button
                Button(
                    onClick = {
                        WhatsAppBusinessHelper.sendWhatsAppBusinessMedia(
                            context = context,
                            phoneNumber = phoneNumber,
                            messageText = customMessage,
                            mediaUri = attachedMediaUri,
                            mimeType = attachedMimeType
                        )
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isWhatsAppBusiness) "Send via WhatsApp Business" else "Send via WhatsApp",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        // Insert Link Dialog
        if (showAddLinkDialog) {
            var linkUrl by remember { mutableStateOf("https://") }
            AlertDialog(
                onDismissRequest = { showAddLinkDialog = false },
                title = { Text("Insert Link into Template", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Enter the URL or choose a preset link to append to your WhatsApp message:",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = linkUrl,
                            onValueChange = { linkUrl = it },
                            label = { Text("Web / Document / Catalog Link") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SuggestionChip(
                                onClick = { linkUrl = "https://aethens.com/catalog" },
                                label = { Text("Catalog", fontSize = 11.sp) }
                            )
                            SuggestionChip(
                                onClick = { linkUrl = "https://aethens.com/brochure.pdf" },
                                label = { Text("Brochure", fontSize = 11.sp) }
                            )
                            SuggestionChip(
                                onClick = { linkUrl = "https://calendly.com/aethens" },
                                label = { Text("Demo", fontSize = 11.sp) }
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (linkUrl.isNotBlank() && linkUrl != "https://") {
                                customMessage = if (customMessage.isNotBlank()) "$customMessage\n$linkUrl" else linkUrl
                            }
                            showAddLinkDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                    ) {
                        Text("Insert Link")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddLinkDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

private fun getFileNameFromUri(context: Context, uri: Uri): String {
    var name = "Attachment"
    try {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIdx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIdx >= 0) {
                    name = it.getString(nameIdx) ?: name
                }
            }
        }
    } catch (_: Exception) {}
    return name
}
