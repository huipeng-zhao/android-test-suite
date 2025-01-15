package com.example.ancs

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

class AncsClient(private val context: Context) : BluetoothGattCallback() {

    private val myConnectedDevice = MutableStateFlow<BluetoothDevice?>(null)
    private val myNotification = MutableStateFlow<List<NotificationData>>(emptyList())
    val connectedDevice: StateFlow<BluetoothDevice?> = myConnectedDevice
    val notification: StateFlow<List<NotificationData>> = myNotification

    private var bluetoothGatt: BluetoothGatt? = null
    private var isRequestInProgress = false
    private lateinit var mNotificationData: NotificationData
    private val notificationEvents = mutableListOf<NotificationEvent>()
    private val notifications = mutableListOf<NotificationData>()
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        private const val TAG = "AncsClient"
        private val ANC_SERVICE_UUID = UUID.fromString("7905F431-B5CE-4E99-A40F-4B1E122D00D0")
        private val ANC_NOTIFICATION_SOURCE_CHARACTERISTIC_UUID =
            UUID.fromString("9FBF120D-6301-42D9-8C58-25E699A21DBD")
        private val ANC_CONTROL_POINT_CHARACTERISTIC_UUID =
            UUID.fromString("69D1D8F3-45E1-49A8-9821-9BBDFDAAD9D9")
        private val CLIENT_CONFIG_DESCRIPTOR_UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private val ANC_DATA_SOURCE_UUID = UUID.fromString("22eac6e9-24d6-4bb5-be44-b36ace7c7bfb")
        private val IAP2_PHONE_UUID = UUID.fromString("00000000-deca-fade-deca-deafdecacafe")
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun getRemoteDevices(): List<BluetoothDevice> {
        val bluetoothManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothDevices = bluetoothManager.adapter.bondedDevices.filter {
            it.uuids?.contains(
                ParcelUuid(IAP2_PHONE_UUID)
            ) == true
        }
        Log.d(TAG, "getRemoteDevices: $bluetoothDevices")
        return bluetoothDevices
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connect(device: BluetoothDevice) {
        bluetoothGatt = device.connectGatt(context, false, this)
        Log.d(TAG, "connect: ${myConnectedDevice.value?.name}")
        myConnectedDevice.value = device
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnect() {
        bluetoothGatt?.close()
        Log.d(TAG, "disconnect")
        myConnectedDevice.value = null
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun performAction(uid: ByteArray, action: Int) {
        val data = byteArrayOf(0x02.toByte()) + uid + action.toByte()
        Log.d(TAG, "performAction: uid = ${Util.bytesToHexString(uid)}, action = $action")
        sendRequest(data)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun sendRequest(data: ByteArray) {
        Log.d(TAG, "sendRequest: ${Util.bytesToHexString(data)}")
        bluetoothGatt?.getService(ANC_SERVICE_UUID)
            ?.getCharacteristic(ANC_CONTROL_POINT_CHARACTERISTIC_UUID)
            ?.apply {
                value = data
                bluetoothGatt?.writeCharacteristic(this)
            }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
        super.onConnectionStateChange(gatt, status, newState)
        if (newState == BluetoothProfile.STATE_CONNECTED) {
            Log.d(TAG, "Connected success: ${gatt?.device?.name}")
            gatt?.discoverServices()
            myConnectedDevice.value = gatt?.device
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            Log.e(TAG, "Disconnected: ${gatt?.device?.name}")
            myConnectedDevice.value = null
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
        super.onServicesDiscovered(gatt, status)
        if (status == BluetoothGatt.GATT_SUCCESS) {
            Log.d(TAG, "Discovered success: ${gatt?.device?.name}")
            gatt?.getService(ANC_SERVICE_UUID)?.let { setupCharacteristics(it) }
                ?: Log.e(TAG, "ANCS service not found")
        } else {
            Log.e(TAG, "Discovered failed: ${gatt?.device?.name}")
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun setupCharacteristics(service: BluetoothGattService) {
        Log.d(TAG, "setupCharacteristics")
        subscribeToNotifications(service.getCharacteristic(ANC_DATA_SOURCE_UUID))
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ) {
        super.onCharacteristicChanged(gatt, characteristic, value)
        Log.d(TAG, "onCharacteristicChanged: ${characteristic.uuid}")
        when (characteristic.uuid) {
            ANC_NOTIFICATION_SOURCE_CHARACTERISTIC_UUID -> parseNotificationEvent(value)
            ANC_DATA_SOURCE_UUID -> handleDataSource(value)
        }
    }

    override fun onCharacteristicWrite(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?,
        status: Int
    ) {
        super.onCharacteristicWrite(gatt, characteristic, status)
        if (status == BluetoothGatt.GATT_SUCCESS) {
            Log.d(TAG, "onCharacteristicWrite success")
        } else {
            Log.e(TAG, "onCharacteristicWrite, status: $status")
        }
        isRequestInProgress = false

    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onDescriptorWrite(
        gatt: BluetoothGatt?,
        descriptor: BluetoothGattDescriptor?,
        status: Int
    ) {
        super.onDescriptorWrite(gatt, descriptor, status)
        if (status == BluetoothGatt.GATT_SUCCESS) {
            if (descriptor?.characteristic?.uuid == ANC_DATA_SOURCE_UUID) {
                subscribeToNotifications(
                    gatt?.getService(ANC_SERVICE_UUID)
                        ?.getCharacteristic(ANC_NOTIFICATION_SOURCE_CHARACTERISTIC_UUID)
                )
                Log.d(TAG, "Data Source Subscribe Success")
            } else if (descriptor?.characteristic?.uuid == ANC_NOTIFICATION_SOURCE_CHARACTERISTIC_UUID) {
                Log.d(TAG, "Notification Source Subscribe Success")
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun subscribeToNotifications(characteristic: BluetoothGattCharacteristic?) {
        bluetoothGatt?.setCharacteristicNotification(characteristic, true)
        Log.d(TAG, "subscribeToNotifications")
        characteristic?.getDescriptor(CLIENT_CONFIG_DESCRIPTOR_UUID)?.apply {
            value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            bluetoothGatt?.writeDescriptor(this)
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun parseNotificationEvent(data: ByteArray) {
        val notificationEvent = NotificationEvent(
            data[0].toInt(),
            data[1].toInt(),
            data[2].toInt(),
            data[3].toInt(),
            data.copyOfRange(4, 8)
        )
        Log.d(
            TAG,
            "Event ID: ${data[0]}" +
                    " Event Flags: ${data[1]}" +
                    " CategoryId: ${data[2]}" +
                    " Category Count: ${data[3]}" +
                    " NotificationUID: ${Util.bytesToHexString(data.copyOfRange(4, 8))}"
        )

        when (notificationEvent.eventId) {
            0, 1 -> {
                notificationEvents.add(notificationEvent)
                if (!isRequestInProgress) {
//                    requestNotificationAttributes(notificationEvent.notificationUID)
                    delayRequestNotificationAttributes(notificationEvent.notificationUID)
                }
            }

            2 -> deleteData(notificationEvent.notificationUID)
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun handleDataSource(data: ByteArray) {
        when (data[0].toInt()) {
            0 -> parseNotificationData(data)
            1 -> parseAppAttributes(data)
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun requestNotificationAttributes(uid: ByteArray) {
        if (isRequestInProgress) {
            Log.e(TAG, "Request already in progress, skipping...")
            return
        }
        Log.d(TAG, "requestNotificationAttributes: uid = ${Util.bytesToHexString(uid)}")
        isRequestInProgress = true

        val getNotificationAttribute = byteArrayOf(
            0x00.toByte(), //commandId
            *uid
        ) + byteArrayOf(
            0x00.toByte(),
            0x01.toByte(), //AttributeID --NotificationAttributeIDTitle
            0xFF.toByte(),
            0xFF.toByte(),
            0x02.toByte(), //AttributeID --NotificationAttributeIDSubtitle
            0xFF.toByte(),
            0xFF.toByte(),
            0x03.toByte(), //AttributeID --NotificationAttributeIDMessage
            0xFF.toByte(),
            0xFF.toByte(),
            0x04.toByte(), //AttributeID --NotificationAttributeIDMessageSize
            0x05.toByte(), //AttributeID --NotificationAttributeIDDate
            0x06.toByte(), //AttributeID --NotificationAttributeIDPositiveActionLabel
            0x07.toByte(), //AttributeID --NotificationAttributeIDNegativeActionLabel
        )
        sendRequest(getNotificationAttribute)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun delayRequestNotificationAttributes(uid: ByteArray) {
        handler.postDelayed({
            requestNotificationAttributes(uid)
        }, 500)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun parseNotificationData(data: ByteArray) {
        Log.d(TAG, "parseNotificationData: data =  ${Util.bytesToHexString(data)}")
        val notificationUID = data.sliceArray(1..4).reversed()
            .fold(0) { acc, byte -> (acc shl 8) or (byte.toInt() and 0xFF) }
        Log.d(TAG, "UID: $notificationUID")

        val commandId = data[0].toInt()
        var tagIndex = 5
        var appIdentifier = ""
        var appIdentifierLen = 0
        var title = ""
        var subtitle = ""
        var msg = ""
        var messageSize = ""
        var timeStamp = ""
        var positiveActionLabel = ""
        var negativeActionLabel = ""

        fun parseField(): String {
            var len =
                (data[tagIndex + 1].toInt() and 0xFF) + (data[tagIndex + 2].toInt() and 0xFF) * 256
            if (tagIndex + 3 + len > data.size) {
                //Attempted to read beyond the end of the data array
                len = data.size - tagIndex - 3
            }
            val fieldValue = String(data, tagIndex + 3, len)
            tagIndex += 3 + len
            return fieldValue
        }

        while (tagIndex < data.size) {
            when (data[tagIndex]) {
                0x00.toByte() -> { // AppIdentifier
                    appIdentifierLen =
                        (data[tagIndex + 1].toInt() and 0xFF) + (data[tagIndex + 2].toInt() and 0xFF) * 256
                    appIdentifier = parseField(); Log.d(TAG, "AppIdentifier: $appIdentifier")
                }

                0x01.toByte() -> { //title
                    title = parseField(); Log.d(TAG, "title = $title")
                }

                0x02.toByte() -> { //subtitle
                    subtitle = parseField(); Log.d(TAG, "subtitle = $subtitle")
                }

                0x03.toByte() -> { //message
                    msg = parseField(); Log.d(TAG, "message = $msg")
                }

                0x04.toByte() -> { //messageSize
                    messageSize = parseField(); Log.d(TAG, "messageSize = $messageSize")
                }

                0x05.toByte() -> { //timeStamp
                    timeStamp = parseField(); Log.d(TAG, "timeStamp = $timeStamp")
                }

                0x06.toByte() -> { //positiveActionLabel
                    positiveActionLabel = parseField(); Log.d(
                        TAG, "positiveActionLabel = $positiveActionLabel"
                    )
                }

                0x07.toByte() -> { //negativeActionLabel
                    negativeActionLabel = parseField(); Log.d(
                        TAG, "negativeActionLabel = $negativeActionLabel"
                    )
                }

                else -> tagIndex += 1
            }
        }

        val attributeID = AttributeID(
            appIdentifier,
            appIdentifierLen,
            title,
            subtitle,
            msg,
            messageSize,
            timeStamp,
            positiveActionLabel,
            negativeActionLabel
        )
        mNotificationData = NotificationData(
            commandId,
            byteArrayOf(data[1], data[2], data[3], data[4]),
            attributeID,
            ""
        )

        val existingNotification =
            notifications.firstOrNull { it.attributeID.notificationAttributeIDAppIdentifier == appIdentifier }
        if (existingNotification?.displayName.isNullOrEmpty()) {
            requestAppAttributes(appIdentifier)
        } else {
            Log.d(TAG, "DisplayName is already set, skipping requestAppAttributes")
            if (existingNotification != null) {
                mNotificationData.displayName = existingNotification.displayName
                handleNotificationData()
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun requestAppAttributes(appIdentifier: String) {
        Log.d(TAG, "requestAppAttributes: appIdentifier = $appIdentifier")
        val commandId = byteArrayOf(0x01.toByte())
        val appIdentifierArray = appIdentifier.toByteArray()
        val appAttributeIDArray = byteArrayOf(0x00.toByte())
        val combinedByteArray =
            commandId + appIdentifierArray + byteArrayOf(0x00.toByte()) + appAttributeIDArray
        Log.d(
            TAG,
            "requestAppAttributes: combinedByteArray = " + Util.bytesToHexString(combinedByteArray)
        )
        sendRequest(combinedByteArray)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun parseAppAttributes(data: ByteArray) {
        Log.d(TAG, "parseAppAttributes: data = ${Util.bytesToHexString(data)}")
        var tagIndex =
            1 + mNotificationData.attributeID.appIdentifierLen + 1 + 1 //AttributeID + NULL-terminated
        val displayNameLength =
            (data[tagIndex].toInt() and 0xFF) + (data[tagIndex + 1].toInt() and 0xFF) * 256
        tagIndex += 2
        val displayName = String(data, tagIndex, displayNameLength)
        Log.d(TAG, "parseAppAttributes: Display Name = $displayName")
        mNotificationData.displayName = displayName
        handleNotificationData()
    }

    private fun deleteData(uid: ByteArray) {
        val position = notifications.indexOfFirst { it.notificationUID.contentEquals(uid) }
        if (position != -1) {
            Log.d(TAG, "deleteData: uid = ${Util.bytesToHexString(uid)}, position = $position")
            notifications.removeAt(position)
            myNotification.value =
                myNotification.value.filter { !it.notificationUID.contentEquals(uid) }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun handleNotificationData() {
        notifications.add(mNotificationData)
        myNotification.value += mNotificationData
        notificationEvents.removeAt(0)

        if (notificationEvents.isNotEmpty()) {
            Log.d(TAG, "parseAppAttributes: EventSize = ${notificationEvents.size}")
            val event = notificationEvents.first()
//            requestNotificationAttributes(event.notificationUID)
            delayRequestNotificationAttributes(event.notificationUID)
        }
    }

}