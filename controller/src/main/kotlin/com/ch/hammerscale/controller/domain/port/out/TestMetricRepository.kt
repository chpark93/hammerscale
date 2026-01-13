package com.ch.hammerscale.controller.domain.port.out

import com.project.common.proto.TestStat
import java.time.Instant

interface TestMetricRepository {
    suspend fun saveMetrics(
        stats: List<TestStat>
    )

    suspend fun getMetrics(
        testId: String,
        startTime: Instant? = null,
        endTime: Instant? = null
    ): List<TestMetricData>
}

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

