package com.ch.hammerscale.controller.presentation

import com.ch.hammerscale.controller.domain.port.out.TestMetricData
import com.ch.hammerscale.controller.domain.port.out.TestMetricRepository
import kotlinx.coroutines.runBlocking
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime
import java.time.ZoneId

@RestController
@RequestMapping("/api/test")
class TestMetricQueryController(
    private val testMetricRepository: TestMetricRepository
) {

    @GetMapping("/{id}/metrics")
    fun getMetrics(
        @PathVariable id: String,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) startTime: LocalDateTime?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) endTime: LocalDateTime?
    ): TestMetricsResponse = runBlocking {
        val startInstant = startTime?.atZone(ZoneId.systemDefault())?.toInstant()
        val endInstant = endTime?.atZone(ZoneId.systemDefault())?.toInstant()

        val metrics = testMetricRepository.getMetrics(
            testId = id,
            startTime = startInstant,
            endTime = endInstant
        )

        TestMetricsResponse(
            testId = id,
            count = metrics.size,
            metrics = metrics.map { it.toResponse() }
        )
    }
}

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

