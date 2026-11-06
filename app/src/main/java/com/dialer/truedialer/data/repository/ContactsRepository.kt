package com.dialer.truedialer.data.repository

import android.content.ContentProviderOperation
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import com.dialer.truedialer.data.model.ContactRecord
import com.dialer.truedialer.util.PhoneNumberUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ContactsRepository(private val context: Context) {

    /**
     * Checks if a phone number already exists in device contacts.
     */
    suspend fun isContactSaved(phoneNumber: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext getContactName(phoneNumber) != null
    }

    /**
     * Finds contact name for a number from device contacts.
     * Uses PhoneLookup first, then falls back to CommonDataKinds.Phone with 10-digit matching.
     */
    suspend fun getContactName(phoneNumber: String): String? = withContext(Dispatchers.IO) {
        if (phoneNumber.isBlank()) return@withContext null

        // 1. Try standard PhoneLookup with raw input
        try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber.trim())
            )
            val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
            val cursor: Cursor? = context.contentResolver.query(uri, projection, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val name = it.getString(it.getColumnIndexOrThrow(ContactsContract.PhoneLookup.DISPLAY_NAME))
                    if (!name.isNullOrBlank()) return@withContext name
                }
            }
        } catch (_: Exception) {}

        // 2. Try PhoneLookup with normalized number
        val normalized = PhoneNumberUtils.normalize(phoneNumber)
        if (normalized.isNotBlank() && normalized != phoneNumber.trim()) {
            try {
                val uri = Uri.withAppendedPath(
                    ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                    Uri.encode(normalized)
                )
                val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
                val cursor: Cursor? = context.contentResolver.query(uri, projection, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val name = it.getString(it.getColumnIndexOrThrow(ContactsContract.PhoneLookup.DISPLAY_NAME))
                        if (!name.isNullOrBlank()) return@withContext name
                    }
                }
            } catch (_: Exception) {}
        }

        // 3. Fallback: Query CommonDataKinds.Phone by 10-digit national number
        val digits = phoneNumber.replace("[^0-9]".toRegex(), "")
        if (digits.length >= 10) {
            val last10 = digits.takeLast(10)
            try {
                val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
                val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val selection = "${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?"
                val selectionArgs = arrayOf("%$last10")
                val cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val name = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME))
                        if (!name.isNullOrBlank()) return@withContext name
                    }
                }
            } catch (_: Exception) {}
        }

        return@withContext null
    }

    /**
     * Loads a fast in-memory map of (nationalNumber -> displayName) for batch lookups.
     */
    suspend fun getContactsMap(): Map<String, String> = withContext(Dispatchers.IO) {
        val map = mutableMapOf<String, String>()
        try {
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            val cursor = context.contentResolver.query(uri, projection, null, null, null)
            cursor?.use {
                val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (it.moveToNext()) {
                    val name = if (nameIdx >= 0) it.getString(nameIdx) else null
                    val num = if (numIdx >= 0) it.getString(numIdx) else null
                    if (!name.isNullOrBlank() && !num.isNullOrBlank()) {
                        val digits = num.replace("[^0-9]".toRegex(), "")
                        if (digits.isNotBlank()) {
                            map[digits] = name
                            if (digits.length >= 10) {
                                map[digits.takeLast(10)] = name
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ContactsRepo", "Error generating contacts map: ${e.message}", e)
        }
        return@withContext map
    }

    /**
     * Loads list of device contacts.
     */
    suspend fun getContacts(filter: String = ""): List<ContactRecord> = withContext(Dispatchers.IO) {
        val list = mutableListOf<ContactRecord>()
        try {
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,
                ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
                ContactsContract.CommonDataKinds.Phone.STARRED
            )
            val selection = if (filter.isNotBlank()) {
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ? OR ${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?"
            } else null

            val selectionArgs = if (filter.isNotBlank()) {
                val wild = "%$filter%"
                arrayOf(wild, wild)
            } else null

            val sortOrder = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"

            val cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
            cursor?.use {
                val idIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val photoIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI)
                val lookupIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY)
                val starredIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.STARRED)

                while (it.moveToNext()) {
                    val id = if (idIdx >= 0) it.getString(idIdx) ?: "" else ""
                    val name = if (nameIdx >= 0) it.getString(nameIdx) ?: "Unknown" else "Unknown"
                    val number = if (numIdx >= 0) it.getString(numIdx) ?: "" else ""
                    val photo = if (photoIdx >= 0) it.getString(photoIdx) else null
                    val lookup = if (lookupIdx >= 0) it.getString(lookupIdx) else null
                    val isStarred = if (starredIdx >= 0) it.getInt(starredIdx) == 1 else false

                    list.add(ContactRecord(id = id, name = name, phoneNumber = number, photoUri = photo, lookupKey = lookup, isStarred = isStarred))
                }
            }
        } catch (e: Exception) {
            Log.e("ContactsRepo", "Error querying contacts: ${e.message}", e)
        }
        return@withContext list
    }

    /**
     * Creates an Intent to launch Android's native Add Contact screen,
     * pre-populated with Name (from Enquiry), Phone Number, Company, and Email.
     */
    fun createSaveContactIntent(
        name: String,
        phoneNumber: String,
        company: String? = null,
        email: String? = null
    ): Intent {
        return Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI).apply {
            putExtra(ContactsContract.Intents.Insert.NAME, name)
            putExtra(ContactsContract.Intents.Insert.PHONE, phoneNumber)
            if (!company.isNullOrBlank()) {
                putExtra(ContactsContract.Intents.Insert.COMPANY, company)
            }
            if (!email.isNullOrBlank()) {
                putExtra(ContactsContract.Intents.Insert.EMAIL, email)
            }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    /**
     * Direct programmatic contact insertion into Android device contacts.
     */
    suspend fun saveContactDirectly(
        name: String,
        phoneNumber: String,
        company: String? = null,
        email: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val ops = ArrayList<ContentProviderOperation>()

            val rawContactInsertIndex = ops.size
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                    .build()
            )

            // Name
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
                    .build()
            )

            // Phone
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phoneNumber)
                    .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                    .build()
            )

            // Company (Organization)
            if (!company.isNullOrBlank()) {
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Organization.COMPANY, company)
                        .build()
                )
            }

            // Email
            if (!email.isNullOrBlank()) {
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, email)
                        .build()
                )
            }

            context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            return@withContext true
        } catch (e: Exception) {
            Log.e("ContactsRepo", "Error directly adding contact: ${e.message}", e)
            return@withContext false
        }
    }

    /**
     * Delete contact directly from phonebook by Contact ID.
     */
    suspend fun deleteContact(contactId: String): Boolean = withContext(Dispatchers.IO) {
        if (contactId.isBlank()) return@withContext false
        try {
            val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, contactId)
            val rows = context.contentResolver.delete(uri, null, null)
            return@withContext rows > 0
        } catch (e: Exception) {
            Log.e("ContactsRepo", "Error deleting contact $contactId: ${e.message}", e)
            return@withContext false
        }
    }

    /**
     * Directly update contact display name in phonebook by Contact ID.
     */
    suspend fun updateContactName(contactId: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        if (contactId.isBlank() || newName.isBlank()) return@withContext false
        try {
            val where = "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?"
            val args = arrayOf(contactId, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
            val cv = android.content.ContentValues().apply {
                put(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, newName)
            }
            val rows = context.contentResolver.update(ContactsContract.Data.CONTENT_URI, cv, where, args)
            return@withContext rows > 0
        } catch (e: Exception) {
            Log.e("ContactsRepo", "Error updating contact name: ${e.message}", e)
            return@withContext false
        }
    }

    /**
     * Star / Unstar (Favorite) contact in device phonebook.
     */
    suspend fun toggleContactStarred(contactId: String, isStarred: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (contactId.isBlank()) return@withContext false
        try {
            val cv = android.content.ContentValues().apply {
                put(ContactsContract.Contacts.STARRED, if (isStarred) 1 else 0)
            }
            val rows = context.contentResolver.update(
                ContactsContract.Contacts.CONTENT_URI,
                cv,
                "${ContactsContract.Contacts._ID} = ?",
                arrayOf(contactId)
            )
            return@withContext rows > 0
        } catch (e: Exception) {
            Log.e("ContactsRepo", "Error toggling starred for $contactId: ${e.message}", e)
            return@withContext false
        }
    }
}
