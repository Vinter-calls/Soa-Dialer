package com.dialer.truedialer.sync

import android.util.Log
import com.dialer.truedialer.AppConfig
import com.dialer.truedialer.data.model.Enquiry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object CrmWebhookSync {

    private const val TAG = "CrmWebhookSync"

    suspend fun syncCallEvent(
        phoneNumber: String,
        callDurationSeconds: Long,
        callType: String,
        enquiry: Enquiry?,
        postCallNote: String? = null,
        scheduledFollowUp: Long? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val webhookUrl = AppConfig.CRM_WEBHOOK_URL.trim()
        if (webhookUrl.isBlank()) {
            return@withContext false
        }

        try {
            val payload = JSONObject().apply {
                put("event", "call_completed")
                put("timestamp", System.currentTimeMillis())
                put("centerId", AppConfig.CENTER_ID)
                put("centerName", AppConfig.CENTER_NAME)
                put("userId", AppConfig.CURRENT_USER_ID)
                put("userName", AppConfig.CURRENT_USER_NAME)
                put("userRole", AppConfig.CURRENT_USER_ROLE)
                put("phoneNumber", phoneNumber)
                put("callType", callType)
                put("callDurationSeconds", callDurationSeconds)
                put("postCallNote", postCallNote ?: "")
                if (scheduledFollowUp != null && scheduledFollowUp > 0) {
                    put("scheduledFollowUpEpochMs", scheduledFollowUp)
                }

                if (enquiry != null) {
                    val enqObj = JSONObject().apply {
                        put("id", enquiry.id)
                        put("name", enquiry.name)
                        put("company", enquiry.company ?: "")
                        put("enquiryType", enquiry.enquiryType ?: "")
                        put("status", enquiry.status)
                        put("budget", enquiry.budget ?: "")
                    }
                    put("enquiry", enqObj)
                }
            }

            return@withContext sendPayload(webhookUrl, payload.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error building CRM payload: ${e.message}", e)
            return@withContext false
        }
    }

    suspend fun syncLead(enquiry: Enquiry): Boolean = withContext(Dispatchers.IO) {
        val webhookUrl = AppConfig.CRM_WEBHOOK_URL.trim()
        if (webhookUrl.isBlank()) {
            return@withContext false
        }

        try {
            val payload = JSONObject().apply {
                put("event", "lead_saved")
                put("timestamp", System.currentTimeMillis())
                put("centerId", AppConfig.CENTER_ID)
                put("centerName", AppConfig.CENTER_NAME)
                put("userId", AppConfig.CURRENT_USER_ID)
                put("userName", AppConfig.CURRENT_USER_NAME)
                put("userRole", AppConfig.CURRENT_USER_ROLE)
                put("id", enquiry.id)
                put("name", enquiry.name)
                put("phoneNumber", enquiry.phoneNumber)
                put("company", enquiry.company ?: "")
                put("email", enquiry.email ?: "")
                put("status", enquiry.status)
                put("notes", enquiry.notes ?: "")
                put("enquiryType", enquiry.enquiryType ?: "")
                put("budget", enquiry.budget ?: "")
            }
            sendPayload(webhookUrl, payload.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error building CRM lead payload: ${e.message}", e)
            false
        }
    }

    suspend fun syncCallHistoryBatch(
        callLogs: List<com.dialer.truedialer.data.model.CallRecord>
    ): Boolean = withContext(Dispatchers.IO) {
        val webhookUrl = AppConfig.CRM_WEBHOOK_URL.trim()
        if (webhookUrl.isBlank()) return@withContext false

        try {
            val jsonArray = org.json.JSONArray()
            for (call in callLogs) {
                val obj = JSONObject().apply {
                    put("id", call.id)
                    put("phoneNumber", call.phoneNumber)
                    put("callerName", call.cachedName ?: call.matchedEnquiry?.name ?: "")
                    put("callType", call.type.name)
                    put("durationSeconds", call.durationSeconds)
                    put("timestamp", call.timestamp)
                    put("isSavedContact", call.isContactSaved)
                    if (call.matchedEnquiry != null) {
                        put("enquiryId", call.matchedEnquiry.id)
                        put("leadStatus", call.matchedEnquiry.status)
                    }
                }
                jsonArray.put(obj)
            }

            val payload = JSONObject().apply {
                put("event", "call_history_sync")
                put("timestamp", System.currentTimeMillis())
                put("centerId", AppConfig.CENTER_ID)
                put("centerName", AppConfig.CENTER_NAME)
                put("userId", AppConfig.CURRENT_USER_ID)
                put("userName", AppConfig.CURRENT_USER_NAME)
                put("count", callLogs.size)
                put("calls", jsonArray)
            }

            sendPayload(webhookUrl, payload.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing call history batch: ${e.message}", e)
            false
        }
    }

    suspend fun testWebhook(testUrl: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        if (testUrl.isBlank()) return@withContext Pair(false, "Webhook URL cannot be empty")
        try {
            val testPayload = JSONObject().apply {
                put("event", "test_ping")
                put("app", "TrueEnquiryDialer")
                put("timestamp", System.currentTimeMillis())
                put("message", "CRM Webhook connection test successful!")
            }
            val success = sendPayload(testUrl, testPayload.toString())
            if (success) {
                Pair(true, "Webhook ping sent successfully (HTTP 200/204)!")
            } else {
                Pair(false, "Server returned error or invalid HTTP status")
            }
        } catch (e: Exception) {
            Pair(false, "Connection error: ${e.message}")
        }
    }

    private fun sendPayload(urlStr: String, jsonString: String): Boolean {
        return try {
            val url = URL(urlStr)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
                if (AppConfig.CRM_WEBHOOK_BEARER_TOKEN.isNotBlank()) {
                    setRequestProperty("Authorization", "Bearer ${AppConfig.CRM_WEBHOOK_BEARER_TOKEN}")
                }
            }

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(jsonString)
                writer.flush()
            }

            val responseCode = conn.responseCode
            Log.i(TAG, "CRM Webhook HTTP response: $responseCode")
            responseCode in 200..299
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send CRM webhook: ${e.message}")
            false
        }
    }
}
