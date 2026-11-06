package com.dialer.truedialer.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast

object ClipboardUtils {

    /**
     * Copies a phone number to the system clipboard and shows a user-friendly Toast notification.
     */
    fun copyPhoneNumber(context: Context, phoneNumber: String, label: String? = null) {
        if (phoneNumber.isBlank()) return
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clean = phoneNumber.trim()
            val clip = ClipData.newPlainText("phone_number", clean)
            clipboard?.setPrimaryClip(clip)
            val nameInfo = if (!label.isNullOrBlank()) " ($label)" else ""
            Toast.makeText(context, "Copied number: $clean$nameInfo", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Could not copy: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Copies arbitrary text (such as contact name, or name + number) with a toast.
     */
    fun copyText(context: Context, text: String, toastMessage: String? = null) {
        if (text.isBlank()) return
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = ClipData.newPlainText("text", text.trim())
            clipboard?.setPrimaryClip(clip)
            val msg = toastMessage ?: "Copied: ${text.trim()}"
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Could not copy: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Reads the current text from the system clipboard.
     */
    fun getClipboardText(context: Context): String? {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val item = clipboard?.primaryClip?.getItemAt(0)
            item?.text?.toString()?.trim()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Filters clipboard text to valid telephone digits and symbols (+, *, #, 0-9).
     */
    fun extractPhoneDigits(text: String?): String {
        if (text.isNullOrBlank()) return ""
        return text.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
    }
}
