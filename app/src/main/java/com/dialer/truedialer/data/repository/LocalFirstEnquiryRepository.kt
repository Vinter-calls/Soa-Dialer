package com.dialer.truedialer.data.repository

import android.content.Context
import android.util.Log
import com.dialer.truedialer.AppConfig
import com.dialer.truedialer.data.local.EnquiryDatabaseHelper
import com.dialer.truedialer.data.model.Enquiry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Local-First Offline-Enabled Enquiry Repository.
 *
 * Checks local SQLite database first for instant sub-5ms response (perfect for live incoming calls),
 * and syncs with the remote backend in the background when connectivity is available.
 */
class LocalFirstEnquiryRepository(
    private val context: Context,
    private val remoteRepo: RemoteEnquiryRepository
) : EnquiryRepository {

    private val dbHelper = EnquiryDatabaseHelper(context)
    private val syncScope = CoroutineScope(Dispatchers.IO)

    override suspend fun searchEnquiryByPhone(phoneNumber: String): Enquiry? = withContext(Dispatchers.IO) {
        // 1. Instant local offline lookup (< 5ms)
        val localEnquiry = dbHelper.findEnquiryByPhone(phoneNumber)
        if (localEnquiry != null) {
            return@withContext localEnquiry
        }

        // 2. If not found locally and not using mock, check remote backend
        if (!AppConfig.USE_MOCK_DATABASE) {
            try {
                val remote = remoteRepo.searchEnquiryByPhone(phoneNumber)
                if (remote != null) {
                    // Cache locally for next time
                    dbHelper.insertOrUpdateEnquiry(remote)
                    return@withContext remote
                }
            } catch (e: Exception) {
                Log.w("LocalFirstRepo", "Remote lookup failed: ${e.message}")
            }
        }

        return@withContext null
    }

    override suspend fun getAllEnquiries(): List<Enquiry> = withContext(Dispatchers.IO) {
        // Trigger background sync if connected to remote
        if (!AppConfig.USE_MOCK_DATABASE) {
            syncScope.launch {
                try {
                    val remoteList = remoteRepo.getAllEnquiries()
                    for (item in remoteList) {
                        dbHelper.insertOrUpdateEnquiry(item)
                    }
                } catch (e: Exception) {
                    Log.w("LocalFirstRepo", "Background sync failed: ${e.message}")
                }
            }
        }
        return@withContext dbHelper.getAllEnquiries()
    }

    override suspend fun searchEnquiries(query: String): List<Enquiry> = withContext(Dispatchers.IO) {
        return@withContext dbHelper.searchEnquiries(query)
    }

    override suspend fun saveEnquiry(enquiry: Enquiry): Boolean = withContext(Dispatchers.IO) {
        val localSaved = dbHelper.insertOrUpdateEnquiry(enquiry)
        if (AppConfig.AUTO_SYNC_MONGODB) {
            syncScope.launch {
                try {
                    com.dialer.truedialer.sync.MongoAtlasSyncManager.autoSyncLead(enquiry)
                } catch (e: Exception) {
                    Log.w("LocalFirstRepo", "MongoDB auto-sync failed: ${e.message}")
                }
            }
        }
        if (!AppConfig.USE_MOCK_DATABASE) {
            syncScope.launch {
                try {
                    remoteRepo.saveEnquiry(enquiry)
                } catch (e: Exception) {
                    Log.w("LocalFirstRepo", "Remote save failed: ${e.message}")
                }
            }
        }
        return@withContext localSaved
    }

    fun markAsSpam(phoneNumber: String, isSpam: Boolean): Boolean {
        return dbHelper.markAsSpam(phoneNumber, isSpam)
    }

    fun isNumberSpam(phoneNumber: String): Boolean {
        return dbHelper.isNumberSpam(phoneNumber)
    }

    fun getDatabaseHelper(): EnquiryDatabaseHelper = dbHelper
}
