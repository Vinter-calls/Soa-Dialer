package com.dialer.truedialer.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dialer.truedialer.data.model.Enquiry
import com.dialer.truedialer.ui.theme.EmeraldGreen
import com.dialer.truedialer.ui.theme.TrueBlue
import com.dialer.truedialer.util.PhoneNumberUtils

@Composable
fun CallerIdCard(
    phoneNumber: String,
    enquiry: Enquiry?,
    isContactSaved: Boolean,
    contactName: String? = null,
    onSaveContactClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    AnimatedVisibility(
        visible = phoneNumber.isNotBlank(),
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (enquiry != null) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(
                modifier = Modifier.padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val resolvedTitle = contactName ?: enquiry?.name ?: if (isContactSaved) "Saved Contact" else "Unsaved Number"

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = resolvedTitle,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (enquiry != null && !enquiry.status.isBlank()) {
                                Spacer(modifier = Modifier.width(8.dp))
                                EnquiryBadge(status = enquiry.status)
                            }
                        }

                        if (!enquiry?.company.isNullOrBlank()) {
                            Text(
                                text = enquiry!!.company,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else if (contactName != null && enquiry == null) {
                            Text(
                                text = "Device Contact",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else if (enquiry == null && !isContactSaved) {
                            Text(
                                text = "No enquiry record found in backend",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (!enquiry?.enquiryType.isNullOrBlank()) {
                            Text(
                                text = "Interest: ${enquiry!!.enquiryType}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = TrueBlue
                            )
                        }
                    }

                    // WhatsApp Quick Action
                    IconButton(
                        onClick = {
                            val uri = Uri.parse(PhoneNumberUtils.getWhatsAppUri(phoneNumber))
                            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "WhatsApp",
                            tint = EmeraldGreen
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Contact Status / Save Action
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isContactSaved) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Saved",
                                tint = EmeraldGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Saved in Contacts",
                                fontSize = 12.sp,
                                color = EmeraldGreen,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else {
                        // Prominent "Save Contact" button
                        Button(
                            onClick = onSaveContactClick,
                            colors = ButtonDefaults.buttonColors(containerColor = TrueBlue),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PersonAdd,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (enquiry != null) "Save ${enquiry.name}" else "Save Contact",
                                fontSize = 12.sp
                            )
                        }
                    }

                    if (enquiry?.budget != null) {
                        Text(
                            text = "Budget: ${enquiry.budget}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
