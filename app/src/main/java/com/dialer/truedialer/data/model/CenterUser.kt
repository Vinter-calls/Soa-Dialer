package com.dialer.truedialer.data.model

data class CenterUser(
    val id: String,
    val name: String,
    val role: UserRole,
    val username: String = "",
    val email: String = "",
    val passwordHash: String = "",
    val pin: String = "1234",
    val phone: String = "",
    val centerId: String = "CTR-01",
    val isActive: Boolean = true
) {
    val isManager: Boolean
        get() = role == UserRole.MANAGER || role == UserRole.ACADEMIC_MANAGER ||
                role == UserRole.OPERATIONS_MANAGER || role == UserRole.MEDIA_MANAGER ||
                role == UserRole.PURCHASE_MANAGER || role == UserRole.ADMIN

    val canLogin: Boolean
        get() = isManager
}

enum class UserRole {
    STAFF,
    FACULTY,
    MANAGER,
    ACADEMIC_MANAGER,
    OPERATIONS_MANAGER,
    MEDIA_MANAGER,
    PURCHASE_MANAGER,
    ADMIN;

    val displayName: String
        get() = when (this) {
            STAFF -> "Staff / Telecaller"
            FACULTY -> "Faculty / Staff"
            MANAGER -> "Center Manager"
            ACADEMIC_MANAGER -> "Academic Manager"
            OPERATIONS_MANAGER -> "Operations Manager"
            MEDIA_MANAGER -> "Media Manager"
            PURCHASE_MANAGER -> "Purchase Manager"
            ADMIN -> "Administrator"
        }

    companion object {
        fun fromString(value: String?): UserRole {
            if (value.isNullOrBlank()) return STAFF
            val clean = value.trim().lowercase()
            return when {
                clean == "manager" -> MANAGER
                clean == "academic_manager" || clean.contains("academic") -> ACADEMIC_MANAGER
                clean == "operations_manager" || clean.contains("operations") || clean.contains("ops") -> OPERATIONS_MANAGER
                clean == "media_manager" || clean.contains("media") -> MEDIA_MANAGER
                clean == "purchase_manager" || clean.contains("purchase") -> PURCHASE_MANAGER
                clean == "admin" || clean.contains("admin") -> ADMIN
                clean == "faculty" -> FACULTY
                else -> STAFF
            }
        }
    }
}
