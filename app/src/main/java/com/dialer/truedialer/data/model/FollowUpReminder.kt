package com.dialer.truedialer.data.model

import kotlinx.serialization.Serializable

@Serializable
data class FollowUpReminder(
    val id: String,
    val phoneNumber: String,
    val customerName: String,
    val note: String,
    val scheduledTimeEpochMs: Long,
    val isCompleted: Boolean = false,
    val assignedUserId: String? = null
)
