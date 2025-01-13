package com.example.ancs

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private var deviceInfoTextView: TextView? = null
    private var scanButton: Button? = null
    private var connectButton: Button? = null
    private var recyclerView: RecyclerView? = null
    private var notificationAdapter: NotificationAdapter? = null
    private lateinit var mNotificationData: NotificationData

    private val notifications = mutableListOf<NotificationData>()
    private val notificationEvents = mutableListOf<NotificationEvent>()
    private var selectedDevice: BluetoothDevice? = null
    private val pairedDevices = mutableListOf<BluetoothDevice>()

    private var bluetoothGatt: BluetoothGatt? = null
    private var connectedGatt: BluetoothGatt? = null
    private val bluetoothAdapter: BluetoothAdapter by lazy { BluetoothAdapter.getDefaultAdapter() }
    private var isRequestInProgress = false

    companion object {
        private const val TAG = "ANCS_MainActivity"
        private const val REQUEST_ENABLE_BT = 1
        private const val PERMISSION_REQUEST_CODE = 2

        // ANCS UUIDs
        private val ANC_SERVICE_UUID = UUID.fromString("7905F431-B5CE-4E99-A40F-4B1E122D00D0")
        private val ANC_NOTIFICATION_SOURCE_CHARACTERISTIC_UUID =
            UUID.fromString("9FBF120D-6301-42D9-8C58-25E699A21DBD")
        private val ANC_CONTROL_POINT_CHARACTERISTIC_UUID =
            UUID.fromString("69D1D8F3-45E1-49A8-9821-9BBDFDAAD9D9")
        private val CLIENT_CONFIG_DESCRIPTOR_UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private val ANC_DATA_SOURCE_UUID = UUID.fromString("22eac6e9-24d6-4bb5-be44-b36ace7c7bfb")
    }

    @RequiresApi(Build.VERSION_CODES.S)
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        initUI()
        checkBluetoothAndPermissions()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun initUI() {
        with(findViewById<TextView>(R.id.deviceInfoTextView)) { deviceInfoTextView = this }
        with(findViewById<Button>(R.id.scanButton)) { scanButton = this }
        with(findViewById<Button>(R.id.connectButton)) { connectButton = this }
        with(findViewById<RecyclerView>(R.id.recyclerView)) {
            recyclerView = this
            this?.layoutManager = LinearLayoutManager(this@MainActivity).apply {
                orientation = LinearLayoutManager.VERTICAL
            }
            this?.addItemDecoration(
                DividerItemDecoration(
                    this@MainActivity,
                    DividerItemDecoration.VERTICAL
                )
            )
        }

        notificationAdapter = NotificationAdapter(notifications) { onItemClicked(it) }
        recyclerView?.adapter = notificationAdapter

        scanButton?.setOnClickListener { startScan() }
        connectButton?.setOnClickListener { connectToDevice() }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun onItemClicked(notification: NotificationData) {
        Log.d(TAG, "onItemClicked: ${Util.bytesToHexString(notification.notificationUID)}")
        val action = when {
            notification.attributeID.notificationAttributeIDNegativeActionLabel.isNotEmpty() -> 1
            notification.attributeID.notificationAttributeIDPositiveActionLabel.isNotEmpty() -> 0
            else -> 2
        }
        sendNotifyAction(notification.notificationUID, action)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    @RequiresApi(Build.VERSION_CODES.S)
    private fun checkBluetoothAndPermissions() {
        if (!bluetoothAdapter.isEnabled) requestBluetoothEnable()
        if (!hasRequiredPermissions()) requestPermissions()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun requestBluetoothEnable() {
        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun hasRequiredPermissions(): Boolean =
        listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        ).all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            ),
            PERMISSION_REQUEST_CODE
        )
    }

    private fun showMessage(message: String) {
        deviceInfoTextView?.text = message
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun startScan() {
        if (!bluetoothAdapter.isEnabled) {
            showMessage("Bluetooth Turned OFF")
            return
        }

        pairedDevices.clear()
        pairedDevices.addAll(bluetoothAdapter.bondedDevices)

        showDeviceSelectionDialog()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun showDeviceSelectionDialog() {
        val deviceNames = pairedDevices.map { "${it.name} (${it.address})" }
        if (deviceNames.isNotEmpty()) {
            AlertDialog.Builder(this).apply {
                setTitle("Select Device")
                setItems(deviceNames.toTypedArray()) { _, which ->
                    selectedDevice = pairedDevices[which]
                    runOnUiThread { showMessage("Selected Device: ${deviceNames[which]}") }
                }
            }.show()
        } else {
            showMessage("There Are No Paired Devices Available。")
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun connectToDevice() {
        selectedDevice?.let {
            bluetoothGatt = it.connectGatt(this, false, gattCallback)
            showMessage("Connecting: ${it.name} (${it.address})")
        } ?: showMessage("No Device Selected")
    }

    private val gattCallback = object : BluetoothGattCallback() {

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> handleDeviceConnected(gatt)
                BluetoothProfile.STATE_DISCONNECTED -> handleDeviceDisconnected()
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        private fun handleDeviceConnected(gatt: BluetoothGatt) {
            connectedGatt = gatt
            runOnUiThread {
                showMessage("Connected: ${gatt.device.name}")
                if (!gatt.discoverServices()) showMessage("Service Discovery Failed To Start")
            }
        }

        private fun handleDeviceDisconnected() {
            runOnUiThread { showMessage("Disconnect Device") }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            super.onServicesDiscovered(gatt, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                gatt.getService(ANC_SERVICE_UUID)?.let { setupCharacteristics(it) }
                    ?: Log.d(TAG, "ANCS service not found")
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        private fun setupCharacteristics(service: BluetoothGattService) {
            subscribeToNotifications(service.getCharacteristic(ANC_DATA_SOURCE_UUID))
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            super.onCharacteristicChanged(gatt, characteristic, value)
            when (characteristic.uuid) {
                ANC_NOTIFICATION_SOURCE_CHARACTERISTIC_UUID -> parseNotificationEvent(value)
                ANC_DATA_SOURCE_UUID -> handleDataSource(value)
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
        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            super.onDescriptorWrite(gatt, descriptor, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (descriptor.characteristic.uuid == ANC_DATA_SOURCE_UUID) {
                    subscribeToNotifications(
                        gatt.getService(ANC_SERVICE_UUID)
                            ?.getCharacteristic(ANC_NOTIFICATION_SOURCE_CHARACTERISTIC_UUID)
                    )
                    Log.d(TAG, "Data Source Subscribe Success")
                } else if (descriptor.characteristic.uuid == ANC_NOTIFICATION_SOURCE_CHARACTERISTIC_UUID) {
                    Log.d(TAG, "Notification Source Subscribe Success")
                }
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Request sent successfully")
            } else {
                Log.e(TAG, "Failed to send request, status: $status")
            }
            isRequestInProgress = false

        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun subscribeToNotifications(characteristic: BluetoothGattCharacteristic?) {
        bluetoothGatt?.setCharacteristicNotification(characteristic, true)
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
            TAG, "Event ID: ${data[0]}" +
                    " Event Flags: ${data[1]}" +
                    " CategoryId: ${data[2]}" +
                    " Category Count: ${data[3]}" +
                    " NotificationUID: ${Util.bytesToHexString(data.copyOfRange(4, 8))}"
        )

        when (notificationEvent.eventId) {
            0, 1 -> {
                notificationEvents.add(notificationEvent)
                if (!isRequestInProgress) {
                    requestNotificationAttributes(notificationEvent.notificationUID)
                }
            }
            2 -> deleteData(notificationEvent.notificationUID)
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun parseNotificationData(data: ByteArray) {
        Log.d(TAG, "data: ${Util.bytesToHexString(data)}")
        val notificationUID = data.sliceArray(1..4).reversed()
            .fold(0) { acc, byte -> (acc shl 8) or (byte.toInt() and 0xFF) }
        Log.d(TAG, "UID: $notificationUID")

        val commandId = data[0].toInt()
        var tagIndex = 5
        var appIdentifier = ""
        var appIdentifierLen = 0;
        var title = ""
        var subtitle = ""
        var msg = ""
        var messageSize = ""
        var timeStamp = ""
        var positiveActionLabel = ""
        var negativeActionLabel = ""

        fun parseField(): String {
            val len =
                (data[tagIndex + 1].toInt() and 0xFF) + (data[tagIndex + 2].toInt() and 0xFF) * 256
            if (tagIndex + 3 + len > data.size) {
                throw IndexOutOfBoundsException("Attempted to read beyond the end of the data array")
            }
            val fieldValue = String(data, tagIndex + 3, len)
            tagIndex += 3 + len
            return fieldValue
        }

        while (tagIndex < data.size) {
            when (val tag = data[tagIndex]) {

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
                        TAG,
                        "positiveActionLabel = $positiveActionLabel"
                    )
                }

                0x07.toByte() -> { //negativeActionLabel
                    negativeActionLabel = parseField(); Log.d(
                        TAG,
                        "negativeActionLabel = $negativeActionLabel"
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

        requestAppAttributes(appIdentifier)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun requestNotificationAttributes(data: ByteArray) {
        if (isRequestInProgress) {
            Log.d(TAG, "Request already in progress, skipping...")
            return
        }
        Log.d(TAG, "requestNotificationAttributes")
        isRequestInProgress = true;

        val getNotificationAttribute = byteArrayOf(
            0x00.toByte(), //commandId
            *data
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
        Log.d(
            TAG,
            "send notify attribute request = " + Util.bytesToHexString(getNotificationAttribute)
        )
        sendRequest(getNotificationAttribute)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun requestAppAttributes(appIdentifier: String) {
        val commandId = byteArrayOf(0x01.toByte())
        val appIdentifierArray = appIdentifier.toByteArray()
        val appAttributeIDArray = byteArrayOf(0x00.toByte())
        val combinedByteArray = commandId + appIdentifierArray + byteArrayOf(0x00.toByte()) + appAttributeIDArray
        Log.i(TAG, "send app attribute request = " + Util.bytesToHexString(combinedByteArray))
        sendRequest(combinedByteArray)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun parseAppAttributes(data: ByteArray) {
        Log.d(TAG, "App attributes response: ${Util.bytesToHexString(data)}")
        var tagIndex =
            1 + mNotificationData.attributeID.appIdentifierLen + 1 + 1 //AttributeID + NULL-terminated
        val displayNameLength =
            (data[tagIndex].toInt() and 0xFF) + (data[tagIndex + 1].toInt() and 0xFF) * 256
        tagIndex += 2
        val displayName = String(data, tagIndex, displayNameLength)
        Log.d(TAG, "Display Name: $displayName")
        runOnUiThread {
            mNotificationData.displayName = displayName
            notifications.add(mNotificationData)
            notificationAdapter?.notifyItemInserted(notifications.size)
            notificationEvents.removeAt(0)

            if (notificationEvents.isNotEmpty()) {
                val event = notificationEvents.first()
                requestNotificationAttributes(event.notificationUID)
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun sendNotifyAction(uid: ByteArray, action: Int) {
        val combinedByteArray = byteArrayOf(0x02.toByte()) + uid + action.toByte()
        Log.i(TAG, "send notify action = ${Util.bytesToHexString(combinedByteArray)}")
        sendRequest(combinedByteArray)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun sendRequest(data: ByteArray) {
        bluetoothGatt?.getService(ANC_SERVICE_UUID)
            ?.getCharacteristic(ANC_CONTROL_POINT_CHARACTERISTIC_UUID)
            ?.apply {
                value = data
                bluetoothGatt?.writeCharacteristic(this)
            }
    }

    private fun deleteData(uid: ByteArray) {
        runOnUiThread {
            val position = notifications.indexOfFirst { it.notificationUID.contentEquals(uid) }
            if (position != -1) {
                notifications.removeAt(position)
                notificationAdapter?.notifyItemRemoved(position)
            }
        }

    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onDestroy() {
        super.onDestroy()
        bluetoothGatt?.close()
    }

}
