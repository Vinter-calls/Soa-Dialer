package com.dialer.truedialer.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import com.dialer.truedialer.data.model.Enquiry
import java.net.URLEncoder

object WhatsAppBusinessHelper {

    const val PACKAGE_WHATSAPP_BUSINESS = "com.whatsapp.w4b"
    const val PACKAGE_WHATSAPP_STANDARD = "com.whatsapp"

    /**
     * Checks if WhatsApp Business is installed on the device.
     */
    fun isWhatsAppBusinessInstalled(context: Context): Boolean {
        return isPackageInstalled(context, PACKAGE_WHATSAPP_BUSINESS)
    }

    /**
     * Checks if standard WhatsApp is installed.
     */
    fun isWhatsAppInstalled(context: Context): Boolean {
        return isPackageInstalled(context, PACKAGE_WHATSAPP_STANDARD)
    }

    private fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Fills template placeholders like {name}, {company}, {requirement}, {budget}.
     */
    fun fillTemplate(templateBody: String, enquiry: Enquiry?, phoneNumber: String): String {
        var result = templateBody
        val name = enquiry?.name ?: "Valued Customer"
        val company = enquiry?.company ?: "our company"
        val requirement = enquiry?.enquiryType ?: "your requirement"
        val budget = enquiry?.budget ?: ""

        result = result.replace("{name}", name)
            .replace("{company}", company)
            .replace("{requirement}", requirement)
            .replace("{budget}", budget)
            .replace("{phone}", phoneNumber)

        return result
    }

    /**
     * Sends message targeting WhatsApp Business first.
     */
    fun sendWhatsAppBusinessMessage(context: Context, phoneNumber: String, messageText: String) {
        var cleanPhone = PhoneNumberUtils.normalize(phoneNumber).replace("+", "")

        // Strip leading zeros if present (e.g. 09876543210 -> 9876543210)
        while (cleanPhone.startsWith("0") && cleanPhone.length > 10) {
            cleanPhone = cleanPhone.substring(1)
        }

        // If 10 digits, prepend India country code (91)
        if (cleanPhone.length == 10) {
            cleanPhone = "91$cleanPhone"
        }

        // WhatsApp requires standard percent-encoding (%20 for spaces), NOT '+'
        val encodedMsg = Uri.encode(messageText)

        // Native whatsapp:// URI is the official protocol that populates the compose box in WhatsApp
        val nativeUri = Uri.parse("whatsapp://send?phone=$cleanPhone&text=$encodedMsg")
        val webUri = Uri.parse("https://api.whatsapp.com/send?phone=$cleanPhone&text=$encodedMsg")

        val targetPackage = when {
            isWhatsAppBusinessInstalled(context) -> PACKAGE_WHATSAPP_BUSINESS
            isWhatsAppInstalled(context) -> PACKAGE_WHATSAPP_STANDARD
            else -> null
        }

        // 1. Try native whatsapp:// protocol with intent extras
        val nativeIntent = Intent(Intent.ACTION_VIEW, nativeUri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(Intent.EXTRA_TEXT, messageText)
            putExtra("sms_body", messageText)
            if (targetPackage != null) {
                setPackage(targetPackage)
            }
        }

        try {
            context.startActivity(nativeIntent)
            return
        } catch (e: Exception) {
            // Native protocol failed, fall through to web API URL
        }

        // 2. Try web API URL with target package and extras
        val webIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(Intent.EXTRA_TEXT, messageText)
            putExtra("sms_body", messageText)
            if (targetPackage != null) {
                setPackage(targetPackage)
            }
        }

        try {
            context.startActivity(webIntent)
            return
        } catch (e: Exception) {
            // Target package failed, try generic intent
        }

        // 3. Fallback without package restriction
        val genericIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(Intent.EXTRA_TEXT, messageText)
            putExtra("sms_body", messageText)
        }

        try {
            context.startActivity(genericIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open WhatsApp. Please check if installed.", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Sends message with an attached document or image to WhatsApp / WhatsApp Business.
     */
    fun sendWhatsAppBusinessMedia(
        context: Context,
        phoneNumber: String,
        messageText: String,
        mediaUri: Uri?,
        mimeType: String = "*/*"
    ) {
        var cleanPhone = PhoneNumberUtils.normalize(phoneNumber).replace("+", "")
        if (cleanPhone.length == 10) {
            cleanPhone = "91$cleanPhone"
        }

        if (mediaUri != null) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, mediaUri)
                putExtra(Intent.EXTRA_TEXT, messageText)
                putExtra("jid", "$cleanPhone@s.whatsapp.net")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            if (isWhatsAppBusinessInstalled(context)) {
                intent.setPackage(PACKAGE_WHATSAPP_BUSINESS)
            } else if (isWhatsAppInstalled(context)) {
                intent.setPackage(PACKAGE_WHATSAPP_STANDARD)
            }

            try {
                context.startActivity(intent)
                return
            } catch (e: Exception) {
                intent.setPackage(null)
                try {
                    context.startActivity(
                        Intent.createChooser(intent, "Share via WhatsApp").apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                    return
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
            }
        }

        // If no media attached or fallback, send text message
        sendWhatsAppBusinessMessage(context, phoneNumber, messageText)
    }
}
