package com.dialer.truedialer.data.repository

import android.content.Context
import android.database.Cursor
import android.provider.CallLog
import android.util.Log
import com.dialer.truedialer.data.model.CallRecord
import com.dialer.truedialer.data.model.CallType
import com.dialer.truedialer.util.PhoneNumberUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CallLogRepository(
    private val context: Context,
    private val contactsRepo: ContactsRepository,
    private val enquiryRepo: EnquiryRepository
) {

    suspend fun getRecentCalls(limit: Int = 100): List<CallRecord> = withContext(Dispatchers.IO) {
        val list = mutableListOf<CallRecord>()
        try {
            val contactsMap = contactsRepo.getContactsMap()

            val projection = arrayOf(
                CallLog.Calls._ID,
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION
            )
            val sortOrder = "${CallLog.Calls.DATE} DESC LIMIT $limit"

            val cursor: Cursor? = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )

            cursor?.use {
                val idIdx = it.getColumnIndex(CallLog.Calls._ID)
                val numIdx = it.getColumnIndex(CallLog.Calls.NUMBER)
                val nameIdx = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val typeIdx = it.getColumnIndex(CallLog.Calls.TYPE)
                val dateIdx = it.getColumnIndex(CallLog.Calls.DATE)
                val durIdx = it.getColumnIndex(CallLog.Calls.DURATION)

                while (it.moveToNext()) {
                    val id = if (idIdx >= 0) it.getString(idIdx) ?: "" else ""
                    val number = if (numIdx >= 0) it.getString(numIdx) ?: "" else ""
                    val cachedName = if (nameIdx >= 0) it.getString(nameIdx) else null
                    val rawType = if (typeIdx >= 0) it.getInt(typeIdx) else CallLog.Calls.INCOMING_TYPE
                    val date = if (dateIdx >= 0) it.getLong(dateIdx) else System.currentTimeMillis()
                    val duration = if (durIdx >= 0) it.getLong(durIdx) else 0L

                    val callType = when (rawType) {
                        CallLog.Calls.INCOMING_TYPE -> CallType.INCOMING
                        CallLog.Calls.OUTGOING_TYPE -> CallType.OUTGOING
                        CallLog.Calls.MISSED_TYPE -> CallType.MISSED
                        CallLog.Calls.REJECTED_TYPE -> CallType.REJECTED
                        CallLog.Calls.BLOCKED_TYPE -> CallType.BLOCKED
                        else -> CallType.UNKNOWN
                    }

                    // 1. Check saved contacts: Exact or 10-digit national match
                    val national = PhoneNumberUtils.extractNationalNumber(number)
                    val cleanDigits = number.replace("[^0-9]".toRegex(), "")
                    val savedContactName = contactsMap[national] ?: contactsMap[cleanDigits]
                    val isSaved = (savedContactName != null)

                    // 2. Lookup enquiry
                    val matchedEnquiry = enquiryRepo.searchEnquiryByPhone(number)

                    // Hierarchy:
                    // 1. Name and ID number from MongoDB database
                    // 2. If not in DB: contact name from phone contacts
                    // 3. If contact name not available: phone number
                    val resolvedName = when {
                        matchedEnquiry != null -> matchedEnquiry.displayNameWithId
                        savedContactName != null -> savedContactName
                        !cachedName.isNullOrBlank() -> cachedName
                        else -> null
                    }

                    list.add(
                        CallRecord(
                            id = id,
                            phoneNumber = number,
                            cachedName = resolvedName,
                            type = callType,
                            timestamp = date,
                            durationSeconds = duration,
                            isContactSaved = isSaved,
                            matchedEnquiry = matchedEnquiry
                        )
                    )
                }
            }
        } catch (e: SecurityException) {
            Log.w("CallLogRepo", "READ_CALL_LOG permission not granted", e)
        } catch (e: Exception) {
            Log.e("CallLogRepo", "Error reading call log: ${e.message}", e)
        }

        return@withContext list
    }

    suspend fun getCallLogsForPastMonth(): List<CallRecord> = withContext(Dispatchers.IO) {
        val list = mutableListOf<CallRecord>()
        val thirtyDaysAgo = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000L
        try {
            val contactsMap = contactsRepo.getContactsMap()

            val projection = arrayOf(
                CallLog.Calls._ID,
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION
            )
            val selection = "${CallLog.Calls.DATE} >= ?"
            val selectionArgs = arrayOf(thirtyDaysAgo.toString())
            val sortOrder = "${CallLog.Calls.DATE} DESC"

            val cursor: Cursor? = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )

            cursor?.use {
                val idIdx = it.getColumnIndex(CallLog.Calls._ID)
                val numIdx = it.getColumnIndex(CallLog.Calls.NUMBER)
                val nameIdx = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val typeIdx = it.getColumnIndex(CallLog.Calls.TYPE)
                val dateIdx = it.getColumnIndex(CallLog.Calls.DATE)
                val durIdx = it.getColumnIndex(CallLog.Calls.DURATION)

                while (it.moveToNext()) {
                    val id = if (idIdx >= 0) it.getString(idIdx) ?: "" else ""
                    val number = if (numIdx >= 0) it.getString(numIdx) ?: "" else ""
                    val cachedName = if (nameIdx >= 0) it.getString(nameIdx) else null
                    val rawType = if (typeIdx >= 0) it.getInt(typeIdx) else CallLog.Calls.INCOMING_TYPE
                    val date = if (dateIdx >= 0) it.getLong(dateIdx) else System.currentTimeMillis()
                    val duration = if (durIdx >= 0) it.getLong(durIdx) else 0L

                    val callType = when (rawType) {
                        CallLog.Calls.INCOMING_TYPE -> CallType.INCOMING
                        CallLog.Calls.OUTGOING_TYPE -> CallType.OUTGOING
                        CallLog.Calls.MISSED_TYPE -> CallType.MISSED
                        CallLog.Calls.REJECTED_TYPE -> CallType.REJECTED
                        CallLog.Calls.BLOCKED_TYPE -> CallType.BLOCKED
                        else -> CallType.UNKNOWN
                    }

                    val national = PhoneNumberUtils.extractNationalNumber(number)
                    val cleanDigits = number.replace("[^0-9]".toRegex(), "")
                    val savedContactName = contactsMap[national] ?: contactsMap[cleanDigits]
                    val isSaved = (savedContactName != null)

                    val matchedEnquiry = enquiryRepo.searchEnquiryByPhone(number)
                    val resolvedName = savedContactName ?: cachedName ?: matchedEnquiry?.name

                    list.add(
                        CallRecord(
                            id = id,
                            phoneNumber = number,
                            cachedName = resolvedName,
                            type = callType,
                            timestamp = date,
                            durationSeconds = duration,
                            isContactSaved = isSaved,
                            matchedEnquiry = matchedEnquiry
                        )
                    )
                }
            }
        } catch (e: SecurityException) {
            Log.w("CallLogRepo", "READ_CALL_LOG permission not granted", e)
        } catch (e: Exception) {
            Log.e("CallLogRepo", "Error fetching 1 month call logs: ${e.message}", e)
        }

        return@withContext list
    }
}
