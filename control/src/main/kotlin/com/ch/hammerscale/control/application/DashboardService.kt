package com.ch.hammerscale.control.application

import com.ch.hammerscale.control.application.dto.StreamMetricsData
import com.ch.hammerscale.control.domain.dto.TestMetricData
import com.ch.hammerscale.control.domain.model.TestPlan
import com.ch.hammerscale.control.domain.port.out.TestMetricRepository
import com.ch.hammerscale.control.domain.port.out.TestPlanRepository
import com.ch.hammerscale.control.presentation.test.dto.BreakingPoint
import com.ch.hammerscale.control.presentation.test.dto.HealthStatusChange
import com.ch.hammerscale.control.presentation.test.dto.TestAnalysis
import com.ch.hammerscale.control.presentation.test.dto.TpsSaturation
import com.project.common.exception.ResourceNotFoundException
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

@Service
class DashboardService(
    private val testMetricRepository: TestMetricRepository,
    private val testPlanRepository: TestPlanRepository
) {
    suspend fun getTestPlanStatus(
        testId: String
    ): String? = testPlanRepository.findById(
        id = testId
    )?.status?.name

    suspend fun getTestPlan(
        testId: String
    ): TestPlan? = testPlanRepository.findById(
        id = testId
    )

    suspend fun getNewMetrics(
        testId: String,
        sentTimestamps: Set<Instant>
    ): List<TestMetricData> {
        val allMetrics = testMetricRepository.getMetrics(
            testId = testId,
            startTime = null,
            endTime = null
        )

        return allMetrics.filter {
            !sentTimestamps.contains(it.timestamp)
        }
    }

    suspend fun getStreamMetricsData(
        testId: String,
        sentTimestamps: Set<Instant>,
        withoutNewDataCount: Int
    ): StreamMetricsData {
        val testStatus = getTestPlanStatus(
            testId = testId
        ) ?: return StreamMetricsData(
            testStatus = null,
            newMetrics = emptyList(),
            shouldTerminate = true,
            terminateReason = "TestPlan not found"
        )

        if (testStatus == "FINISHED" || testStatus == "FAILED") {
            val finalMetrics = getNewMetrics(
                testId = testId,
                sentTimestamps = sentTimestamps
            )

            return StreamMetricsData(
                testStatus = testStatus,
                newMetrics = finalMetrics,
                shouldTerminate = true,
                terminateReason = "Test completed with status: $testStatus"
            )
        }

        val newMetrics = getNewMetrics(
            testId = testId,
            sentTimestamps = sentTimestamps
        )

        val shouldTerminate = testStatus != "RUNNING" && testStatus != "READY" && withoutNewDataCount >= 5

        return StreamMetricsData(
            testStatus = testStatus,
            newMetrics = newMetrics,
            shouldTerminate = shouldTerminate,
            terminateReason = if (shouldTerminate) "Abnormal termination - Status: $testStatus, WithoutNewDataCount: $withoutNewDataCount" else null
        )
    }

    suspend fun getMetrics(
        testId: String,
        startTime: String?,
        endTime: String?
    ): List<TestMetricData> {
        val start = startTime?.let { Instant.parse(it) }
        val end = endTime?.let { Instant.parse(it) }

        return testMetricRepository.getMetrics(
            testId = testId,
            startTime = start,
            endTime = end
        )
    }

    suspend fun getAnalysis(
        testId: String
    ): TestAnalysis {
        val testPlan = testPlanRepository.findById(
            id = testId
        ) ?: throw ResourceNotFoundException(
            message = "TestPlan not found",
            resourceType = "TestPlan",
            resourceId = testId
        )

        val metrics = testMetricRepository.getMetrics(
            testId = testId,
            startTime = null,
            endTime = null
        )

        if (metrics.isEmpty()) {
            return TestAnalysis(
                testId = testId,
                testType = testPlan.config.testType.name,
                status = testPlan.status.name,
                totalDuration = 0,
                totalRequests = 0,
                totalErrors = 0,
                avgTps = 0.0,
                maxTps = 0,
                minTps = 0,
                avgLatency = 0.0,
                maxLatency = 0.0,
                p95Latency = 0.0,
                p99Latency = 0.0,
                avgErrorRate = 0.0,
                breakingPoint = null,
                tpsSaturation = null,
                healthStatusChanges = emptyList()
            )
        }

        val firstMetric = metrics.first()
        val lastMetric = metrics.last()
        val durationSeconds = Duration.between(firstMetric.timestamp, lastMetric.timestamp).seconds
        val totalRequests = metrics.sumOf { it.tps } * 2
        val totalErrors = metrics.sumOf { it.errorCount }
        val avgTps = metrics.map { it.tps }.average()
        val maxTps = metrics.maxOfOrNull { it.tps } ?: 0
        val minTps = metrics.minOfOrNull { it.tps } ?: 0
        val avgLatency = metrics.map { it.avgLatency }.average()
        val maxLatency = metrics.maxOfOrNull { it.avgLatency } ?: 0.0
        val avgP95 = metrics.map { it.p95Latency }.average()
        val avgP99 = metrics.map { it.p99Latency }.average()
        val avgErrorRate = metrics.map { it.errorRate }.average()
        val breakingPoint = metrics.firstOrNull { it.healthStatus == "CRITICAL" || it.healthStatus == "FAILED" }?.let {
            BreakingPoint(
                timestamp = it.timestamp.toString(),
                activeUsers = it.activeUsers,
                tps = it.tps,
                avgLatency = it.avgLatency,
                errorRate = it.errorRate,
                healthStatus = it.healthStatus
            )
        }
        var tpsSaturation: TpsSaturation? = null
        for (i in 5 until metrics.size) {
            val currentTps = metrics[i].tps
            val previousTps = metrics.subList(i - 5, i).map { it.tps }.average()
            if (currentTps < previousTps * 0.9 && metrics[i].activeUsers > metrics[i - 1].activeUsers) {
                tpsSaturation = TpsSaturation(
                    timestamp = metrics[i].timestamp.toString(),
                    activeUsers = metrics[i].activeUsers,
                    maxTps = previousTps.toInt(),
                    currentTps = currentTps
                )

                break
            }
        }

        val healthChanges = mutableListOf<HealthStatusChange>()
        var lastStatus = metrics.first().healthStatus
        for (metric in metrics) {
            if (metric.healthStatus != lastStatus) {
                healthChanges.add(
                    HealthStatusChange(
                        timestamp = metric.timestamp.toString(),
                        fromStatus = lastStatus,
                        toStatus = metric.healthStatus,
                        activeUsers = metric.activeUsers,
                        tps = metric.tps,
                        avgLatency = metric.avgLatency
                    )
                )
                lastStatus = metric.healthStatus
            }
        }

        return TestAnalysis(
            testId = testId,
            testType = testPlan.config.testType.name,
            status = testPlan.status.name,
            totalDuration = durationSeconds.toInt(),
            totalRequests = totalRequests,
            totalErrors = totalErrors,
            avgTps = avgTps,
            maxTps = maxTps,
            minTps = minTps,
            avgLatency = avgLatency,
            maxLatency = maxLatency,
            p95Latency = avgP95,
            p99Latency = avgP99,
            avgErrorRate = avgErrorRate,
            breakingPoint = breakingPoint,
            tpsSaturation = tpsSaturation,
            healthStatusChanges = healthChanges
        )
    }
}
