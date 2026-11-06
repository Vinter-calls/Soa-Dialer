package com.dialer.truedialer.data.model

data class CallRecord(
    val id: String,
    val phoneNumber: String,
    val cachedName: String?,
    val type: CallType,
    val timestamp: Long,
    val durationSeconds: Long,
    val isContactSaved: Boolean = false,
    val matchedEnquiry: Enquiry? = null
)

enum class CallType {
    INCOMING,
    OUTGOING,
    MISSED,
    REJECTED,
    BLOCKED,
    UNKNOWN
}
