package com.epfl.esl.chaze.data.model

data class NotificationData(
    val id: String = "",
    val title: String = "",
    val message: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isUnread: Boolean = true,
    val type: String = "discount" // discount, general, etc.
)
