package com.example.ancs

data class NotificationData(
    var commandId: Int,
    var notificationUID: ByteArray,
    var attributeID: AttributeID,
    var displayName: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as NotificationData

        if (commandId != other.commandId) return false
        if (!notificationUID.contentEquals(other.notificationUID)) return false
        if (attributeID != other.attributeID) return false

        return true
    }

    override fun hashCode(): Int {
        var result = commandId
        result = 31 * result + notificationUID.contentHashCode()
        result = 31 * result + attributeID.hashCode()
        return result
    }
}
