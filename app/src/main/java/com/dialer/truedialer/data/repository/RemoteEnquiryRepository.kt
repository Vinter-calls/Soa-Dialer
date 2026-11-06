package com.dialer.truedialer.data.repository

import android.util.Log
import com.dialer.truedialer.AppConfig
import com.dialer.truedialer.data.model.Enquiry
import com.dialer.truedialer.util.PhoneNumberUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Remote Implementation of EnquiryRepository.
 *
 * When you provide your backend database link (REST API, Supabase, Firebase, or custom backend),
 * this class handles fetching real-time enquiry information and sending updates.
 */
class RemoteEnquiryRepository : EnquiryRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override suspend fun searchEnquiryByPhone(phoneNumber: String): Enquiry? = withContext(Dispatchers.IO) {
        try {
            val normalized = PhoneNumberUtils.normalize(phoneNumber)
            val encodedPhone = URLEncoder.encode(normalized, "UTF-8")
            val urlString = "${AppConfig.BACKEND_API_BASE_URL}${AppConfig.ENQUIRY_LOOKUP_ENDPOINT}?phone=$encodedPhone"
            
            val url = URL(urlString)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("Accept", "application/json")
                if (AppConfig.BACKEND_API_KEY.isNotBlank()) {
                    setRequestProperty("Authorization", "Bearer ${AppConfig.BACKEND_API_KEY}")
                    // Also support common headers like apiKey (for Supabase)
                    setRequestProperty("apikey", AppConfig.BACKEND_API_KEY)
                }
            }

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val response = reader.readText()
                reader.close()

                if (response.isBlank() || response == "null") return@withContext null

                // Parses JSON directly to Enquiry model
                return@withContext json.decodeFromString<Enquiry>(response)
            } else if (conn.responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                return@withContext null
            } else {
                Log.w("RemoteEnquiryRepo", "Enquiry lookup failed with HTTP code: ${conn.responseCode}")
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e("RemoteEnquiryRepo", "Error querying remote enquiry backend: ${e.message}", e)
            return@withContext null
        }
    }

    override suspend fun getAllEnquiries(): List<Enquiry> = withContext(Dispatchers.IO) {
        try {
            val urlString = "${AppConfig.BACKEND_API_BASE_URL}${AppConfig.ENQUIRY_LIST_ENDPOINT}"
            val url = URL(urlString)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("Accept", "application/json")
                if (AppConfig.BACKEND_API_KEY.isNotBlank()) {
                    setRequestProperty("Authorization", "Bearer ${AppConfig.BACKEND_API_KEY}")
                    setRequestProperty("apikey", AppConfig.BACKEND_API_KEY)
                }
            }

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val response = reader.readText()
                reader.close()

                return@withContext json.decodeFromString<List<Enquiry>>(response)
            }
            emptyList()
        } catch (e: Exception) {
            Log.e("RemoteEnquiryRepo", "Error fetching enquiries: ${e.message}", e)
            emptyList()
        }
    }

    override suspend fun searchEnquiries(query: String): List<Enquiry> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext getAllEnquiries()
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val urlString = "${AppConfig.BACKEND_API_BASE_URL}${AppConfig.ENQUIRY_LIST_ENDPOINT}?search=$encodedQuery"
            val url = URL(urlString)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("Accept", "application/json")
                if (AppConfig.BACKEND_API_KEY.isNotBlank()) {
                    setRequestProperty("Authorization", "Bearer ${AppConfig.BACKEND_API_KEY}")
                }
            }

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val response = reader.readText()
                reader.close()

                return@withContext json.decodeFromString<List<Enquiry>>(response)
            }
            emptyList()
        } catch (e: Exception) {
            Log.e("RemoteEnquiryRepo", "Error searching enquiries: ${e.message}", e)
            emptyList()
        }
    }

    override suspend fun saveEnquiry(enquiry: Enquiry): Boolean = withContext(Dispatchers.IO) {
        try {
            val urlString = "${AppConfig.BACKEND_API_BASE_URL}${AppConfig.ENQUIRY_LIST_ENDPOINT}"
            val url = URL(urlString)
            val payload = json.encodeToString(Enquiry.serializer(), enquiry)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("Content-Type", "application/json")
                if (AppConfig.BACKEND_API_KEY.isNotBlank()) {
                    setRequestProperty("Authorization", "Bearer ${AppConfig.BACKEND_API_KEY}")
                }
            }

            conn.outputStream.use { os ->
                os.write(payload.toByteArray(Charsets.UTF_8))
            }

            conn.responseCode in 200..299
        } catch (e: Exception) {
            Log.e("RemoteEnquiryRepo", "Error saving enquiry: ${e.message}", e)
            false
        }
    }
}
