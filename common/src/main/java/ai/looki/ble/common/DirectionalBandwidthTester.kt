package ai.looki.ble.common

import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicLong

class DirectionalBandwidthTester {
    private var startTime = 0L
    private val totalBytes = AtomicLong(0)
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

    fun addBytes(n: Int) {
        totalBytes.addAndGet(n.toLong())
    }

    fun setCallback(cb: (Float) -> Unit) {
        callback = cb
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    // 修改 currentMbps() 为计算瞬时速率
    fun currentMbps(): Float {
        val currentTime = System.currentTimeMillis()
        val deltaTime = (currentTime - lastCheckTime) / 1000f
        if (deltaTime <= 0) return 0f

        val currentTotal = totalBytes.get()
        val deltaBytes = currentTotal - lastTotalBytes
        val rate = deltaBytes * 8 / (deltaTime * 1_000_000f)

        // 更新记录
        lastTotalBytes = currentTotal
        lastCheckTime = currentTime

        return rate
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