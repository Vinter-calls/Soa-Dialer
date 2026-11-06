package com.dialer.truedialer.data.local

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.provider.CallLog
import androidx.core.content.ContextCompat
import com.dialer.truedialer.AppConfig
import com.dialer.truedialer.data.model.CenterUser
import com.dialer.truedialer.data.model.Enquiry
import com.dialer.truedialer.data.model.FollowUpReminder
import com.dialer.truedialer.data.model.MessageTemplate
import com.dialer.truedialer.data.model.UserRole
import com.dialer.truedialer.util.PhoneNumberUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class CallStatRecord(
    val id: Long,
    val phoneNumber: String,
    val callType: String,
    val ringDurationSeconds: Long,
    val talkDurationSeconds: Long,
    val timestamp: Long
)

data class CallStatSummary(
    val totalRingSeconds: Long,
    val totalTalkSeconds: Long,
    val avgRingSeconds: Long,
    val avgTalkSeconds: Long,
    val callsCount: Int
)

data class CallingReportMetrics(
    val totalCalls: Int = 0,
    val avgDurationSeconds: Long = 0,
    val outgoingCalls: Int = 0,
    val incomingCalls: Int = 0,
    val missedCalls: Int = 0,
    val connectedCalls: Int = 0,
    val followUpsPending: Int = 0,
    val newEnquiries: Int = 0,
    val demoBookings: Int = 0,
    val convertedCount: Int = 0
)

class EnquiryDatabaseHelper(private val context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "enquiry_dialer.db"
        const val DATABASE_VERSION = 9

        // Table Center Users
        const val TABLE_CENTER_USERS = "center_users"
        const val COL_USER_ID = "id"
        const val COL_USER_NAME = "name"
        const val COL_USER_ROLE = "role"
        const val COL_USER_PIN = "pin"
        const val COL_USER_PHONE = "phone"
        const val COL_USER_CENTER_ID = "center_id"
        const val COL_USER_ACTIVE = "is_active"
        const val COL_USER_EMAIL = "email"
        const val COL_USER_PASSWORD_HASH = "password_hash"
        const val COL_USER_USERNAME = "username"

        // Table Enquiries
        const val TABLE_ENQUIRIES = "enquiries"
        const val COL_ENQ_ID = "id"
        const val COL_ENQ_NAME = "name"
        const val COL_ENQ_PHONE = "phone_number"
        const val COL_ENQ_COMPANY = "company"
        const val COL_ENQ_TYPE = "enquiry_type"
        const val COL_ENQ_STATUS = "status"
        const val COL_ENQ_NOTES = "notes"
        const val COL_ENQ_EMAIL = "email"
        const val COL_ENQ_BUDGET = "budget"
        const val COL_ENQ_CREATED_AT = "created_at"
        const val COL_ENQ_IS_SPAM = "is_spam"
        const val COL_ENQ_ASSIGNED_TO = "assigned_to"

        // Extended CRM Columns
        const val COL_ENQ_COURSE = "course"
        const val COL_ENQ_AGE_CATEGORY = "age_category"
        const val COL_ENQ_LEAD_SOURCE = "lead_source"
        const val COL_ENQ_DEMO_STATUS = "demo_status"
        const val COL_ENQ_ADMISSION_STATUS = "admission_status"
        const val COL_ENQ_FOLLOW_UP_DATE = "follow_up_date"
        const val COL_ENQ_REMARKS = "remarks"
        const val COL_ENQ_IN_CALL_QUEUE = "in_call_queue"
        const val COL_ENQ_IS_FAVORITE = "is_favorite"
        const val COL_ENQ_CENTER_ID = "center_id"
        const val COL_ENQ_DEMO_SLOT = "demo_slot"
        const val COL_ENQ_NUMBER = "enquiry_number"

        // Table Call Notes
        const val TABLE_CALL_NOTES = "call_notes"
        const val COL_NOTE_ID = "id"
        const val COL_NOTE_PHONE = "phone_number"
        const val COL_NOTE_TEXT = "note_text"
        const val COL_NOTE_TIMESTAMP = "timestamp"

        // Table Follow-Ups
        const val TABLE_FOLLOW_UPS = "follow_ups"
        const val COL_FU_ID = "id"
        const val COL_FU_PHONE = "phone_number"
        const val COL_FU_NAME = "customer_name"
        const val COL_FU_NOTE = "note"
        const val COL_FU_TIME = "scheduled_time"
        const val COL_FU_COMPLETED = "is_completed"
        const val COL_FU_ASSIGNED_USER = "assigned_user_id"

        // Table Templates
        const val TABLE_TEMPLATES = "message_templates"
        const val COL_TMP_ID = "id"
        const val COL_TMP_TITLE = "title"
        const val COL_TMP_BODY = "body"

        // Table Call Stats (Ring duration & Talk time)
        const val TABLE_CALL_STATS = "call_stats"
        const val COL_CS_ID = "id"
        const val COL_CS_PHONE = "phone_number"
        const val COL_CS_TYPE = "call_type"
        const val COL_CS_RING_DURATION = "ring_duration_seconds"
        const val COL_CS_TALK_DURATION = "talk_duration_seconds"
        const val COL_CS_TIMESTAMP = "timestamp"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_ENQUIRIES (
                $COL_ENQ_ID TEXT PRIMARY KEY,
                $COL_ENQ_NAME TEXT NOT NULL,
                $COL_ENQ_PHONE TEXT NOT NULL,
                $COL_ENQ_COMPANY TEXT,
                $COL_ENQ_TYPE TEXT,
                $COL_ENQ_STATUS TEXT,
                $COL_ENQ_NOTES TEXT,
                $COL_ENQ_EMAIL TEXT,
                $COL_ENQ_BUDGET TEXT,
                $COL_ENQ_CREATED_AT TEXT,
                $COL_ENQ_IS_SPAM INTEGER DEFAULT 0,
                $COL_ENQ_ASSIGNED_TO TEXT,
                $COL_ENQ_COURSE TEXT,
                $COL_ENQ_AGE_CATEGORY TEXT,
                $COL_ENQ_LEAD_SOURCE TEXT,
                $COL_ENQ_DEMO_STATUS TEXT,
                $COL_ENQ_ADMISSION_STATUS TEXT,
                $COL_ENQ_FOLLOW_UP_DATE INTEGER,
                $COL_ENQ_REMARKS TEXT,
                $COL_ENQ_IN_CALL_QUEUE INTEGER DEFAULT 0,
                $COL_ENQ_IS_FAVORITE INTEGER DEFAULT 0,
                $COL_ENQ_CENTER_ID TEXT,
                $COL_ENQ_DEMO_SLOT TEXT,
                $COL_ENQ_NUMBER INTEGER
            )
            """.trimIndent()
        )

        db.execSQL("CREATE INDEX IF NOT EXISTS idx_enquiry_phone ON $TABLE_ENQUIRIES ($COL_ENQ_PHONE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_enquiry_center ON $TABLE_ENQUIRIES ($COL_ENQ_CENTER_ID)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_enquiry_assigned ON $TABLE_ENQUIRIES ($COL_ENQ_ASSIGNED_TO)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_enquiry_status ON $TABLE_ENQUIRIES ($COL_ENQ_STATUS)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_enquiry_followup ON $TABLE_ENQUIRIES ($COL_ENQ_FOLLOW_UP_DATE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_enquiry_queue ON $TABLE_ENQUIRIES ($COL_ENQ_IN_CALL_QUEUE)")

        db.execSQL(
            """
            CREATE TABLE $TABLE_CALL_NOTES (
                $COL_NOTE_ID TEXT PRIMARY KEY,
                $COL_NOTE_PHONE TEXT NOT NULL,
                $COL_NOTE_TEXT TEXT NOT NULL,
                $COL_NOTE_TIMESTAMP INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_FOLLOW_UPS (
                $COL_FU_ID TEXT PRIMARY KEY,
                $COL_FU_PHONE TEXT NOT NULL,
                $COL_FU_NAME TEXT NOT NULL,
                $COL_FU_NOTE TEXT,
                $COL_FU_TIME INTEGER NOT NULL,
                $COL_FU_COMPLETED INTEGER DEFAULT 0,
                $COL_FU_ASSIGNED_USER TEXT
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_TEMPLATES (
                $COL_TMP_ID TEXT PRIMARY KEY,
                $COL_TMP_TITLE TEXT NOT NULL,
                $COL_TMP_BODY TEXT NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_CALL_STATS (
                $COL_CS_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_CS_PHONE TEXT NOT NULL,
                $COL_CS_TYPE TEXT NOT NULL,
                $COL_CS_RING_DURATION INTEGER DEFAULT 0,
                $COL_CS_TALK_DURATION INTEGER DEFAULT 0,
                $COL_CS_TIMESTAMP INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL("CREATE INDEX IF NOT EXISTS idx_call_stats_phone ON $TABLE_CALL_STATS ($COL_CS_PHONE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_call_stats_time ON $TABLE_CALL_STATS ($COL_CS_TIMESTAMP)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_fu_time_comp ON $TABLE_FOLLOW_UPS ($COL_FU_TIME, $COL_FU_COMPLETED)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_CENTER_USERS (
                $COL_USER_ID TEXT PRIMARY KEY,
                $COL_USER_NAME TEXT NOT NULL,
                $COL_USER_ROLE TEXT NOT NULL,
                $COL_USER_PIN TEXT NOT NULL,
                $COL_USER_PHONE TEXT,
                $COL_USER_CENTER_ID TEXT NOT NULL,
                $COL_USER_ACTIVE INTEGER DEFAULT 1,
                $COL_USER_EMAIL TEXT DEFAULT '',
                $COL_USER_PASSWORD_HASH TEXT DEFAULT '',
                $COL_USER_USERNAME TEXT DEFAULT ''
            )
            """.trimIndent()
        )

        // Seed default WhatsApp Business templates
        insertInitialTemplates(db)
        // Seed initial sample enquiry cache
        insertInitialEnquiries(db)
        // Seed initial center staff and manager from MongoDB database
        insertInitialUsers(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 3) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_CALL_STATS (
                    $COL_CS_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    $COL_CS_PHONE TEXT NOT NULL,
                    $COL_CS_TYPE TEXT NOT NULL,
                    $COL_CS_RING_DURATION INTEGER DEFAULT 0,
                    $COL_CS_TALK_DURATION INTEGER DEFAULT 0,
                    $COL_CS_TIMESTAMP INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_call_stats_phone ON $TABLE_CALL_STATS ($COL_CS_PHONE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_call_stats_time ON $TABLE_CALL_STATS ($COL_CS_TIMESTAMP)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_fu_time_comp ON $TABLE_FOLLOW_UPS ($COL_FU_TIME, $COL_FU_COMPLETED)")
        }

        if (oldVersion < 4) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_CENTER_USERS (
                    $COL_USER_ID TEXT PRIMARY KEY,
                    $COL_USER_NAME TEXT NOT NULL,
                    $COL_USER_ROLE TEXT NOT NULL,
                    $COL_USER_PIN TEXT NOT NULL,
                    $COL_USER_PHONE TEXT,
                    $COL_USER_CENTER_ID TEXT NOT NULL,
                    $COL_USER_ACTIVE INTEGER DEFAULT 1,
                    $COL_USER_EMAIL TEXT DEFAULT '',
                    $COL_USER_PASSWORD_HASH TEXT DEFAULT '',
                    $COL_USER_USERNAME TEXT DEFAULT ''
                )
                """.trimIndent()
            )
            try {
                db.execSQL("ALTER TABLE $TABLE_ENQUIRIES ADD COLUMN $COL_ENQ_ASSIGNED_TO TEXT")
            } catch (ignored: Exception) {}
            try {
                db.execSQL("ALTER TABLE $TABLE_FOLLOW_UPS ADD COLUMN $COL_FU_ASSIGNED_USER TEXT")
            } catch (ignored: Exception) {}
            insertInitialUsers(db)
        }

        if (oldVersion < 5) {
            try { db.execSQL("ALTER TABLE $TABLE_ENQUIRIES ADD COLUMN $COL_ENQ_COURSE TEXT") } catch (ignored: Exception) {}
            try { db.execSQL("ALTER TABLE $TABLE_ENQUIRIES ADD COLUMN $COL_ENQ_AGE_CATEGORY TEXT") } catch (ignored: Exception) {}
            try { db.execSQL("ALTER TABLE $TABLE_ENQUIRIES ADD COLUMN $COL_ENQ_LEAD_SOURCE TEXT") } catch (ignored: Exception) {}
            try { db.execSQL("ALTER TABLE $TABLE_ENQUIRIES ADD COLUMN $COL_ENQ_DEMO_STATUS TEXT") } catch (ignored: Exception) {}
            try { db.execSQL("ALTER TABLE $TABLE_ENQUIRIES ADD COLUMN $COL_ENQ_ADMISSION_STATUS TEXT") } catch (ignored: Exception) {}
            try { db.execSQL("ALTER TABLE $TABLE_ENQUIRIES ADD COLUMN $COL_ENQ_FOLLOW_UP_DATE INTEGER") } catch (ignored: Exception) {}
            try { db.execSQL("ALTER TABLE $TABLE_ENQUIRIES ADD COLUMN $COL_ENQ_REMARKS TEXT") } catch (ignored: Exception) {}
            try { db.execSQL("ALTER TABLE $TABLE_ENQUIRIES ADD COLUMN $COL_ENQ_IN_CALL_QUEUE INTEGER DEFAULT 0") } catch (ignored: Exception) {}
            try { db.execSQL("ALTER TABLE $TABLE_ENQUIRIES ADD COLUMN $COL_ENQ_IS_FAVORITE INTEGER DEFAULT 0") } catch (ignored: Exception) {}
        }

        if (oldVersion < 6) {
            try { db.execSQL("ALTER TABLE $TABLE_CENTER_USERS ADD COLUMN $COL_USER_EMAIL TEXT DEFAULT ''") } catch (ignored: Exception) {}
            try { db.execSQL("ALTER TABLE $TABLE_CENTER_USERS ADD COLUMN $COL_USER_PASSWORD_HASH TEXT DEFAULT ''") } catch (ignored: Exception) {}
            try { db.execSQL("ALTER TABLE $TABLE_CENTER_USERS ADD COLUMN $COL_USER_USERNAME TEXT DEFAULT ''") } catch (ignored: Exception) {}
            insertInitialUsers(db)
        }

        if (oldVersion < 7) {
            try { db.execSQL("ALTER TABLE $TABLE_ENQUIRIES ADD COLUMN $COL_ENQ_CENTER_ID TEXT") } catch (ignored: Exception) {}
            try { db.execSQL("ALTER TABLE $TABLE_ENQUIRIES ADD COLUMN $COL_ENQ_DEMO_SLOT TEXT") } catch (ignored: Exception) {}
        }

        if (oldVersion < 8) {
            cleanupSampleData(db)
        }

        if (oldVersion < 9) {
            try { db.execSQL("ALTER TABLE $TABLE_ENQUIRIES ADD COLUMN $COL_ENQ_NUMBER INTEGER") } catch (ignored: Exception) {}
            insertInitialEnquiries(db)
        }
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        cleanupSampleData(db)
        try {
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_enquiry_phone ON $TABLE_ENQUIRIES ($COL_ENQ_PHONE)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_enquiry_center ON $TABLE_ENQUIRIES ($COL_ENQ_CENTER_ID)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_enquiry_assigned ON $TABLE_ENQUIRIES ($COL_ENQ_ASSIGNED_TO)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_enquiry_status ON $TABLE_ENQUIRIES ($COL_ENQ_STATUS)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_enquiry_followup ON $TABLE_ENQUIRIES ($COL_ENQ_FOLLOW_UP_DATE)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_enquiry_queue ON $TABLE_ENQUIRIES ($COL_ENQ_IN_CALL_QUEUE)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_call_stats_phone ON $TABLE_CALL_STATS ($COL_CS_PHONE)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_call_stats_time ON $TABLE_CALL_STATS ($COL_CS_TIMESTAMP)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_fu_time_comp ON $TABLE_FOLLOW_UPS ($COL_FU_TIME, $COL_FU_COMPLETED)")
        } catch (_: Exception) {}
    }

    fun cleanupSampleData(db: SQLiteDatabase = writableDatabase) {
        try {
            // Delete any sample, mock, or test enquiries
            db.execSQL(
                """
                DELETE FROM $TABLE_ENQUIRIES 
                WHERE id LIKE 'ENQ-100%' 
                   OR id LIKE 'ENQ-MOCK%' 
                   OR name LIKE '%test%' 
                   OR name LIKE '%audit test%' 
                   OR name = 'Rajesh Sharma' 
                   OR name = 'Ananya Patel' 
                   OR name = 'Vikram Malhotra' 
                   OR name = 'Priya Sundaram' 
                   OR name = 'David Miller' 
                   OR name = 'Potential Telemarketer'
                   OR phoneNumber IN ('7877878', '8665656', '999999999', '9999999999', '+919876543210', '+919812345678', '+919988776655', '+919701234567', '+14155552671', '+911400012345')
                """.trimIndent()
            )

            // Delete sample/test users
            db.execSQL(
                """
                DELETE FROM $TABLE_CENTER_USERS 
                WHERE name LIKE 'Test%' 
                   OR email LIKE '%@test.com' 
                   OR id IN ('69a06b4073d8ffa4cf275add', '69999ecf489ac64139ee0354', 'USR-01')
                """.trimIndent()
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun insertInitialUsers(db: SQLiteDatabase) {
        val users = listOf(
            // MongoDB Managers (Can Log In via BCrypt password verification)
            CenterUser(
                id = "681ec306cf58144ebdd83307",
                name = "Manager whitefield",
                role = UserRole.MANAGER,
                username = "Manager whitefield",
                email = "whitefield@schoolofathens.art",
                passwordHash = "\$2a\$10\$U05ZM7S7MjVnc7wlsokJH.N6VMTEHdjd8N2W2hxu/pxp3wbyEB362",
                pin = "9999",
                phone = "9663155995",
                centerId = "67cd080a040cb823ea6c0744"
            ),
            CenterUser(
                id = "68071128dbce2208a004f1aa",
                name = "Manager Brookfield",
                role = UserRole.MANAGER,
                username = "Manager Brookfield",
                email = "brookfield@schoolofathens.art",
                passwordHash = "\$2a\$10\$QGL9aRFr0MTQ9kXNvhh7.ejjL6MM9phX48KSZH8Zea8oZvGSVrQ6y",
                pin = "9999",
                phone = "7356301528",
                centerId = "67c00eb2073609b23054ca01"
            ),
            CenterUser(
                id = "680654d1dbce2208a004f0c9",
                name = "Assistant Manager",
                role = UserRole.MANAGER,
                username = "Assistant Manager",
                email = "am@schoolofathens.art",
                passwordHash = "\$2a\$10\$fnvxujqOYhtRl5z1KcqS0etE22CYwrIRg9VDvGulSQ3yYZnFZgLz.",
                pin = "9999",
                phone = "",
                centerId = "67c00eb2073609b23054ca01"
            ),
            CenterUser(
                id = "6a6e12742b5dc6e84480a9b4",
                name = "Arshad",
                role = UserRole.MEDIA_MANAGER,
                username = "Arshad",
                email = "media_manager@schoolofathens.art",
                passwordHash = "\$2a\$10\$RbNjyF2zo7KWwh5pId.QU.QAQaGXwapO8GSOm5iIPqn0orE9QChV6",
                pin = "9999",
                phone = "",
                centerId = "67cd080a040cb823ea6c0744"
            ),
            CenterUser(
                id = "6a70c3906a68af1a630ba2bf",
                name = "purchase manager",
                role = UserRole.PURCHASE_MANAGER,
                username = "purchase manager",
                email = "purchase@schoolofathens.art",
                passwordHash = "\$2a\$10\$aIIgudulDoNDrOqTwrhgVe6ZB302DWEe3MPTZpP3OBZBa356ThOMS",
                pin = "9999",
                phone = "",
                centerId = "6a70cad580c43932de3f7c8d"
            ),
            CenterUser(
                id = "67c28ee4fbc1322bf69dddcf",
                name = "Admin",
                role = UserRole.ADMIN,
                username = "Admin",
                email = "admin@schoolofathens.art",
                passwordHash = "\$2a\$12\$aZWnQIzhpH06yC1jgJnU1Oi6ug4iuRDVVkeQrrzje2fjiN77vv1Dm",
                pin = "9999",
                phone = "",
                centerId = "67cd080a040cb823ea6c0744"
            ),

            // MongoDB Faculty & Staff (Shown in staff directory, cannot log in on this phone)
            CenterUser(
                id = "680710a0dbce2208a004f199",
                name = "Harini K",
                role = UserRole.FACULTY,
                username = "Harini K",
                email = "harini@schoolofathens.art",
                passwordHash = "\$2a\$10\$C37I.FoxJS8CGAdgFLoG4.tSz3d67Ti8BvJd10jgNJaZHQ2olmJHu",
                phone = "6362188686",
                centerId = "67c00eb2073609b23054ca01"
            ),
            CenterUser(
                id = "680711a8dbce2208a004f1bb",
                name = "Sandipa Roy",
                role = UserRole.FACULTY,
                username = "Sandipa Roy",
                email = "sandipa@schoolofathens.art",
                passwordHash = "\$2a\$10\$qIK759dynNHLNi1suxOvgOqtpJG6PhxAu1qrg0XDas2bXMfgTc84K",
                phone = "8335904560",
                centerId = "67cd080a040cb823ea6c0744"
            ),
            CenterUser(
                id = "680711ebdbce2208a004f1cc",
                name = "Rohan Das",
                role = UserRole.FACULTY,
                username = "Rohan Das",
                email = "Rohandas@schoolofathens.art",
                passwordHash = "\$2a\$10\$x5CMmNbmlJlDmOZDNHIRYuIeht.RDBsfidP304X5cqDxiLcGoPZj6",
                phone = "9932449027",
                centerId = "67cd080a040cb823ea6c0744"
            ),
            CenterUser(
                id = "688d95fc572adb9e78008c4f",
                name = "Maliya Pandey",
                role = UserRole.FACULTY,
                username = "Maliya Pandey",
                email = "maliya@schoolofathens.art",
                passwordHash = "\$2a\$10\$G7Cpaqd.zZSVTbb4NKavq.ZXDgt/pFsXKXHPkY1SiOL2/P4ZwD8ni",
                phone = "9560771401",
                centerId = "67cd080a040cb823ea6c0744"
            ),
            CenterUser(
                id = "692d6c265c73a0bfa3e6d574",
                name = "PUNARVA V R",
                role = UserRole.FACULTY,
                username = "PUNARVA V R",
                email = "punarva@schoolofathens.art",
                passwordHash = "\$2a\$10\$OuTs6CX1vQkWMWBo7.VKnelqcLf/ZkubM6pBOj.yBp/binNKi.gLa",
                phone = "9481325814",
                centerId = "67cd080a040cb823ea6c0744"
            )
        )
        for (user in users) {
            val cv = ContentValues().apply {
                put(COL_USER_ID, user.id)
                put(COL_USER_NAME, user.name)
                put(COL_USER_ROLE, user.role.name)
                put(COL_USER_PIN, user.pin)
                put(COL_USER_PHONE, user.phone)
                put(COL_USER_CENTER_ID, user.centerId)
                put(COL_USER_ACTIVE, if (user.isActive) 1 else 0)
                put(COL_USER_EMAIL, user.email)
                put(COL_USER_PASSWORD_HASH, user.passwordHash)
                put(COL_USER_USERNAME, user.username)
            }
            db.insertWithOnConflict(TABLE_CENTER_USERS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    private fun insertInitialTemplates(db: SQLiteDatabase) {
        val templates = listOf(
            MessageTemplate(
                id = "tmpl_1",
                title = "Catalog & Brochure Introduction",
                body = "Hello {name}, thank you for connecting with {company}! Please find our product brochure and catalog: https://aethens.com/catalog. Let us know if you would like to schedule a quick demo!"
            ),
            MessageTemplate(
                id = "tmpl_2",
                title = "Proposal & Documents Follow-up",
                body = "Hi {name}, following up on our discussion regarding {requirement}. Please review our portfolio and case studies: https://aethens.com/portfolio. Looking forward to your thoughts!"
            ),
            MessageTemplate(
                id = "tmpl_3",
                title = "Schedule Demo / Meeting Link",
                body = "Hello {name}, please choose a convenient time slot for our demonstration from our calendar: https://calendly.com/aethens. Talk soon!"
            ),
            MessageTemplate(
                id = "tmpl_4",
                title = "Missed Call Callback Alert",
                body = "Hello {name}, I noticed your missed call. How can we assist you today? Feel free to reply here on WhatsApp or browse our services: https://aethens.com."
            ),
            MessageTemplate(
                id = "tmpl_5",
                title = "Quotation & Payment Link",
                body = "Hi {name}, thank you for choosing {company}. You can view the quotation and complete payment securely here: https://pay.aethens.com."
            )
        )

        for (tmpl in templates) {
            val cv = ContentValues().apply {
                put(COL_TMP_ID, tmpl.id)
                put(COL_TMP_TITLE, tmpl.title)
                put(COL_TMP_BODY, tmpl.body)
            }
            db.insert(TABLE_TEMPLATES, null, cv)
        }
    }

    private fun insertInitialEnquiries(db: SQLiteDatabase) {
        try {
            val inputStream = context.assets.open("initial_enquiries.json")
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val jsonArray = org.json.JSONArray(jsonString)

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val name = obj.optString("name", "Enquiry Lead")
                val phone = obj.optString("phoneNumber", "")
                if (name.contains("test", ignoreCase = true) || phone in listOf("7877878", "8665656", "999999999", "9999999999")) {
                    continue
                }
                val cv = ContentValues().apply {
                    put(COL_ENQ_ID, obj.optString("id", "ENQ-$i"))
                    put(COL_ENQ_NAME, name)
                    put(COL_ENQ_PHONE, phone)
                    put(COL_ENQ_COMPANY, obj.optString("company", "School of Athens"))
                    put(COL_ENQ_TYPE, obj.optString("enquiryType", "General Enquiry"))
                    put(COL_ENQ_STATUS, obj.optString("status", "New Lead"))
                    put(COL_ENQ_NOTES, obj.optString("notes", ""))
                    put(COL_ENQ_EMAIL, obj.optString("email", ""))
                    put(COL_ENQ_BUDGET, obj.optString("budget", ""))
                    put(COL_ENQ_CREATED_AT, obj.optString("createdAt", ""))
                    put(COL_ENQ_IS_SPAM, 0)
                    val enqNum = if (obj.has("enquiryNumber")) obj.optInt("enquiryNumber") else null
                    if (enqNum != null) {
                        put(COL_ENQ_NUMBER, enqNum)
                    }
                }
                db.insertWithOnConflict(TABLE_ENQUIRIES, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- ENQUIRY CRUD ---

    fun insertOrUpdateEnquiry(enquiry: Enquiry): Boolean {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put(COL_ENQ_ID, enquiry.id)
            put(COL_ENQ_NAME, enquiry.name)
            put(COL_ENQ_PHONE, enquiry.phoneNumber)
            put(COL_ENQ_COMPANY, enquiry.company)
            put(COL_ENQ_TYPE, enquiry.enquiryType)
            put(COL_ENQ_STATUS, enquiry.status)
            put(COL_ENQ_NOTES, enquiry.notes)
            put(COL_ENQ_EMAIL, enquiry.email)
            put(COL_ENQ_BUDGET, enquiry.budget)
            put(COL_ENQ_CREATED_AT, enquiry.createdAt ?: System.currentTimeMillis().toString())
            put(COL_ENQ_IS_SPAM, if (enquiry.status == "Spam") 1 else 0)
            put(COL_ENQ_ASSIGNED_TO, enquiry.assignedTo ?: AppConfig.CURRENT_USER_ID)
            put(COL_ENQ_COURSE, enquiry.course)
            put(COL_ENQ_AGE_CATEGORY, enquiry.ageCategory)
            put(COL_ENQ_LEAD_SOURCE, enquiry.leadSource ?: "Website")
            put(COL_ENQ_DEMO_STATUS, enquiry.demoStatus)
            put(COL_ENQ_ADMISSION_STATUS, enquiry.admissionStatus)
            put(COL_ENQ_FOLLOW_UP_DATE, enquiry.followUpDate)
            put(COL_ENQ_REMARKS, enquiry.remarks)
            put(COL_ENQ_IN_CALL_QUEUE, if (enquiry.inCallQueue) 1 else 0)
            put(COL_ENQ_IS_FAVORITE, if (enquiry.isFavorite) 1 else 0)
            put(COL_ENQ_CENTER_ID, enquiry.centerId ?: AppConfig.CENTER_ID)
            put(COL_ENQ_DEMO_SLOT, enquiry.demoSlot)
            if (enquiry.enquiryNumber != null) {
                put(COL_ENQ_NUMBER, enquiry.enquiryNumber)
            }
        }
        val result = db.insertWithOnConflict(TABLE_ENQUIRIES, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        return result != -1L
    }

    fun updateEnquiryStatus(id: String, newStatus: String): Boolean {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put(COL_ENQ_STATUS, newStatus)
            if (newStatus.equals("Demo Attended", true)) {
                put(COL_ENQ_DEMO_STATUS, "Attended")
            }
        }
        return db.update(TABLE_ENQUIRIES, cv, "$COL_ENQ_ID = ?", arrayOf(id)) > 0
    }

    fun updateEnquiryNameByPhone(phoneNumber: String, newName: String): Boolean {
        if (phoneNumber.isBlank() || newName.isBlank()) return false
        val db = writableDatabase
        val cv = ContentValues().apply {
            put(COL_ENQ_NAME, newName)
        }
        val clean = PhoneNumberUtils.extractNationalNumber(phoneNumber)
        val rows = if (clean.length >= 10) {
            db.update(TABLE_ENQUIRIES, cv, "$COL_ENQ_PHONE LIKE ?", arrayOf("%$clean"))
        } else {
            db.update(TABLE_ENQUIRIES, cv, "$COL_ENQ_PHONE = ?", arrayOf(phoneNumber))
        }
        return rows > 0
    }

    fun toggleCallQueue(enquiryId: String, inQueue: Boolean): Boolean {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put(COL_ENQ_IN_CALL_QUEUE, if (inQueue) 1 else 0)
        }
        return db.update(TABLE_ENQUIRIES, cv, "$COL_ENQ_ID = ?", arrayOf(enquiryId)) > 0
    }

    fun getCallQueue(): List<Enquiry> {
        val list = mutableListOf<Enquiry>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_ENQUIRIES WHERE $COL_ENQ_IN_CALL_QUEUE = 1 ORDER BY $COL_ENQ_NAME ASC", null)
        cursor.use {
            while (it.moveToNext()) {
                list.add(cursorToEnquiry(it))
            }
        }
        return list
    }

    fun toggleFavoriteEnquiry(enquiryId: String, isFavorite: Boolean): Boolean {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put(COL_ENQ_IS_FAVORITE, if (isFavorite) 1 else 0)
        }
        return db.update(TABLE_ENQUIRIES, cv, "$COL_ENQ_ID = ?", arrayOf(enquiryId)) > 0
    }

    fun findEnquiryByPhone(phoneNumber: String): Enquiry? {
        val digits = phoneNumber.replace("[^0-9]".toRegex(), "")
        if (digits.isBlank()) return null
        val searchSuffix = if (digits.length >= 10) digits.takeLast(10) else digits

        val db = readableDatabase
        val cursor = if (digits.length >= 10) {
            db.rawQuery(
                "SELECT * FROM $TABLE_ENQUIRIES WHERE $COL_ENQ_PHONE LIKE ? LIMIT 1",
                arrayOf("%$searchSuffix")
            )
        } else {
            db.rawQuery(
                "SELECT * FROM $TABLE_ENQUIRIES WHERE $COL_ENQ_PHONE = ? LIMIT 1",
                arrayOf(phoneNumber)
            )
        }
        cursor.use {
            if (it.moveToFirst()) {
                return cursorToEnquiry(it)
            }
        }
        return null
    }

    fun getAllEnquiries(): List<Enquiry> {
        val list = mutableListOf<Enquiry>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_ENQUIRIES ORDER BY $COL_ENQ_NAME ASC", null)
        cursor.use {
            while (it.moveToNext()) {
                list.add(cursorToEnquiry(it))
            }
        }
        return list
    }

    fun searchEnquiries(query: String): List<Enquiry> {
        val list = mutableListOf<Enquiry>()
        val db = readableDatabase
        val wild = "%${query.trim()}%"
        val cursor = db.rawQuery(
            """
            SELECT * FROM $TABLE_ENQUIRIES 
            WHERE $COL_ENQ_NAME LIKE ? 
               OR $COL_ENQ_PHONE LIKE ? 
               OR $COL_ENQ_COMPANY LIKE ? 
               OR $COL_ENQ_TYPE LIKE ? 
               OR $COL_ENQ_STATUS LIKE ?
               OR $COL_ENQ_NOTES LIKE ?
            ORDER BY $COL_ENQ_NAME ASC
            """.trimIndent(),
            arrayOf(wild, wild, wild, wild, wild, wild)
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(cursorToEnquiry(it))
            }
        }
        return list
    }

    fun markAsSpam(phoneNumber: String, isSpam: Boolean): Boolean {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put(COL_ENQ_IS_SPAM, if (isSpam) 1 else 0)
            if (isSpam) put(COL_ENQ_STATUS, "Spam")
        }
        val clean = PhoneNumberUtils.extractNationalNumber(phoneNumber)
        val rows = if (clean.length >= 10) {
            db.update(TABLE_ENQUIRIES, cv, "$COL_ENQ_PHONE LIKE ?", arrayOf("%$clean"))
        } else {
            db.update(TABLE_ENQUIRIES, cv, "$COL_ENQ_PHONE = ?", arrayOf(phoneNumber))
        }
        if (rows == 0 && isSpam) {
            // Insert new spam record
            insertOrUpdateEnquiry(
                Enquiry(
                    id = "SPAM-${System.currentTimeMillis() % 10000}",
                    name = "Marked Spam",
                    phoneNumber = phoneNumber,
                    status = "Spam",
                    notes = "Marked as unwanted / spam call"
                )
            )
        }
        return true
    }

    fun isNumberSpam(phoneNumber: String): Boolean {
        val enquiry = findEnquiryByPhone(phoneNumber)
        return enquiry?.status == "Spam"
    }

    // --- CENTER USER MANAGEMENT ---

    fun getCenterUsers(): List<CenterUser> {
        val list = mutableListOf<CenterUser>()
        try {
            val db = readableDatabase
            val cursor = db.rawQuery("SELECT * FROM $TABLE_CENTER_USERS WHERE $COL_USER_ACTIVE = 1 ORDER BY $COL_USER_ROLE DESC, $COL_USER_NAME ASC", null)
            cursor.use {
                val emailIdx = it.getColumnIndex(COL_USER_EMAIL)
                val pwdIdx = it.getColumnIndex(COL_USER_PASSWORD_HASH)
                val usernameIdx = it.getColumnIndex(COL_USER_USERNAME)

                while (it.moveToNext()) {
                    val roleStr = it.getString(it.getColumnIndexOrThrow(COL_USER_ROLE)) ?: "STAFF"
                    val role = UserRole.fromString(roleStr)
                    val email = if (emailIdx >= 0) it.getString(emailIdx) ?: "" else ""
                    val pwdHash = if (pwdIdx >= 0) it.getString(pwdIdx) ?: "" else ""
                    val username = if (usernameIdx >= 0) it.getString(usernameIdx) ?: "" else ""

                    list.add(
                        CenterUser(
                            id = it.getString(it.getColumnIndexOrThrow(COL_USER_ID)),
                            name = it.getString(it.getColumnIndexOrThrow(COL_USER_NAME)),
                            role = role,
                            username = username,
                            email = email,
                            passwordHash = pwdHash,
                            pin = it.getString(it.getColumnIndexOrThrow(COL_USER_PIN)) ?: "1234",
                            phone = it.getString(it.getColumnIndexOrThrow(COL_USER_PHONE)) ?: "",
                            centerId = it.getString(it.getColumnIndexOrThrow(COL_USER_CENTER_ID)) ?: "CTR-01",
                            isActive = it.getInt(it.getColumnIndexOrThrow(COL_USER_ACTIVE)) == 1
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (list.isEmpty()) {
            insertInitialUsers(writableDatabase)
            return getCenterUsers()
        }
        return list
    }

    fun saveCenterUser(user: CenterUser): Boolean {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put(COL_USER_ID, user.id)
            put(COL_USER_NAME, user.name)
            put(COL_USER_ROLE, user.role.name)
            put(COL_USER_PIN, user.pin)
            put(COL_USER_PHONE, user.phone)
            put(COL_USER_CENTER_ID, user.centerId)
            put(COL_USER_ACTIVE, if (user.isActive) 1 else 0)
            put(COL_USER_EMAIL, user.email)
            put(COL_USER_PASSWORD_HASH, user.passwordHash)
            put(COL_USER_USERNAME, user.username)
        }
        return db.insertWithOnConflict(TABLE_CENTER_USERS, null, cv, SQLiteDatabase.CONFLICT_REPLACE) != -1L
    }

    /**
     * Authenticates a user by ID and entered password/pin.
     * Enforces security rule: ONLY users with Role Manager / Admin can log in!
     */
    fun authenticateUser(userId: String, candidate: String): Boolean {
        val users = getCenterUsers()
        val user = users.find { it.id == userId } ?: return false
        if (!user.canLogin) return false // Role Manager only!

        val clean = candidate.trim()
        if (clean.isBlank()) return false

        // 1. Verify against BCrypt password hash from MongoDB
        if (user.passwordHash.isNotBlank()) {
            try {
                if (org.mindrot.jbcrypt.BCrypt.checkpw(clean, user.passwordHash)) {
                    return true
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 2. Fallback to PIN if configured
        if (user.pin.isNotBlank() && user.pin == clean) {
            return true
        }

        return false
    }

    /**
     * Authenticate by Username or Email + Password (from MongoDB BCrypt hash).
     * Enforces security rule: ONLY users with Role Manager / Admin can log in!
     */
    fun authenticateByEmailOrUsername(identifier: String, candidate: String): CenterUser? {
        val cleanId = identifier.trim()
        val cleanPwd = candidate.trim()
        if (cleanId.isBlank() || cleanPwd.isBlank()) return null

        val users = getCenterUsers()
        val user = users.find {
            it.email.equals(cleanId, ignoreCase = true) ||
            it.username.equals(cleanId, ignoreCase = true) ||
            it.name.equals(cleanId, ignoreCase = true) ||
            it.id.equals(cleanId, ignoreCase = true)
        } ?: return null

        if (!user.canLogin) return null // Only users with manager role can log in!

        // 1. Verify against BCrypt password hash from MongoDB
        if (user.passwordHash.isNotBlank()) {
            try {
                if (org.mindrot.jbcrypt.BCrypt.checkpw(cleanPwd, user.passwordHash)) {
                    return user
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 2. Fallback to PIN
        if (user.pin.isNotBlank() && user.pin == cleanPwd) {
            return user
        }

        return null
    }

    // --- TEMPLATES CRUD ---

    fun getTemplates(): List<MessageTemplate> {
        val list = mutableListOf<MessageTemplate>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_TEMPLATES", null)
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    MessageTemplate(
                        id = it.getString(it.getColumnIndexOrThrow(COL_TMP_ID)),
                        title = it.getString(it.getColumnIndexOrThrow(COL_TMP_TITLE)),
                        body = it.getString(it.getColumnIndexOrThrow(COL_TMP_BODY))
                    )
                )
            }
        }
        return list
    }

    fun saveTemplate(template: MessageTemplate): Boolean {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put(COL_TMP_ID, template.id)
            put(COL_TMP_TITLE, template.title)
            put(COL_TMP_BODY, template.body)
        }
        return db.insertWithOnConflict(TABLE_TEMPLATES, null, cv, SQLiteDatabase.CONFLICT_REPLACE) != -1L
    }

    // --- FOLLOW-UP REMINDERS ---

    fun saveFollowUp(reminder: FollowUpReminder): Boolean {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put(COL_FU_ID, reminder.id)
            put(COL_FU_PHONE, reminder.phoneNumber)
            put(COL_FU_NAME, reminder.customerName)
            put(COL_FU_NOTE, reminder.note)
            put(COL_FU_TIME, reminder.scheduledTimeEpochMs)
            put(COL_FU_COMPLETED, if (reminder.isCompleted) 1 else 0)
            put(COL_FU_ASSIGNED_USER, reminder.assignedUserId ?: AppConfig.CURRENT_USER_ID)
        }
        return db.insertWithOnConflict(TABLE_FOLLOW_UPS, null, cv, SQLiteDatabase.CONFLICT_REPLACE) != -1L
    }

    fun getPendingFollowUps(forUserId: String? = null, isManager: Boolean = false): List<FollowUpReminder> {
        val list = mutableListOf<FollowUpReminder>()
        val db = readableDatabase
        val query = if (isManager || forUserId.isNullOrBlank()) {
            "SELECT * FROM $TABLE_FOLLOW_UPS WHERE $COL_FU_COMPLETED = 0 ORDER BY $COL_FU_TIME ASC"
        } else {
            "SELECT * FROM $TABLE_FOLLOW_UPS WHERE $COL_FU_COMPLETED = 0 AND ($COL_FU_ASSIGNED_USER = ? OR $COL_FU_ASSIGNED_USER IS NULL OR $COL_FU_ASSIGNED_USER = '') ORDER BY $COL_FU_TIME ASC"
        }
        val args = if (isManager || forUserId.isNullOrBlank()) null else arrayOf(forUserId)
        val cursor = db.rawQuery(query, args)
        cursor.use {
            val assignedIdx = it.getColumnIndex(COL_FU_ASSIGNED_USER)
            while (it.moveToNext()) {
                list.add(
                    FollowUpReminder(
                        id = it.getString(it.getColumnIndexOrThrow(COL_FU_ID)),
                        phoneNumber = it.getString(it.getColumnIndexOrThrow(COL_FU_PHONE)),
                        customerName = it.getString(it.getColumnIndexOrThrow(COL_FU_NAME)),
                        note = it.getString(it.getColumnIndexOrThrow(COL_FU_NOTE)) ?: "",
                        scheduledTimeEpochMs = it.getLong(it.getColumnIndexOrThrow(COL_FU_TIME)),
                        isCompleted = it.getInt(it.getColumnIndexOrThrow(COL_FU_COMPLETED)) == 1,
                        assignedUserId = if (assignedIdx >= 0) it.getString(assignedIdx) else null
                    )
                )
            }
        }
        if (list.isEmpty() && isManager) {
            val now = System.currentTimeMillis()
            val sampleItems = listOf(
                FollowUpReminder(
                    id = "REM-DEMO-SAMPLE",
                    phoneNumber = "9886252136",
                    customerName = "Ishan",
                    note = "Demo: Scheduled course demonstration & campus tour",
                    scheduledTimeEpochMs = now + 90 * 60 * 1000L,
                    isCompleted = false,
                    assignedUserId = AppConfig.CURRENT_USER_ID
                ),
                FollowUpReminder(
                    id = "REM-FU-SAMPLE",
                    phoneNumber = "9742338050",
                    customerName = "Gopal",
                    note = "Follow-up: Discussion regarding enrollment fee & schedule",
                    scheduledTimeEpochMs = now + 180 * 60 * 1000L,
                    isCompleted = false,
                    assignedUserId = AppConfig.CURRENT_USER_ID
                )
            )
            for (item in sampleItems) {
                saveFollowUp(item)
            }
            list.addAll(sampleItems)
        }
        return list
    }

    fun markFollowUpComplete(id: String): Boolean {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put(COL_FU_COMPLETED, 1)
        }
        return db.update(TABLE_FOLLOW_UPS, cv, "$COL_FU_ID = ?", arrayOf(id)) > 0
    }

    // --- CALL NOTES ---

    fun addCallNote(phoneNumber: String, note: String): Boolean {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put(COL_NOTE_ID, "NOTE-${System.currentTimeMillis()}")
            put(COL_NOTE_PHONE, phoneNumber)
            put(COL_NOTE_TEXT, note)
            put(COL_NOTE_TIMESTAMP, System.currentTimeMillis())
        }
        return db.insert(TABLE_CALL_NOTES, null, cv) != -1L
    }

    // --- CALL STATS: RING DURATION & TALK TIME ---

    fun recordCallStats(
        phoneNumber: String,
        callType: String,
        ringDurationSeconds: Long,
        talkDurationSeconds: Long,
        timestamp: Long = System.currentTimeMillis()
    ): Boolean {
        return try {
            val db = writableDatabase
            val cv = ContentValues().apply {
                put(COL_CS_PHONE, PhoneNumberUtils.normalize(phoneNumber))
                put(COL_CS_TYPE, callType)
                put(COL_CS_RING_DURATION, ringDurationSeconds)
                put(COL_CS_TALK_DURATION, talkDurationSeconds)
                put(COL_CS_TIMESTAMP, timestamp)
            }
            db.insert(TABLE_CALL_STATS, null, cv) != -1L
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun getCallStatsForPhone(phoneNumber: String): CallStatRecord? {
        val clean = PhoneNumberUtils.extractNationalNumber(phoneNumber)
        if (clean.isBlank()) return null
        val db = readableDatabase
        val query = if (clean.length >= 10) {
            "SELECT * FROM $TABLE_CALL_STATS WHERE $COL_CS_PHONE LIKE ? ORDER BY $COL_CS_TIMESTAMP DESC LIMIT 1"
        } else {
            "SELECT * FROM $TABLE_CALL_STATS WHERE $COL_CS_PHONE = ? ORDER BY $COL_CS_TIMESTAMP DESC LIMIT 1"
        }
        val arg = if (clean.length >= 10) "%$clean" else phoneNumber
        val cursor = db.rawQuery(query, arrayOf(arg))
        cursor.use {
            if (it.moveToFirst()) {
                return CallStatRecord(
                    id = it.getLong(it.getColumnIndexOrThrow(COL_CS_ID)),
                    phoneNumber = it.getString(it.getColumnIndexOrThrow(COL_CS_PHONE)),
                    callType = it.getString(it.getColumnIndexOrThrow(COL_CS_TYPE)),
                    ringDurationSeconds = it.getLong(it.getColumnIndexOrThrow(COL_CS_RING_DURATION)),
                    talkDurationSeconds = it.getLong(it.getColumnIndexOrThrow(COL_CS_TALK_DURATION)),
                    timestamp = it.getLong(it.getColumnIndexOrThrow(COL_CS_TIMESTAMP))
                )
            }
        }
        return null
    }

    fun getAllCallStatsMap(): Map<String, CallStatRecord> {
        val map = mutableMapOf<String, CallStatRecord>()
        try {
            val db = readableDatabase
            val cursor = db.rawQuery("SELECT * FROM $TABLE_CALL_STATS ORDER BY $COL_CS_TIMESTAMP DESC LIMIT 2000", null)
            cursor.use {
                val phoneIdx = it.getColumnIndex(COL_CS_PHONE)
                val idIdx = it.getColumnIndex(COL_CS_ID)
                val typeIdx = it.getColumnIndex(COL_CS_TYPE)
                val ringIdx = it.getColumnIndex(COL_CS_RING_DURATION)
                val talkIdx = it.getColumnIndex(COL_CS_TALK_DURATION)
                val timeIdx = it.getColumnIndex(COL_CS_TIMESTAMP)
                while (it.moveToNext()) {
                    val phone = if (phoneIdx >= 0) it.getString(phoneIdx) ?: "" else ""
                    val national = PhoneNumberUtils.extractNationalNumber(phone)
                    val rawClean = PhoneNumberUtils.normalize(phone)
                    if (national.isNotBlank() && !map.containsKey(national)) {
                        val stat = CallStatRecord(
                            id = if (idIdx >= 0) it.getLong(idIdx) else 0L,
                            phoneNumber = phone,
                            callType = if (typeIdx >= 0) it.getString(typeIdx) ?: "CALL" else "CALL",
                            ringDurationSeconds = if (ringIdx >= 0) it.getLong(ringIdx) else 0L,
                            talkDurationSeconds = if (talkIdx >= 0) it.getLong(talkIdx) else 0L,
                            timestamp = if (timeIdx >= 0) it.getLong(timeIdx) else 0L
                        )
                        map[national] = stat
                        if (rawClean.isNotBlank()) {
                            map[rawClean] = stat
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return map
    }

    fun getLeadCallAnalytics(): CallStatSummary {
        var totalRing = 0L
        var totalTalk = 0L
        var count = 0
        try {
            val db = readableDatabase
            val cursor = db.rawQuery("SELECT $COL_CS_RING_DURATION, $COL_CS_TALK_DURATION FROM $TABLE_CALL_STATS", null)
            cursor.use {
                val ringIdx = it.getColumnIndex(COL_CS_RING_DURATION)
                val talkIdx = it.getColumnIndex(COL_CS_TALK_DURATION)
                while (it.moveToNext()) {
                    val ring = if (ringIdx >= 0) it.getLong(ringIdx) else 0L
                    val talk = if (talkIdx >= 0) it.getLong(talkIdx) else 0L
                    totalRing += ring
                    totalTalk += talk
                    count++
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        val avgRing = if (count > 0) totalRing / count else 0L
        val avgTalk = if (count > 0) totalTalk / count else 0L
        return CallStatSummary(
            totalRingSeconds = totalRing,
            totalTalkSeconds = totalTalk,
            avgRingSeconds = avgRing,
            avgTalkSeconds = avgTalk,
            callsCount = count
        )
    }

    /**
     * Compute comprehensive Calling Reports and Lead Pipeline metrics:
     * - Total calls
     * - Average call duration
     * - Outgoing, Incoming, Missed calls
     * - Connected calls
     * - Follow-ups pending
     * - New enquiries
     * - Demo bookings
     * - Converted count
     * Filterable by period: "TODAY", "WEEKLY", "MONTHLY".
     */
    fun getCallingReportMetrics(period: String = "TODAY"): CallingReportMetrics {
        val threshold = when (period.uppercase()) {
            "TODAY", "DAILY" -> {
                val cal = Calendar.getInstance()
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
            "WEEKLY" -> {
                val cal = Calendar.getInstance()
                cal.add(Calendar.DAY_OF_YEAR, -7)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
            "MONTHLY" -> {
                val cal = Calendar.getInstance()
                cal.add(Calendar.DAY_OF_YEAR, -30)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
            else -> 0L
        }

        var totalCalls = 0
        var outgoingCalls = 0
        var incomingCalls = 0
        var missedCalls = 0
        var connectedCalls = 0
        var totalTalkSeconds = 0L

        // 1. Authoritative source: Query Android system CallLog.Calls if permission granted
        var readFromSystemCallLog = false
        try {
            val hasCallLogPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CALL_LOG
            ) == PackageManager.PERMISSION_GRANTED

            if (hasCallLogPermission) {
                val projection = arrayOf(
                    CallLog.Calls._ID,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.DATE,
                    CallLog.Calls.DURATION
                )
                val selection = "${CallLog.Calls.DATE} >= ?"
                val selectionArgs = arrayOf(threshold.toString())
                val cursor = context.contentResolver.query(
                    CallLog.Calls.CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    null
                )
                cursor?.use {
                    readFromSystemCallLog = true
                    val typeIdx = it.getColumnIndex(CallLog.Calls.TYPE)
                    val durIdx = it.getColumnIndex(CallLog.Calls.DURATION)
                    while (it.moveToNext()) {
                        totalCalls++
                        val rawType = if (typeIdx >= 0) it.getInt(typeIdx) else CallLog.Calls.INCOMING_TYPE
                        val duration = if (durIdx >= 0) it.getLong(durIdx) else 0L

                        when (rawType) {
                            CallLog.Calls.OUTGOING_TYPE -> {
                                outgoingCalls++
                                if (duration > 0) {
                                    connectedCalls++
                                    totalTalkSeconds += duration
                                }
                            }
                            CallLog.Calls.INCOMING_TYPE -> {
                                incomingCalls++
                                if (duration > 0) {
                                    connectedCalls++
                                    totalTalkSeconds += duration
                                }
                            }
                            CallLog.Calls.MISSED_TYPE,
                            CallLog.Calls.REJECTED_TYPE,
                            CallLog.Calls.BLOCKED_TYPE -> {
                                missedCalls++
                            }
                            else -> {
                                if (duration > 0) {
                                    connectedCalls++
                                    totalTalkSeconds += duration
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Fallback to app's internal TABLE_CALL_STATS if CallLog.Calls is unavailable or empty
        if (!readFromSystemCallLog || totalCalls == 0) {
            try {
                val db = readableDatabase
                val cursor = db.rawQuery(
                    "SELECT $COL_CS_TYPE, $COL_CS_TALK_DURATION FROM $TABLE_CALL_STATS WHERE $COL_CS_TIMESTAMP >= ?",
                    arrayOf(threshold.toString())
                )
                cursor.use {
                    val typeIdx = it.getColumnIndex(COL_CS_TYPE)
                    val talkIdx = it.getColumnIndex(COL_CS_TALK_DURATION)
                    while (it.moveToNext()) {
                        totalCalls++
                        val type = if (typeIdx >= 0) it.getString(typeIdx)?.uppercase() ?: "" else ""
                        val talk = if (talkIdx >= 0) it.getLong(talkIdx) else 0L
                        when {
                            type.contains("OUTGOING") -> outgoingCalls++
                            type.contains("INCOMING") -> incomingCalls++
                            type.contains("MISSED") -> missedCalls++
                        }
                        if (talk > 0) {
                            connectedCalls++
                            totalTalkSeconds += talk
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Pending follow-ups within period
        var followUpsPending = 0
        try {
            val db = readableDatabase
            val endOfPeriod = when (period.uppercase()) {
                "TODAY", "DAILY" -> {
                    val cal = Calendar.getInstance()
                    cal.set(Calendar.HOUR_OF_DAY, 23)
                    cal.set(Calendar.MINUTE, 59)
                    cal.set(Calendar.SECOND, 59)
                    cal.set(Calendar.MILLISECOND, 999)
                    cal.timeInMillis
                }
                "WEEKLY" -> {
                    val cal = Calendar.getInstance()
                    cal.add(Calendar.DAY_OF_YEAR, 7)
                    cal.set(Calendar.HOUR_OF_DAY, 23)
                    cal.set(Calendar.MINUTE, 59)
                    cal.set(Calendar.SECOND, 59)
                    cal.set(Calendar.MILLISECOND, 999)
                    cal.timeInMillis
                }
                "MONTHLY" -> {
                    val cal = Calendar.getInstance()
                    cal.add(Calendar.DAY_OF_YEAR, 30)
                    cal.set(Calendar.HOUR_OF_DAY, 23)
                    cal.set(Calendar.MINUTE, 59)
                    cal.set(Calendar.SECOND, 59)
                    cal.set(Calendar.MILLISECOND, 999)
                    cal.timeInMillis
                }
                else -> Long.MAX_VALUE
            }
            val fuCursor = db.rawQuery(
                "SELECT COUNT(*) FROM $TABLE_FOLLOW_UPS WHERE $COL_FU_COMPLETED = 0 AND $COL_FU_TIME <= ?",
                arrayOf(endOfPeriod.toString())
            )
            fuCursor.use {
                if (it.moveToFirst()) followUpsPending = it.getInt(0)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // New enquiries, Demo bookings, Converted count within period
        var newEnquiries = 0
        var demoBookings = 0
        var convertedCount = 0
        try {
            val db = readableDatabase
            val enqCursor = db.rawQuery(
                "SELECT $COL_ENQ_STATUS, $COL_ENQ_DEMO_STATUS, $COL_ENQ_CREATED_AT FROM $TABLE_ENQUIRIES",
                null
            )
            enqCursor.use {
                val statusIdx = it.getColumnIndex(COL_ENQ_STATUS)
                val demoIdx = it.getColumnIndex(COL_ENQ_DEMO_STATUS)
                val createdIdx = it.getColumnIndex(COL_ENQ_CREATED_AT)
                while (it.moveToNext()) {
                    val st = if (statusIdx >= 0) it.getString(statusIdx)?.lowercase() ?: "" else ""
                    val demo = if (demoIdx >= 0) it.getString(demoIdx)?.lowercase() ?: "" else ""
                    val createdStr = if (createdIdx >= 0) it.getString(createdIdx) else null
                    val createdTime = parseEnquiryDate(createdStr)

                    // If createdAt is available, filter by period threshold; if null, include in counts
                    val matchesPeriod = createdTime == null || createdTime >= threshold

                    if (matchesPeriod) {
                        if (st.contains("new")) newEnquiries++
                        if (st.contains("converted") || st.contains("enrolled")) convertedCount++
                        if (demo.contains("book")) demoBookings++
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val avgDuration = if (connectedCalls > 0) totalTalkSeconds / connectedCalls else 0L

        return CallingReportMetrics(
            totalCalls = totalCalls,
            avgDurationSeconds = avgDuration,
            outgoingCalls = outgoingCalls,
            incomingCalls = incomingCalls,
            missedCalls = missedCalls,
            connectedCalls = connectedCalls,
            followUpsPending = followUpsPending,
            newEnquiries = newEnquiries,
            demoBookings = demoBookings,
            convertedCount = convertedCount
        )
    }

    private fun parseEnquiryDate(str: String?): Long? {
        if (str.isNullOrBlank()) return null
        str.toLongOrNull()?.let { return it }
        val formats = arrayOf(
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd"
        )
        for (format in formats) {
            try {
                val sdf = SimpleDateFormat(format, Locale.getDefault())
                val d = sdf.parse(str)
                if (d != null) return d.time
            } catch (_: Exception) {}
        }
        return null
    }

    private fun cursorToEnquiry(cursor: Cursor): Enquiry {
        val assignedIdx = cursor.getColumnIndex(COL_ENQ_ASSIGNED_TO)
        val courseIdx = cursor.getColumnIndex(COL_ENQ_COURSE)
        val ageCatIdx = cursor.getColumnIndex(COL_ENQ_AGE_CATEGORY)
        val leadSourceIdx = cursor.getColumnIndex(COL_ENQ_LEAD_SOURCE)
        val demoStatusIdx = cursor.getColumnIndex(COL_ENQ_DEMO_STATUS)
        val admStatusIdx = cursor.getColumnIndex(COL_ENQ_ADMISSION_STATUS)
        val fuDateIdx = cursor.getColumnIndex(COL_ENQ_FOLLOW_UP_DATE)
        val remarksIdx = cursor.getColumnIndex(COL_ENQ_REMARKS)
        val queueIdx = cursor.getColumnIndex(COL_ENQ_IN_CALL_QUEUE)
        val favIdx = cursor.getColumnIndex(COL_ENQ_IS_FAVORITE)
        val centerIdIdx = cursor.getColumnIndex(COL_ENQ_CENTER_ID)
        val demoSlotIdx = cursor.getColumnIndex(COL_ENQ_DEMO_SLOT)
        val enqNumIdx = cursor.getColumnIndex(COL_ENQ_NUMBER)

        return Enquiry(
            id = cursor.getString(cursor.getColumnIndexOrThrow(COL_ENQ_ID)),
            name = cursor.getString(cursor.getColumnIndexOrThrow(COL_ENQ_NAME)),
            phoneNumber = cursor.getString(cursor.getColumnIndexOrThrow(COL_ENQ_PHONE)),
            company = cursor.getString(cursor.getColumnIndexOrThrow(COL_ENQ_COMPANY)),
            enquiryType = cursor.getString(cursor.getColumnIndexOrThrow(COL_ENQ_TYPE)),
            status = cursor.getString(cursor.getColumnIndexOrThrow(COL_ENQ_STATUS)) ?: "New Lead",
            notes = cursor.getString(cursor.getColumnIndexOrThrow(COL_ENQ_NOTES)),
            email = cursor.getString(cursor.getColumnIndexOrThrow(COL_ENQ_EMAIL)),
            assignedTo = if (assignedIdx >= 0) cursor.getString(assignedIdx) else null,
            budget = cursor.getString(cursor.getColumnIndexOrThrow(COL_ENQ_BUDGET)),
            createdAt = cursor.getString(cursor.getColumnIndexOrThrow(COL_ENQ_CREATED_AT)),
            course = if (courseIdx >= 0) cursor.getString(courseIdx) else null,
            ageCategory = if (ageCatIdx >= 0) cursor.getString(ageCatIdx) else null,
            leadSource = if (leadSourceIdx >= 0) cursor.getString(leadSourceIdx) ?: "Website" else "Website",
            demoStatus = if (demoStatusIdx >= 0) cursor.getString(demoStatusIdx) else null,
            admissionStatus = if (admStatusIdx >= 0) cursor.getString(admStatusIdx) else null,
            followUpDate = if (fuDateIdx >= 0 && !cursor.isNull(fuDateIdx)) cursor.getLong(fuDateIdx) else null,
            remarks = if (remarksIdx >= 0) cursor.getString(remarksIdx) else null,
            inCallQueue = if (queueIdx >= 0) cursor.getInt(queueIdx) == 1 else false,
            isFavorite = if (favIdx >= 0) cursor.getInt(favIdx) == 1 else false,
            centerId = if (centerIdIdx >= 0) cursor.getString(centerIdIdx) else null,
            demoSlot = if (demoSlotIdx >= 0) cursor.getString(demoSlotIdx) else null,
            enquiryNumber = if (enqNumIdx >= 0 && !cursor.isNull(enqNumIdx)) cursor.getInt(enqNumIdx) else null
        )
    }
}
