package com.dialer.truedialer.util

import android.content.Context
import android.os.Environment
import java.io.File

object CallRecordingUtils {

    fun formatDuration(seconds: Long): String {
        if (seconds <= 0) return "0s"
        val mins = seconds / 60
        val secs = seconds % 60
        return if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
    }

    fun formatStopwatch(seconds: Long): String {
        if (seconds <= 0) return "00:00"
        val hrs = seconds / 3600
        val mins = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hrs > 0) {
            String.format(java.util.Locale.US, "%02d:%02d:%02d", hrs, mins, secs)
        } else {
            String.format(java.util.Locale.US, "%02d:%02d", mins, secs)
        }
    }

    fun findCallRecording(context: Context, phoneNumber: String): File? {
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir
        if (!storageDir.exists()) return null
        val cleanNumber = PhoneNumberUtils.normalize(phoneNumber).replace("+", "")
        if (cleanNumber.isBlank()) return null
        val files = storageDir.listFiles { file ->
            file.extension.equals("m4a", true) && file.name.contains(cleanNumber)
        }
        return files?.maxByOrNull { it.lastModified() }
    }
}
