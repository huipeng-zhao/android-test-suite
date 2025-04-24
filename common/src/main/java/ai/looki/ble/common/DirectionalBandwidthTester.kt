package ai.looki.ble.common

import kotlinx.coroutines.*
import java.util.LinkedList
import java.util.concurrent.atomic.AtomicLong

class DirectionalBandwidthTester {
    private val windowSize = 1000L // 1秒窗口
    private val byteRecords = LinkedList<Pair<Long, Int>>()

    private var startTime = 0L
    val totalBytes = AtomicLong(0)
    private var job: Job? = null
    private var callback: ((Float) -> Unit)? = null

    // 新增变量：记录上一次的总字节和时间
    private var lastTotalBytes: Long = 0
    private var lastCheckTime: Long = 0

    fun start() {
        totalBytes.set(0)
        startTime = System.currentTimeMillis()
        lastTotalBytes = 0 // 重置初始值
        lastCheckTime = startTime
        startLoop()
    }

    fun addBytes(bytes: Int) {
        synchronized(this) {
            val now = System.currentTimeMillis()
            byteRecords.add(now to bytes)
            // 移除超过窗口期的记录
            while (byteRecords.isNotEmpty() && now - byteRecords.first.first > windowSize) {
                byteRecords.removeFirst()
            }
        }
    }

    fun getTotalBytes() = totalBytes.get()

    fun setCallback(cb: (Float) -> Unit) {
        callback = cb
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    // 修改 currentMbps() 为计算瞬时速率

    fun currentMbps(): Float {
        synchronized(this) {
            if (byteRecords.size < 2) return 0f
            val first = byteRecords.first
            val last = byteRecords.last
            val durationSec = (last.first - first.first) / 1000f
            val totalBits = byteRecords.sumOf { it.second.toLong() } * 8
            return totalBits / (durationSec * 1_000_000f)
        }
    }

    private fun startLoop() {
        job?.cancel()
        job = CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                delay(1000) // 每秒触发一次
                callback?.invoke(currentMbps())
            }
        }
    }
}