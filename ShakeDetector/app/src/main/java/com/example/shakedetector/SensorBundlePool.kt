package com.example.shakedetector

class SensorBundlePool(private val maxSize: Int = 100) {
    private val pool: MutableList<SensorBundle> = ArrayList(maxSize)

    init {
        // 初始化池中对象，预先创建一定数量的 SensorBundle
        for (i in 0 until maxSize) {
            pool.add(SensorBundle(0.0, 0.0, 0.0, 0L))
        }
    }

    // 获取一个对象
    fun acquire(): SensorBundle {
        synchronized(pool) {
            return if (pool.isNotEmpty()) {
                pool.removeAt(pool.size - 1) // 获取池中的一个对象
            } else {
                // 如果池为空，则创建一个新的对象
                SensorBundle(0.0, 0.0, 0.0, 0L)
            }
        }
    }

    // 归还一个对象
    fun release(bundle: SensorBundle) {
        synchronized(pool) {
            if (pool.size < maxSize) {
                pool.add(bundle) // 归还到池中
            }
        }
    }
}
