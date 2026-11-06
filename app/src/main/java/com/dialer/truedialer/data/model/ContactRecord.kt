package com.dialer.truedialer.data.model

data class ContactRecord(
    val id: String,
    val name: String,
    val phoneNumber: String,
    val photoUri: String? = null,
    val lookupKey: String? = null,
    val isStarred: Boolean = false
)

