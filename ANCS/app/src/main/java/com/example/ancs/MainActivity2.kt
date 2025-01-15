package com.example.ancs

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity2: AppCompatActivity() {

    private var deviceInfoTextView: TextView? = null
    private var connectButton: Button? = null
    private var disconnectButton: Button? = null
    private var recyclerView: RecyclerView? = null
    private var notificationAdapter: NotificationAdapter2? = null

    private lateinit var ancsClient: AncsClient

    private val bluetoothAdapter: BluetoothAdapter by lazy { BluetoothAdapter.getDefaultAdapter() }

    companion object {
        private const val TAG = "ANCS_MainActivity2"
        private const val REQUEST_ENABLE_BT = 1
        private const val PERMISSION_REQUEST_CODE = 2

    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main2)

        ancsClient = AncsClient(this)

        initUI()
        setupObservers()
        checkBluetoothAndPermissions()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun initUI() {
        deviceInfoTextView = findViewById(R.id.deviceInfoTextView)
        connectButton = findViewById(R.id.connectButton)
        disconnectButton = findViewById(R.id.disconnectButton)
        recyclerView = findViewById(R.id.recyclerView)

        recyclerView?.layoutManager = LinearLayoutManager(this).apply {
            orientation = LinearLayoutManager.VERTICAL
        }

        notificationAdapter = NotificationAdapter2(ancsClient.notification.value) { onItemClicked(it) }
        recyclerView?.adapter = notificationAdapter

        connectButton?.setOnClickListener { startConnected() }
        disconnectButton?.setOnClickListener { disconnectToDevice() }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun onItemClicked(notification: NotificationData) {
        Log.d(TAG, "onItemClicked: ${Util.bytesToHexString(notification.notificationUID)}")
        val action = when {
            notification.attributeID.notificationAttributeIDNegativeActionLabel.isNotEmpty() -> 1
            notification.attributeID.notificationAttributeIDPositiveActionLabel.isNotEmpty() -> 0
            else -> 2
        }
        ancsClient.performAction(notification.notificationUID, action)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun checkBluetoothAndPermissions() {
        if (!bluetoothAdapter.isEnabled) requestBluetoothEnable()
        if (!hasRequiredPermissions()) requestPermissions()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun requestBluetoothEnable() {
        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
    }

    private fun hasRequiredPermissions(): Boolean =
        listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        ).all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

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

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun setupObservers() {
        lifecycleScope.launch {
            ancsClient.notification.collect { notification ->
                notificationAdapter?.submitList(notification)
            }
        }

        lifecycleScope.launch {
            ancsClient.connectedDevice.collect { device ->
                if (device != null) {
                    showMessage("Connected to: ${device.name}")
                } else {
                    showMessage("Disconnected")
                }
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun startConnected() {
        val devices = ancsClient.getRemoteDevices()
        val deviceNames = devices.map { it.name }
        showDeviceSelectionDialog(deviceNames, devices)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun showDeviceSelectionDialog(
        deviceNames: List<String>,
        devices: List<BluetoothDevice>
    ) {
        if (deviceNames.isNotEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Select Device")
                .setItems(deviceNames.toTypedArray()) { _, which ->
                    showMessage("Connecting to: ${deviceNames[which]}")
                    ancsClient.connect(devices[which])
                }
                .show()
        } else {
            showMessage("No paired devices found.")
        }
    }

    private fun showMessage(message: String) {
        runOnUiThread {
            deviceInfoTextView?.text = message
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun disconnectToDevice() {
        ancsClient.disconnect()
    }

}