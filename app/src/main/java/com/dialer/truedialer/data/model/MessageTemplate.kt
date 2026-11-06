package com.dialer.truedialer.data.model

import kotlinx.serialization.Serializable

@Serializable
data class MessageTemplate(
    val id: String,
    val title: String,
    val body: String,
    val isDefault: Boolean = false
)
