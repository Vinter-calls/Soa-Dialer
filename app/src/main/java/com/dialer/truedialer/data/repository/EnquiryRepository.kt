package com.dialer.truedialer.data.repository

import com.dialer.truedialer.data.model.Enquiry

interface EnquiryRepository {
    /**
     * Searches backend enquiry list for a phone number match.
     * Used for real-time Truecaller-style Caller ID lookup.
     */
    suspend fun searchEnquiryByPhone(phoneNumber: String): Enquiry?

    /**
     * Fetches all enquiries from the backend.
     */
    suspend fun getAllEnquiries(): List<Enquiry>

    /**
     * Filters enquiries by query string (name, phone, company, status, notes).
     */
    suspend fun searchEnquiries(query: String): List<Enquiry>

    /**
     * Adds or updates an enquiry in the backend database.
     */
    suspend fun saveEnquiry(enquiry: Enquiry): Boolean
}
