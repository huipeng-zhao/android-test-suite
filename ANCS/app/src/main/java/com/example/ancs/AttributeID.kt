package com.example.ancs

data class AttributeID(
    var notificationAttributeIDAppIdentifier: String,
    var appIdentifierLen: Int,
    var notificationAttributeIDTitle: String,
    var notificationAttributeIDSubtitle: String,
    var notificationAttributeIDMessage: String,
    var notificationAttributeIDMessageSize: String,
    var notificationAttributeIDDate: String,
    var notificationAttributeIDPositiveActionLabel: String,
    val notificationAttributeIDNegativeActionLabel: String
)
