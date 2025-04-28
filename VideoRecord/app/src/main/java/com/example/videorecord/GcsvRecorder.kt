package com.example.videorecord

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.util.Log
import androidx.core.content.getSystemService
import java.io.BufferedWriter
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread

class GcsvRecorder(context: Context) : SensorEventListener {
    private sealed class Event {
        data class FileEvent(val file: File?): Event()
        data class ImuEvent(val acc: SensorEvent, val gyr: SensorEvent): Event()
    }

    companion object {
        private val TAG = GcsvRecorder::class.java.simpleName
    }

    private val myId = context.packageName
    private val mySensorManager = context.getSystemService<SensorManager>()!!
    private var myLastAccEvent: SensorEvent? = null
    private var myLastGyrEvent: SensorEvent? = null
    private val myEventQueue = LinkedBlockingQueue<Event>(10000)
    private var myDropCount = 0
    private var myIsActive = true
    private var myThread: Thread
    private var myStartTime: Long = 0L
    private var myRawGyrCount = 0L
    private var myRawAccCount = 0L

    @Suppress("SpellCheckingInspection")
    private fun gcsvHeader(timestampNs: Long): String {
        // https://docs.gyroflow.xyz/app/technical-details/gcsv-format
        return StringBuilder().apply {
            append("GYROFLOW IMU LOG\n")
            append("version,1.3\n")
            append("id,${myId}\n")
            append("orientation,YxZ\n")
            append("timestamp,${timestampNs}\n")
            append("lens_info,wide\n")
            append("t,gx,gy,gz,ax,ay,az\n")
        }.toString()
    }

    private fun processEvents() {
        Log.d(TAG, "processEvents: thread started")
        var file: File? = null
        var writer: BufferedWriter? = null
        var index = 0
        while (myIsActive) {
            try {
                val firstEvent = myEventQueue.take()
                val events = ArrayList<Event>()
                events.add(firstEvent)
                myEventQueue.drainTo(events)
                events.forEach { ev ->
                    when (ev) {
                        is Event.FileEvent -> { // Close the current recording file and open a new one if needed.
                            file?.run { Log.d(TAG, "processEvents: close file ${this.path}") }
                            writer?.close()
                            file = ev.file
                            file?.run { Log.d(TAG, "processEvents: open file ${this.path}") }
                            writer = file?.bufferedWriter() // nullable
                            index = 0
                        }
                        is Event.ImuEvent -> { // Write the IMU data to the current recording file.
                            if (index == 0) {
                                writer?.write(gcsvHeader(ev.gyr.timestamp)) // always use gyro's timestamp for consistency.
                            }
                            writer?.write("%d,%f,%f,%f,%f,%f,%f\n".format(
                                index++, ev.gyr.values[0], ev.gyr.values[1], ev.gyr.values[2], ev.acc.values[0], ev.acc.values[1], ev.acc.values[2]
                            ))
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "processEvents: ${e.message}")
                break
            }
        }
        Log.d(TAG, "processEvents: thread ended")
    }

    init {
        myThread = thread { processEvents() }
        Log.d(TAG, "initialized")
    }

    fun close() {
        Log.d(TAG, "close: begin")
        myIsActive = false
        myEventQueue.clear()
        myEventQueue.put(Event.FileEvent(null))
        try {
            myThread.join(1_000)
        } catch (e: Exception) {
            Log.e(TAG, "close: thread.join(), ${e.message}")
        }
        if (myThread.isAlive) {
            Log.e(TAG, "close: thread join timeout, it is still alive")
        }
        Log.d(TAG, "close: end")
    }

    fun startSensor(hz: Double = 500.0) {
        Log.d(TAG, "startSensor: hz=$hz")
        myDropCount = 0
        myEventQueue.clear()
        myLastAccEvent = null
        myLastGyrEvent = null
        myStartTime = SystemClock.elapsedRealtime()
        val samplingPeriodUs = (1_000_000 / hz).toInt()
        arrayOf(Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE).forEach { sensorType ->
            mySensorManager.registerListener(this, mySensorManager.getDefaultSensor(sensorType), samplingPeriodUs)
        }
    }

    // Close the current recording file and open a new one.
    fun startRecord(file: File) {
        Log.d(TAG, "startRecord: file=${file.path}")
        myRawGyrCount = 0
        myRawAccCount = 0
        myEventQueue.put(Event.FileEvent(file))
    }

    fun stop() {
        if (myStartTime > 0) {
            val elapsedTime = (SystemClock.elapsedRealtime() - myStartTime).toDouble()/1000.0 // in seconds
            val hzGyr = if (elapsedTime > 0) myRawGyrCount.toDouble()/elapsedTime else 0.0
            val hzAcc = if (elapsedTime > 0) myRawAccCount.toDouble()/elapsedTime else 0.0
            Log.d(TAG, "stop: gyr.hz=%.1f, acc.hz=%.1f, RawGyrCount=%d".format(hzGyr, hzAcc, myRawGyrCount))
            myStartTime = 0L // reset start time
        } else {
            Log.d(TAG, "stop")
        }
        mySensorManager.unregisterListener(this)
        myEventQueue.put(Event.FileEvent(null))
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> { myLastAccEvent = event; myRawGyrCount +=1 }
            Sensor.TYPE_GYROSCOPE -> { myLastGyrEvent = event; myRawAccCount += 1 }
        }
        val lastAcc = myLastAccEvent
        val lastGyr = myLastGyrEvent
        if (event.sensor.type != Sensor.TYPE_GYROSCOPE) {
            // We refer the GYROSCOPE as the freq time base.
            return
        }
        if (lastAcc == null || lastGyr == null) {
            // We need both accelerometer and gyroscope to form a valid IMU event.
            // So we will wait until we have both before sending to the queue.
            // This is to ensure that the timestamp of both events are aligned.
            return
        }
        if (!myEventQueue.offer(Event.ImuEvent(lastAcc, lastGyr))) {
            // queue is full, drop but cry.
            myDropCount += 1
            Log.e(TAG, "Event queue is full, dropping event. dropCount=$myDropCount")
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Log.d(TAG, "onAccuracyChanged: sensor=${sensor?.name}, accuracy=$accuracy")
    }
}