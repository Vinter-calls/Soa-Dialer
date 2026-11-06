package com.dialer.truedialer.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Enquiry(
    val id: String,
    val name: String,
    val phoneNumber: String,
    val company: String? = null,
    val enquiryType: String? = null,
    val status: String = "New", // "New", "Contacted", "Interested", "Converted", "Lost", "Follow-up", "Enrolled", "Spam", "VIP"
    val notes: String? = null,
    val email: String? = null,
    val assignedTo: String? = null,
    val budget: String? = null,
    val createdAt: String? = null,

    // Extended CRM Fields
    val course: String? = null, // e.g. "Python Full Stack", "Data Analytics", "Java"
    val ageCategory: String? = null, // "School", "College / Graduate", "Working Professional", "Job Seeker"
    val leadSource: String? = "Website", // "Website", "Google", "Instagram", "Walk-in", "Referral"
    val demoStatus: String? = null, // "Booked", "Attended", "Missed"
    val admissionStatus: String? = null, // "Pending", "Enrolled", "Not Interested"
    val followUpDate: Long? = null, // Timestamp for next follow-up
    val remarks: String? = null,
    val inCallQueue: Boolean = false,
    val isFavorite: Boolean = false,
    val centerId: String? = null, // Center ID where this enquiry belongs
    val demoSlot: String? = null, // Scheduled demo slot info or time
    val enquiryNumber: Int? = null // MongoDB ID number (e.g. 20, 79, 87)
) {
    /**
     * Returns formatted ID number from MongoDB (e.g. "#79" or "#20")
     */
    val displayId: String
        get() = when {
            enquiryNumber != null -> "#$enquiryNumber"
            id.startsWith("ENQ-", ignoreCase = true) -> "#${id.removePrefix("ENQ-").removePrefix("enq-")}"
            id.length == 24 -> "#${id.takeLast(4).uppercase()}"
            id.isNotBlank() -> "#$id"
            else -> ""
        }

    /**
     * Formats Name and ID Number from MongoDB (e.g. "Muktha (#79)")
     */
    val displayNameWithId: String
        get() = if (displayId.isNotBlank()) "$name ($displayId)" else name
}
