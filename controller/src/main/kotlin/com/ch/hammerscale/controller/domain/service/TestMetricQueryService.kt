package com.ch.hammerscale.controller.domain.service

import com.ch.hammerscale.controller.domain.dto.TestMetricData
import com.ch.hammerscale.controller.domain.port.out.TestMetricRepository
import com.ch.hammerscale.controller.presentation.dto.MetricDataResponse
import com.ch.hammerscale.controller.presentation.dto.TestMetricsResponse
import kotlinx.coroutines.runBlocking
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.ZoneId

@Service
class TestMetricQueryService(
    private val testMetricRepository: TestMetricRepository
) {
    suspend fun getMetrics(
        testId: String,
        startTime: LocalDateTime?,
        endTime: LocalDateTime?
    ): TestMetricsResponse {
        val startInstant = startTime?.atZone(ZoneId.systemDefault())?.toInstant()
        val endInstant = endTime?.atZone(ZoneId.systemDefault())?.toInstant()

        val metrics = testMetricRepository.getMetrics(
            testId = testId,
            startTime = startInstant,
            endTime = endInstant
        )

        return TestMetricsResponse(
            testId = testId,
            count = metrics.size,
            metrics = metrics.map { it.toResponse() }
        )
    }
    
    private fun TestMetricData.toResponse(): MetricDataResponse {
        return MetricDataResponse(
            timestamp = this.timestamp.toString(),
            tps = this.tps,
            avgLatency = this.avgLatency,
            p50Latency = this.p50Latency,
            p95Latency = this.p95Latency,
            p99Latency = this.p99Latency,
            errorCount = this.errorCount,
            errorRate = this.errorRate,
            activeUsers = this.activeUsers,
            healthStatus = this.healthStatus
        )
    }
}
