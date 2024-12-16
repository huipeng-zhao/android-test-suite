package com.example.shakedetector

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import java.util.Timer
import java.util.TimerTask
import kotlin.math.abs
import kotlin.math.sqrt

class ShakeDetector private constructor(private val mShakeListener: OnShakeListener) :
    SensorEventListener {

    companion object {
        private const val DEFAULT_THRESHOLD_ACCELERATION = 2.0
        private const val DEFAULT_THRESHOLD_SHAKE_NUMBER = 2
        private const val INTERVAL = 200L

        private val MAX_SENSOR_BUNDLES = 100 // 最大数据包数
        private val MAX_DATA_TIME_WINDOW = 1000L // 2秒内的数据

        private var mSensorManager: SensorManager? = null
        private var mSensorEventListener: ShakeDetector? = null

        private val kalmanX = KalmanFilter()
        private val kalmanY = KalmanFilter()
        private val kalmanZ = KalmanFilter()

        @JvmStatic
        fun create(context: Context, listener: OnShakeListener): Boolean {
            requireNotNull(context) { "Context must not be null" }
            requireNotNull(listener) { "Shake listener must not be null" }

            if (mSensorManager == null) {
                mSensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            }
            mSensorEventListener = ShakeDetector(listener)

            return mSensorManager?.registerListener(
                mSensorEventListener,
                mSensorManager!!.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_GAME
            ) ?: false
        }

        @JvmStatic
        fun start(): Boolean {
            return mSensorManager != null && mSensorEventListener != null &&
                    mSensorManager!!.registerListener(
                        mSensorEventListener,
                        mSensorManager!!.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                        SensorManager.SENSOR_DELAY_GAME
                    )
        }

        @JvmStatic
        fun stop() {
            mSensorManager?.unregisterListener(mSensorEventListener)
        }

        @JvmStatic
        fun destroy() {
            mSensorManager = null
            mSensorEventListener = null
        }
    }

    private val mSensorBundles = mutableListOf<SensorBundle>()
    private val mLock = Any()
    private var mThresholdAcceleration = DEFAULT_THRESHOLD_ACCELERATION
    private var mThresholdShakeNumber = DEFAULT_THRESHOLD_SHAKE_NUMBER

    interface OnShakeListener {
        fun onShake()
    }

    override fun onSensorChanged(sensorEvent: SensorEvent?) {
        sensorEvent?.let {
            // 使用 Kalman Filter 来平滑加速度数据
            val rawX = sensorEvent.values[0]
            val rawY = sensorEvent.values[1]
            val rawZ = sensorEvent.values[2]

            val filteredX = kalmanX.update(rawX.toDouble())
            val filteredY = kalmanY.update(rawY.toDouble())
            val filteredZ = kalmanZ.update(rawZ.toDouble())

            val sensorBundle = SensorBundle(filteredX, filteredY, filteredZ, it.timestamp)

            synchronized(mLock) {
                if (mSensorBundles.isEmpty() || sensorBundle.timestamp - mSensorBundles.last().timestamp > INTERVAL) {
                    val currentTime = System.currentTimeMillis()
                    //优化内存，减少mutableListOf SensorBundle数量
                    mSensorBundles.removeAll { currentTime - it.timestamp > MAX_DATA_TIME_WINDOW }
                    if (mSensorBundles.size >= MAX_SENSOR_BUNDLES) {
                        mSensorBundles.removeAt(0)
                    }
                    mSensorBundles.add(sensorBundle)
                }
            }

            // 更新动态阈值
            updateDynamicThreshold()

            // 执行摇动检测
            performCheck()

        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // 精度变化不做处理
    }

    private fun updateDynamicThreshold() {
        val accelerations = mSensorBundles.map { bundle ->
            sqrt(bundle.xAcc * bundle.xAcc + bundle.yAcc * bundle.yAcc + bundle.zAcc * bundle.zAcc)
        }

        if (accelerations.isNotEmpty()) {
            val mean = accelerations.average()
            val variance = accelerations.map { (it - mean) * (it - mean) }.average()
            val stdDev = sqrt(variance)

            // 根据标准差调整阈值
            mThresholdAcceleration = mean + stdDev
        }
    }

    private var lastShakeTime: Long = 0
    private val SHAKE_COOL_DOWN_TIME = 500L
    private val MIN_SHAKE_MOVEMENT = 1.5
    private var isShakeCooldown: Boolean = false

    private fun performCheck() {
        synchronized(mLock) {
            val shakeThreshold = mThresholdAcceleration
            var shakeDetected = false

            var lastX = 0.0
            var lastY = 0.0
            var lastZ = 0.0

            var totalMovement = 0.0
            var movementCount = 0
            val movements = mutableListOf<Double>()

            // 简化计算过程
            for (sensorBundle in mSensorBundles) {
                val deltaX = sensorBundle.xAcc - lastX
                val deltaY = sensorBundle.yAcc - lastY
                val deltaZ = sensorBundle.zAcc - lastZ

                val movement = sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ)

                if (movement > shakeThreshold) {
                    movementCount++
                }

                totalMovement += movement
                movements.add(movement)

                lastX = sensorBundle.xAcc
                lastY = sensorBundle.yAcc
                lastZ = sensorBundle.zAcc
            }

            // 检查是否满足多次震动条件以及频率检测
            if (movementCount >= mThresholdShakeNumber && isHighFrequencyMovement(movements)) {
                shakeDetected = true
            }

            // 判断是否触发摇动事件，并确保冷却时间有效
            if (shakeDetected && !isShakeCooldown && System.currentTimeMillis() - lastShakeTime > SHAKE_COOL_DOWN_TIME) {
                if (totalMovement > MIN_SHAKE_MOVEMENT) {
                    Log.d("", "mSensorBundles.size = ${mSensorBundles.size}")
                    mShakeListener.onShake()
                    lastShakeTime = System.currentTimeMillis()

                    // 设置冷却状态
                    isShakeCooldown = true

                    // 清空历史数据以准备下一次检测
                    mSensorBundles.clear()

                    // 启动冷却定时器，防止连续触发
                    Timer().schedule(object : TimerTask() {
                        override fun run() {
                            isShakeCooldown = false
                        }
                    }, SHAKE_COOL_DOWN_TIME)
                }
            }
        }
    }

    private fun isHighFrequencyMovement(movements: List<Double>): Boolean {
        val averageMovement = movements.average()
        val deviation = movements.map { abs(it - averageMovement) }.average()
        return deviation > 0.5
    }
}
