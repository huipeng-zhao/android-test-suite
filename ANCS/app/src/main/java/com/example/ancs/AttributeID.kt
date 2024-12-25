package com.example.ancs

data class AttributeID(
    var notificationAttributeIDAppIdentifier: Int,
    var notificationAttributeIDTitle: String,
    var notificationAttributeIDSubtitle: String,
    var notificationAttributeIDMessage: String,
    var notificationAttributeIDMessageSize: Int,
    var notificationAttributeIDDate: String,
    var notificationAttributeIDPositiveActionLabel: String,
    val notificationAttributeIDNegativeActionLabel: String
)
