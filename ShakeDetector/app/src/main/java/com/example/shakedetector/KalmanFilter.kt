package com.example.shakedetector

class KalmanFilter {
    private var Q = 0.1 // Process noise covariance
    private var R = 0.1 // Measurement noise covariance
    private var x = 0.0 // Estimated value
    private var P = 1.0 // Estimation error covariance
    private var K = 0.0 // Kalman gain

    fun update(measurement: Double): Double {
        // Calculate Kalman gain
        K = P / (P + R)

        // Update estimated value
        x += K * (measurement - x)

        // Update estimation error covariance
        P = (1 - K) * P + Math.abs(x) * Q

        return x
    }
}