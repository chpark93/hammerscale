package com.ch.hammerscale.controller.presentation.dto

data class TestSummary(
    val testId: String,
    val title: String,
    val testType: String,
    val status: String,
    val createdAt: String
)

data class TestAnalysis(
    val testId: String,
    val testType: String,
    val status: String,
    val totalDuration: Int,
    val totalRequests: Int,
    val totalErrors: Int,
    val avgTps: Double,
    val maxTps: Int,
    val minTps: Int,
    val avgLatency: Double,
    val maxLatency: Double,
    val p95Latency: Double,
    val p99Latency: Double,
    val avgErrorRate: Double,
    val breakingPoint: BreakingPoint?,
    val tpsSaturation: TpsSaturation?,
    val healthStatusChanges: List<HealthStatusChange>
)

data class BreakingPoint(
    val timestamp: String,
    val activeUsers: Int,
    val tps: Int,
    val avgLatency: Double,
    val errorRate: Double,
    val healthStatus: String
)

data class TpsSaturation(
    val timestamp: String,
    val activeUsers: Int,
    val maxTps: Int,
    val currentTps: Int
)

data class HealthStatusChange(
    val timestamp: String,
    val fromStatus: String,
    val toStatus: String,
    val activeUsers: Int,
    val tps: Int,
    val avgLatency: Double
)
