package com.ch.hammerscale.controller.domain.dto

import java.time.Instant

data class TestMetricData(
    val timestamp: Instant,
    val tps: Int,
    val avgLatency: Double,
    val p50Latency: Double,
    val p95Latency: Double,
    val p99Latency: Double,
    val errorCount: Int,
    val errorRate: Double,
    val activeUsers: Int,
    val healthStatus: String
)
