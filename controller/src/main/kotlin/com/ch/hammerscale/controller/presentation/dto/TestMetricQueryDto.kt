package com.ch.hammerscale.controller.presentation.dto

data class TestMetricsResponse(
    val testId: String,
    val count: Int,
    val metrics: List<MetricDataResponse>
)

data class MetricDataResponse(
    val timestamp: String,
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
