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

    private lateinit var deviceInfoTextView: TextView
    private lateinit var scanButton: Button
    private lateinit var connectButton: Button
    private lateinit var recyclerView: RecyclerView
    private lateinit var notificationAdapter: NotificationAdapter
    private val notifications = mutableListOf<NotificationData>()
    private var selectedDevice: BluetoothDevice? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var connectedGatt: BluetoothGatt? = null
    private val notificationEvents = mutableListOf<NotificationEvent>()

    private val bluetoothAdapter: BluetoothAdapter by lazy { BluetoothAdapter.getDefaultAdapter() }
    private val pairedDevices = mutableListOf<BluetoothDevice>()

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
        deviceInfoTextView = findViewById(R.id.deviceInfoTextView)!!
        scanButton = findViewById(R.id.scanButton)!!
        connectButton = findViewById(R.id.connectButton)!!
        recyclerView = findViewById(R.id.recyclerView)!!

        notificationAdapter = NotificationAdapter(notifications)
        recyclerView.adapter = notificationAdapter
        recyclerView.layoutManager = LinearLayoutManager(this).apply {
            orientation = LinearLayoutManager.VERTICAL
        }
        recyclerView.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))

        scanButton.setOnClickListener { startScan() }
        connectButton.setOnClickListener { connectToDevice() }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    @RequiresApi(Build.VERSION_CODES.S)
    private fun checkBluetoothAndPermissions() {
        if (!bluetoothAdapter.isEnabled) {
            requestBluetoothEnable()
        }

        if (!hasRequiredPermissions()) {
            requestPermissions()
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun requestBluetoothEnable() {
        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun hasRequiredPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.BLUETOOTH_SCAN
        ) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
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
        deviceInfoTextView.text = message
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
            val builder = AlertDialog.Builder(this)
            builder.setTitle("Select Device")
            builder.setItems(deviceNames.toTypedArray()) { _, which ->
                selectedDevice = pairedDevices[which]
                runOnUiThread { showMessage("Selected Device: ${deviceNames[which]}") }
            }
            builder.show()
        } else {
            showMessage("There Are No Paired Devices Available。")
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun connectToDevice() {
        selectedDevice?.let {
            bluetoothGatt = it.connectGatt(this, false, gattCallback)
            showMessage("Connecting: ${it.name} (${it.address})")
        } ?: run { showMessage("No Device Selected") }
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
                val success = gatt.discoverServices()
                if (!success) showMessage("Service Discovery Failed To Start")
            }
        }

        private fun handleDeviceDisconnected() {
            runOnUiThread { showMessage("Disconnect Device") }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            super.onServicesDiscovered(gatt, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val ancsService = gatt.getService(ANC_SERVICE_UUID)
                if (ancsService != null) {
                    setupCharacteristics(ancsService)
                } else {
                    Log.d(TAG, "ANCS service not found")
                }
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        private fun setupCharacteristics(service: BluetoothGattService) {
            subscribeToNotifications(service.getCharacteristic(ANC_DATA_SOURCE_UUID))
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            super.onCharacteristicChanged(gatt, characteristic)
            Log.d(TAG, "onCharacteristicChanged -> uuid: ${characteristic.uuid}")
            if (characteristic.value.isEmpty()) {
                Log.e(TAG, "Received empty data")
            }
            when (characteristic.uuid) {
                ANC_NOTIFICATION_SOURCE_CHARACTERISTIC_UUID -> {
                    parseNotificationEvent(characteristic.value)
                }

                ANC_DATA_SOURCE_UUID -> {
                    val data = characteristic.value
                    if (data[0].toInt() == 0) {
                        parseNotificationData(data)
                    }
                }
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
                if (descriptor.characteristic.uuid.equals(ANC_DATA_SOURCE_UUID)) {
                    subscribeToNotifications(
                        gatt.getService(ANC_SERVICE_UUID)
                            .getCharacteristic(ANC_NOTIFICATION_SOURCE_CHARACTERISTIC_UUID)
                    )
                    Log.d(TAG, "Data Source Subscribe Success")
                } else if (descriptor.characteristic.uuid.equals(
                        ANC_NOTIFICATION_SOURCE_CHARACTERISTIC_UUID
                    )
                ) {
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
            if (characteristic != null) {
                Log.d(
                    TAG,
                    "CharacteristicWrite data: ${Util.bytesToHexString(characteristic.value)}"
                )
            }
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
        val descriptor = characteristic?.getDescriptor(CLIENT_CONFIG_DESCRIPTOR_UUID)
        descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        bluetoothGatt?.writeDescriptor(descriptor)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun parseNotificationEvent(data: ByteArray) {
        if (data.isEmpty()) {
            Log.e(TAG, "Received empty notification event")
            return
        }
        Log.d(
            TAG,
            "Received Event: Length: ${data.size}  " +
                    "EventData: ${
                        data.joinToString(", ") {
                            String.format(
                                "%02X",
                                it.toInt() and 0xFF
                            )
                        }
                    }"
        )

        val notificationEvent = NotificationEvent(
            data[0].toInt(),
            data[1].toInt(),
            data[2].toInt(),
            data[3].toInt(),
            data.copyOfRange(4, 8)
        )
        if (notificationEvent.eventId == 0) {
            Log.d(TAG, "EventId: Add")
            notificationEvents.add(notificationEvent)
            if (!isRequestInProgress) {
                requestNotificationAttributes(notificationEvent.notificationUID)
            }

        } else if (notificationEvent.eventId == 1) {
            //更改
            Log.d(TAG, "EventId: Modified")
        } else if (notificationEvent.eventId == 2) {
            //删除
            Log.d(TAG, "EventId: Removed")
        }

        if (notificationEvent.eventFlags and 0x01 != 0) {
            Log.d(TAG, "isSilent Notification")
        } else if (notificationEvent.eventFlags and 0x02 != 0) {
            Log.d(TAG, "Important Notification")
        } else if (notificationEvent.eventFlags and 0x04 != 0) {
            Log.d(TAG, "PreExisting Notification")
        } else if (notificationEvent.eventFlags and 0x08 != 0) {
            Log.d(TAG, "PositiveAction Notification")
        } else if (notificationEvent.eventFlags and 0x10 != 0) {
            Log.d(TAG, "NegativeAction Notification")
        }
        Log.d(
            TAG, "Event ID: ${data[0]}" +
                    " Event Flags: ${data[1]}" +
                    " CategoryId: ${data[2]}" +
                    " Category Count: ${data[3]}" +
                    " NotificationUID: ${Util.bytesToHexString(data.copyOfRange(4, 8))}"
        )
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun parseNotificationData(data: ByteArray) {
        Log.d(TAG, "CommandID = ${data[0]}")
        Log.d(TAG, "data: ${Util.bytesToHexString(data)}")

        // 解析 NotificationUID
        val notificationUID = data.sliceArray(1..4).reversed()
            .fold(0) { acc, byte -> (acc shl 8) or (byte.toInt() and 0xFF) }
        Log.d(TAG, "UID: $notificationUID")

        val commandId = data[0].toInt()
        var tagIndex = 5
        var appId = 0
        var title = ""
        var subtitle = ""
        var msg = ""
        var messageSize = 0
        var positiveActionLabel = ""
        var negativeActionLabel = ""

        // 辅助函数：解析字段
        fun parseField(tag: Byte): String {
            val len =
                (data[tagIndex + 1].toInt() and 0xFF) + (data[tagIndex + 2].toInt() and 0xFF) * 256
            val fieldValue = String(data, tagIndex + 3, len)
            tagIndex += 3 + len
            return fieldValue
        }

        while (tagIndex < data.size) {
            val tag = data[tagIndex]
            when (tag) {

                0x00.toByte() -> { // AppIdentifier
                    appId = data[tagIndex + 1].toInt() and 0xFF
                    Log.d(TAG, "appId = $appId")
                    tagIndex += 2
                }

                0x01.toByte() -> { //title
                    title = parseField(tag); Log.d(TAG, "title = $title")
                }

                0x02.toByte() -> { //subtitle
                    subtitle = parseField(tag); Log.d(TAG, "subtitle = $subtitle")
                }

                0x03.toByte() -> { //message
                    msg = parseField(tag); Log.d(TAG, "message = $msg")
                }

                0x04.toByte() -> { //messageSize
                    messageSize = data[tagIndex + 1].toInt() and 0xFF; Log.d(
                        TAG,
                        "messageSize = $messageSize"
                    ); tagIndex += 2
                }

                0x06.toByte() -> { //positiveActionLabel
                    positiveActionLabel = parseField(tag); Log.d(
                        TAG,
                        "positiveActionLabel = $positiveActionLabel"
                    )
                }

                0x07.toByte() -> { //negativeActionLabel
                    negativeActionLabel = parseField(tag); Log.d(
                        TAG,
                        "negativeActionLabel = $negativeActionLabel"
                    )
                }

                0x05.toByte() -> { //timeStamp
                    val timestamp =
                        (data[tagIndex + 1].toInt() and 0xFF) + (data[tagIndex + 2].toInt() and 0xFF) * 256
                    Log.d(TAG, "date = $timestamp")
                    tagIndex += 3
                }

                else -> tagIndex += 1 // 跳过不需要解析的字段
            }
        }

        val attributeID = AttributeID(
            appId,
            title,
            subtitle,
            msg,
            messageSize,
            "",
            positiveActionLabel,
            negativeActionLabel
        )

        runOnUiThread {
            val notificationData = NotificationData(commandId, data, attributeID)
            notifications.add(notificationData)
            notificationAdapter.notifyItemInserted(notifications.size)
            notificationEvents.removeAt(0)

            if (notificationEvents.isNotEmpty()) {
                val event = notificationEvents.first()
                requestNotificationAttributes(event.notificationUID)
            }
        }
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
            data[0],
            data[1],
            data[2],
            data[3], //NotificationUID
//            0xFF.toByte(), ////AttributeID --NotificationAttributeIDAppIdentifier
            0x00.toByte(),
//            0xFF.toByte(),
            0x01.toByte(),
            0xFF.toByte(), //AttributeID --NotificationAttributeIDTitle
            0xFF.toByte(),

            0x02.toByte(),
            0xFF.toByte(), //AttributeID --NotificationAttributeIDSubtitle
            0xFF.toByte(),

            0x03.toByte(),
            0xFF.toByte(), //AttributeID --NotificationAttributeIDMessage
            0xFF.toByte(),

            0x04.toByte(), //AttributeID --NotificationAttributeIDMessageSize
            0x05.toByte(), //AttributeID --NotificationAttributeIDDate
            0x06.toByte(), //AttributeID --NotificationAttributeIDPositiveActionLabel
            0x07.toByte(), //AttributeID --NotificationAttributeIDNegativeActionLabel
        )

        Log.i(TAG, "send common = " + Util.bytesToHexString(getNotificationAttribute))
        if (bluetoothGatt != null) {
            val service = bluetoothGatt?.getService(ANC_SERVICE_UUID)
            if (service == null) {
                Log.d(TAG, "cant find service")
            } else {
                Log.d(TAG, "find service")
                val characteristic =
                    service.getCharacteristic(ANC_CONTROL_POINT_CHARACTERISTIC_UUID)
                if (characteristic == null) {
                    Log.d(TAG, "cant find chara")
                } else {
                    Log.d(TAG, "find chara")
                    characteristic.value = getNotificationAttribute
                    bluetoothGatt?.writeCharacteristic(characteristic)
                }
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onDestroy() {
        super.onDestroy()
        bluetoothGatt?.close() // 关闭连接
    }

}
