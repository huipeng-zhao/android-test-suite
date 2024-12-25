package com.example.ancs

data class NotificationData(
    var commandId: Int,
    var notificationUID: ByteArray,
    var attributeID: AttributeID,
)
