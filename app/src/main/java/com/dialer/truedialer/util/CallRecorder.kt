package com.dialer.truedialer.util

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CallRecorder(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null
    var outputFile: File? = null
        private set
    var isRecording: Boolean = false
        private set

    fun startRecording(phoneNumber: String): Boolean {
        // Multi-level source fallback for optimal two-way in-call recording:
        // 1. VOICE_COMMUNICATION: official Android telephony/VoIP audio source with AGC & AEC
        // 2. VOICE_RECOGNITION: high-gain voice capture
        // 3. MIC: standard device microphone
        // 4. DEFAULT: device fallback
        val sources = listOf(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.DEFAULT
        )

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir
        val cleanPhone = phoneNumber.replace("[^0-9+]".toRegex(), "")
        val targetFile = File(storageDir, "Call_${cleanPhone}_$timeStamp.m4a")

        for (source in sources) {
            try {
                val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MediaRecorder(context)
                } else {
                    @Suppress("DEPRECATION")
                    MediaRecorder()
                }

                mr.setAudioSource(source)
                mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                mr.setAudioChannels(1)
                mr.setAudioSamplingRate(44100)
                mr.setAudioEncodingBitRate(128000)
                mr.setOutputFile(targetFile.absolutePath)
                mr.prepare()
                mr.start()

                outputFile = targetFile
                mediaRecorder = mr
                isRecording = true
                Log.i("CallRecorder", "Call recording started successfully with source $source: ${targetFile.absolutePath}")
                return true
            } catch (e: Exception) {
                Log.w("CallRecorder", "Source $source failed: ${e.message}")
                try {
                    mediaRecorder?.release()
                } catch (_: Exception) {}
                mediaRecorder = null
            }
        }

        isRecording = false
        return false
    }

    fun stopRecording(): File? {
        try {
            if (isRecording) {
                mediaRecorder?.apply {
                    try {
                        stop()
                    } catch (e: Exception) {
                        Log.w("CallRecorder", "Exception stopping recorder: ${e.message}")
                    }
                    release()
                }
                mediaRecorder = null
                isRecording = false
                Log.i("CallRecorder", "Call recording stopped. Saved to: ${outputFile?.absolutePath}")
                return outputFile
            }
        } catch (e: Exception) {
            Log.e("CallRecorder", "Failed to stop recording: ${e.message}", e)
            isRecording = false
        }
        return null
    }
}
