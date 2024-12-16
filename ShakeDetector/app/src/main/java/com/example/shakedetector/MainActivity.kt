package com.example.shakedetector

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.text.SimpleDateFormat
import java.util.Date

class MainActivity : AppCompatActivity(), ShakeDetector.OnShakeListener {
    companion object {
        const val TAG = "ShakeDetector_MainActivity"
        private const val STATUS = "status"
    }

    private lateinit var mStatus: TextView

    private var mTotalCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initView()
        initShakeDetector()
        //test
        getSensorList()
    }

    private fun initView() {
        supportActionBar?.apply {
            title = getString(R.string.app_name)
        }
        mStatus = findViewById(R.id.status)
    }

    private fun initShakeDetector() {
        // We create and start the shake detector here.
        if (ShakeDetector.create(this, this)) {
            addStatusMessage(getString(R.string.shake_detector_created))
        } else {
            addStatusMessage(getString(R.string.shake_detector_error))
        }
    }

    private fun getSensorList() {
        // 获取 SensorManager 实例
        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        // 获取所有传感器列表
        val sensorList: List<Sensor> = sensorManager.getSensorList(Sensor.TYPE_ALL)

        // 遍历并打印所有传感器的信息
        for (sensor in sensorList) {
            Log.d("SensorList", "Name: ${sensor.name}, Type: ${sensor.type}")
        }
    }

    override fun onResume() {
        super.onResume()
        if (ShakeDetector.start()) {
            mTotalCount = 0
            addStatusMessage(getString(R.string.shake_detector_restarted))
        }
    }

    override fun onStop() {
        super.onStop()
        ShakeDetector.stop()
        mTotalCount = 0
        addStatusMessage(getString(R.string.shake_detector_stopped))
    }

    override fun onDestroy() {
        super.onDestroy()
        ShakeDetector.destroy()
        mTotalCount = 0
        addStatusMessage(getString(R.string.shake_detector_destroyed))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATUS, mStatus.text.toString())
    }

    override fun onShake() {
        mTotalCount++
        addStatusMessage(getString(R.string.shake_detected))
    }

    private fun addStatusMessage(message: String) {
        val date = SimpleDateFormat("HH:mm:ss-SSS").format(Date())
        val status = "\n[$date] $message [$mTotalCount]"
        mStatus.append(status)
        android.util.Log.d(TAG, status)
    }

}