package com.dialer.truedialer.sync

import android.content.Context
import android.util.Log
import com.dialer.truedialer.AppConfig
import com.dialer.truedialer.data.local.EnquiryDatabaseHelper
import com.dialer.truedialer.data.model.Enquiry
import com.dialer.truedialer.util.PhoneNumberUtils
import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bson.Document
import java.util.Date
import java.util.concurrent.TimeUnit

data class MongoSyncResult(
    val success: Boolean,
    val insertedCount: Int = 0,
    val updatedCount: Int = 0,
    val pulledCount: Int = 0,
    val message: String = ""
)

object MongoAtlasSyncManager {

    private const val TAG = "MongoAtlasSync"

    // Direct non-SRV replica set connection string (works natively on Android without JNDI DNS requirement)
    private const val MONGO_URI =
        "mongodb://sophiseTech:Jazzir%409496@ac-ygvm0w8-shard-00-00.fvcjysb.mongodb.net:27017,ac-ygvm0w8-shard-00-01.fvcjysb.mongodb.net:27017,ac-ygvm0w8-shard-00-02.fvcjysb.mongodb.net:27017/aethens?ssl=true&replicaSet=atlas-smfgrh-shard-0&authSource=admin&retryWrites=true&w=majority"

    private const val DB_NAME = "aethens"
    private const val COL_ENQUIRIES = "enquiries"
    private const val COL_COUNTERS = "enquirycounters"
    private const val DEFAULT_CENTER_ID = "67cd080a040cb823ea6c0744"

    private var cachedClient: MongoClient? = null

    private fun getClient(): MongoClient {
        if (cachedClient == null) {
            val settings = MongoClientSettings.builder()
                .applyConnectionString(ConnectionString(MONGO_URI))
                .applyToSocketSettings {
                    it.connectTimeout(8000, TimeUnit.MILLISECONDS)
                    it.readTimeout(8000, TimeUnit.MILLISECONDS)
                }
                .build()
            cachedClient = MongoClients.create(settings)
        }
        return cachedClient!!
    }

    private fun mapStatusToStage(status: String?): String {
        if (status.isNullOrBlank()) return "New"
        val s = status.lowercase()
        return when {
            s.contains("enrolled") || s.contains("converted") -> "Enrolled"
            s.contains("follow") -> "Follow-up"
            s.contains("lost") -> "Lost"
            s.contains("spam") -> "Spam"
            s.contains("vip") -> "VIP"
            else -> "New"
        }
    }

    private fun formatE164(phone: String?): String {
        if (phone.isNullOrBlank()) return ""
        val clean = phone.trim()
        if (clean.startsWith("+")) return clean
        val digits = clean.replace(Regex("\\D"), "")
        return if (digits.length == 10) "+91$digits" else clean
    }

    /**
     * Performs a full bidirectional sync:
     * 1. Pushes local new/updated leads to MongoDB Atlas
     * 2. Pulls remote leads from MongoDB Atlas to local SQLite so the phone has caller ID for all leads
     */
    suspend fun syncAll(context: Context): MongoSyncResult = withContext(Dispatchers.IO) {
        val dbHelper = EnquiryDatabaseHelper(context)
        val localEnquiries = dbHelper.getAllEnquiries()

        try {
            val client = getClient()
            val db = client.getDatabase(DB_NAME)
            val enquiriesCol = db.getCollection(COL_ENQUIRIES)
            val countersCol = db.getCollection(COL_COUNTERS)

            // 1. Fetch existing remote enquiries
            val remoteDocs = enquiriesCol.find().toList()
            val remotePhoneMap = mutableMapOf<String, Document>()
            for (doc in remoteDocs) {
                val phone = doc.getString("phoneNumber") ?: ""
                val clean = PhoneNumberUtils.normalize(phone).takeLast(10)
                if (clean.isNotBlank() && !remotePhoneMap.containsKey(clean)) {
                    remotePhoneMap[clean] = doc
                }
            }

            // 2. Fetch current sequence counter
            val counterDoc = countersCol.find(Filters.eq("_id", "enquiryNumber")).firstOrNull()
            var nextSeq = counterDoc?.getInteger("seq") ?: (remoteDocs.mapNotNull { it.getInteger("enquiryNumber") }.maxOrNull() ?: 120) + 1

            var inserted = 0
            var updated = 0

            // 3. Push local leads to MongoDB
            for (local in localEnquiries) {
                // Rule: "Wrong Number - no need to sync to database"
                if (local.status.equals("Wrong Number", ignoreCase = true)) {
                    continue
                }

                val clean = PhoneNumberUtils.normalize(local.phoneNumber).takeLast(10)
                if (clean.isBlank()) continue

                val existingRemote = remotePhoneMap[clean]
                if (existingRemote == null) {
                    // New lead -> Insert into MongoDB
                    val newDoc = Document().apply {
                        append("enquiryNumber", nextSeq++)
                        append("name", local.name.ifBlank { "Customer" })
                        append("phoneNumber", formatE164(local.phoneNumber))
                        append("foundUsBy", local.leadSource ?: "Direct / Phone")
                        append("modeOfEnquiry", "Call")
                        append("ageCategory", local.ageCategory ?: local.enquiryType ?: "General")
                        append("classPreference", "Any")
                        append("selectedCourses", if (!local.course.isNullOrBlank()) listOf(local.course) else emptyList<String>())
                        append("stage", mapStatusToStage(local.status))
                        append("demoSlot", Document().apply {
                            append("status", local.demoStatus ?: "Scheduled")
                            append("postponeCount", 0)
                            append("confirmationMessage", null)
                        })
                        append("centerId", DEFAULT_CENTER_ID)
                        append("isTransferred", false)
                        append("place", local.company ?: "Bangalore")
                        append("notes", local.remarks ?: local.notes ?: "")
                        append("stageHistory", emptyList<Document>())
                        append("followUpHistory", emptyList<Document>())
                        append("demoSlotHistory", emptyList<Document>())
                        append("centerTransfers", emptyList<Document>())
                        append("createdAt", Date())
                        append("updatedAt", Date())
                    }
                    enquiriesCol.insertOne(newDoc)
                    remotePhoneMap[clean] = newDoc
                    inserted++
                } else {
                    // Existing lead -> Update if stage/status, name, or notes changed
                    val remoteStage = existingRemote.getString("stage") ?: ""
                    val remoteName = existingRemote.getString("name") ?: ""
                    val targetStage = mapStatusToStage(local.status)
                    val updates = mutableListOf(
                        Updates.set("stage", targetStage),
                        Updates.set("name", local.name),
                        Updates.set("updatedAt", Date())
                    )
                    if (!local.course.isNullOrBlank()) {
                        updates.add(Updates.set("selectedCourses", listOf(local.course)))
                    }
                    if (!local.ageCategory.isNullOrBlank()) {
                        updates.add(Updates.set("ageCategory", local.ageCategory))
                    }
                    enquiriesCol.updateOne(
                        Filters.eq("_id", existingRemote.getObjectId("_id")),
                        Updates.combine(updates)
                    )
                    updated++
                }
            }

            // Update counter sequence
            countersCol.updateOne(
                Filters.eq("_id", "enquiryNumber"),
                Updates.set("seq", nextSeq),
                com.mongodb.client.model.UpdateOptions().upsert(true)
            )

            // 4. Pull new remote leads or update existing leads into local SQLite
            var pulled = 0

            for (remote in remoteDocs) {
                val phone = remote.getString("phoneNumber") ?: ""
                val clean = PhoneNumberUtils.normalize(phone).takeLast(10)
                if (clean.isNotBlank()) {
                    val name = remote.getString("name") ?: "Customer"
                    val stage = remote.getString("stage") ?: "New"
                    val place = remote.getString("place") ?: "School of Athens"
                    val ageCat = remote.getString("ageCategory") ?: "General"
                    val id = remote.getObjectId("_id")?.toHexString() ?: "REM-${System.currentTimeMillis() % 10000}"
                    val remoteCenterId = remote.getString("centerId") ?: remote.getString("center_id") ?: AppConfig.CENTER_ID
                    val remoteAssigned = remote.getString("assignedTo") ?: remote.getString("assigned_to")
                    val remoteDemoDoc = remote.get("demoSlot")
                    val remoteDemoSlotStr = when (remoteDemoDoc) {
                        is org.bson.Document -> {
                            remoteDemoDoc.getString("slot")
                                ?: remoteDemoDoc.getString("time")
                                ?: remoteDemoDoc.getDate("date")?.let { java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(it) }
                                ?: remoteDemoDoc.getString("status")
                        }
                        is String -> remoteDemoDoc
                        else -> null
                    }
                    val remoteDemoStatus = if (remoteDemoDoc is org.bson.Document) remoteDemoDoc.getString("status") else null
                    val remoteFollowUp = remote.getDate("followUpDate")?.time
                    val enqNum = try { remote.getInteger("enquiryNumber") } catch (_: Exception) { null }

                    val existingLocal = localEnquiries.firstOrNull { PhoneNumberUtils.normalize(it.phoneNumber).takeLast(10) == clean }
                    if (existingLocal == null) {
                        val localModel = Enquiry(
                            id = id,
                            name = name,
                            phoneNumber = phone,
                            company = place,
                            enquiryType = ageCat,
                            status = if (stage.equals("Enrolled", true)) "Enrolled" else stage,
                            notes = "Imported from MongoDB Atlas",
                            assignedTo = remoteAssigned,
                            centerId = remoteCenterId,
                            demoSlot = remoteDemoSlotStr,
                            demoStatus = remoteDemoStatus,
                            followUpDate = remoteFollowUp,
                            enquiryNumber = enqNum
                        )
                        dbHelper.insertOrUpdateEnquiry(localModel)
                        pulled++
                    } else {
                        // Keep local enquiry up-to-date with remote center, assignment, demo slot & follow-up date
                        val updated = existingLocal.copy(
                            centerId = remoteCenterId ?: existingLocal.centerId,
                            assignedTo = remoteAssigned ?: existingLocal.assignedTo,
                            demoSlot = remoteDemoSlotStr ?: existingLocal.demoSlot,
                            demoStatus = remoteDemoStatus ?: existingLocal.demoStatus,
                            followUpDate = remoteFollowUp ?: existingLocal.followUpDate
                        )
                        dbHelper.insertOrUpdateEnquiry(updated)
                    }
                }
            }

            val msg = buildString {
                if (inserted > 0) append("Uploaded $inserted new leads. ")
                if (updated > 0) append("Updated $updated leads. ")
                if (pulled > 0) append("Synced $pulled remote leads to phone.")
                if (inserted == 0 && updated == 0 && pulled == 0) append("All leads are completely in sync with MongoDB Atlas!")
            }

            Log.i(TAG, "Sync complete: $msg")
            MongoSyncResult(true, inserted, updated, pulled, msg)
        } catch (e: Exception) {
            Log.e(TAG, "Direct MongoDB sync failed: ${e.message}", e)
            MongoSyncResult(false, message = "MongoDB sync error: ${e.message}")
        }
    }

    /**
     * Auto-sync single lead immediately upon creation/update if AUTO_SYNC_MONGODB is true.
     * Note: "Wrong Number" outcome must NOT sync to MongoDB.
     */
    suspend fun autoSyncLead(enquiry: Enquiry): Boolean = withContext(Dispatchers.IO) {
        if (!AppConfig.AUTO_SYNC_MONGODB) return@withContext false
        if (enquiry.status.equals("Wrong Number", ignoreCase = true)) {
            Log.d(TAG, "Skipping MongoDB sync for 'Wrong Number': ${enquiry.phoneNumber}")
            return@withContext false
        }

        try {
            val client = getClient()
            val db = client.getDatabase(DB_NAME)
            val enquiriesCol = db.getCollection(COL_ENQUIRIES)
            val countersCol = db.getCollection(COL_COUNTERS)

            val clean = PhoneNumberUtils.normalize(enquiry.phoneNumber).takeLast(10)
            if (clean.isBlank()) return@withContext false

            val existing = enquiriesCol.find(Filters.regex("phoneNumber", "$clean$")).firstOrNull()
            if (existing != null) {
                val updates = mutableListOf(
                    Updates.set("stage", mapStatusToStage(enquiry.status)),
                    Updates.set("name", enquiry.name),
                    Updates.set("updatedAt", Date())
                )
                if (!enquiry.course.isNullOrBlank()) {
                    updates.add(Updates.set("selectedCourses", listOf(enquiry.course)))
                }
                if (!enquiry.ageCategory.isNullOrBlank()) {
                    updates.add(Updates.set("ageCategory", enquiry.ageCategory))
                }
                if (!enquiry.remarks.isNullOrBlank()) {
                    updates.add(Updates.set("notes", enquiry.remarks))
                }
                enquiriesCol.updateOne(
                    Filters.eq("_id", existing.getObjectId("_id")),
                    Updates.combine(updates)
                )
            } else {
                val counterDoc = countersCol.find(Filters.eq("_id", "enquiryNumber")).firstOrNull()
                val nextSeq = counterDoc?.getInteger("seq") ?: 124

                val newDoc = Document().apply {
                    append("enquiryNumber", nextSeq)
                    append("name", enquiry.name.ifBlank { "Customer" })
                    append("phoneNumber", formatE164(enquiry.phoneNumber))
                    append("foundUsBy", enquiry.leadSource ?: "Direct / Phone")
                    append("modeOfEnquiry", "Call")
                    append("ageCategory", enquiry.ageCategory ?: enquiry.enquiryType ?: "General")
                    append("classPreference", "Any")
                    append("selectedCourses", if (!enquiry.course.isNullOrBlank()) listOf(enquiry.course) else emptyList<String>())
                    append("stage", mapStatusToStage(enquiry.status))
                    append("demoSlot", Document().apply {
                        append("status", enquiry.demoStatus ?: "Scheduled")
                        append("postponeCount", 0)
                        append("confirmationMessage", null)
                    })
                    append("centerId", DEFAULT_CENTER_ID)
                    append("isTransferred", false)
                    append("place", enquiry.company ?: "Bangalore")
                    append("notes", enquiry.remarks ?: enquiry.notes ?: "")
                    append("stageHistory", emptyList<Document>())
                    append("followUpHistory", emptyList<Document>())
                    append("demoSlotHistory", emptyList<Document>())
                    append("centerTransfers", emptyList<Document>())
                    append("createdAt", Date())
                    append("updatedAt", Date())
                }
                enquiriesCol.insertOne(newDoc)
                countersCol.updateOne(
                    Filters.eq("_id", "enquiryNumber"),
                    Updates.set("seq", nextSeq + 1),
                    com.mongodb.client.model.UpdateOptions().upsert(true)
                )
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Auto-sync lead failed: ${e.message}")
            false
        }
    }

    /**
     * Updates lead name in MongoDB Atlas when contact name is updated on phone.
     */
    suspend fun updateLeadName(phoneNumber: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        if (!AppConfig.AUTO_SYNC_MONGODB || newName.isBlank()) return@withContext false
        try {
            val client = getClient()
            val db = client.getDatabase(DB_NAME)
            val enquiriesCol = db.getCollection(COL_ENQUIRIES)
            val clean = PhoneNumberUtils.normalize(phoneNumber).takeLast(10)
            if (clean.isBlank()) return@withContext false

            val result = enquiriesCol.updateOne(
                Filters.regex("phoneNumber", "$clean$"),
                Updates.combine(
                    Updates.set("name", newName),
                    Updates.set("updatedAt", Date())
                )
            )
            result.matchedCount > 0
        } catch (e: Exception) {
            Log.w(TAG, "Failed updating name in MongoDB: ${e.message}")
            false
        }
    }

    /**
     * Pulls staff and manager users from MongoDB Atlas `users` collection.
     * Imports username, email, role, phone, and BCrypt password hash.
     */
    suspend fun syncCenterUsers(context: Context): MongoSyncResult = withContext(Dispatchers.IO) {
        try {
            val client = getClient()
            val db = client.getDatabase(DB_NAME)
            val usersCol = db.getCollection("users")

            // Query non-student users (managers, academic/operations/media/purchase managers, faculty, admin)
            val filter = Filters.ne("role", "student")
            val cursor = usersCol.find(filter)

            val dbHelper = EnquiryDatabaseHelper(context)
            var count = 0

            for (doc in cursor) {
                val rawId = doc.get("_id")
                val id = rawId?.toString() ?: ""
                val username = doc.getString("username") ?: ""
                val email = doc.getString("email") ?: ""
                val roleStr = doc.getString("role") ?: "staff"
                val passwordHash = doc.getString("password") ?: ""
                val phone = doc.getString("phone") ?: ""
                val centerId = doc.getString("center_id") ?: AppConfig.CENTER_ID
                val status = doc.getString("status") ?: "active"

                if (id.isNotBlank()) {
                    val role = com.dialer.truedialer.data.model.UserRole.fromString(roleStr)
                    val user = com.dialer.truedialer.data.model.CenterUser(
                        id = id,
                        name = username.ifBlank { email },
                        role = role,
                        username = username,
                        email = email,
                        passwordHash = passwordHash,
                        pin = "9999",
                        phone = phone,
                        centerId = centerId,
                        isActive = status.equals("active", ignoreCase = true)
                    )
                    dbHelper.saveCenterUser(user)
                    count++
                }
            }

            Log.i(TAG, "Synced $count users from MongoDB Atlas users collection")
            MongoSyncResult(true, pulledCount = count, message = "Synced $count users from MongoDB Atlas!")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync center users: ${e.message}", e)
            MongoSyncResult(false, message = "User sync error: ${e.message}")
        }
    }
}
