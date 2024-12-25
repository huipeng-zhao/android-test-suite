package com.example.ancs

data class NotificationEvent(
    val eventId: Int,
    val eventFlags: Int,
    val categoryId: Int,
    val categoryCount: Int,
    val notificationUID: ByteArray
)
