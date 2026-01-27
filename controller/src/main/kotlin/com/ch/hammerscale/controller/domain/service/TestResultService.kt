package com.ch.hammerscale.controller.domain.service

import com.ch.hammerscale.controller.domain.port.out.TestMetricRepository
import com.ch.hammerscale.controller.domain.port.out.TestPlanRepository
import com.ch.hammerscale.controller.presentation.dto.*
import com.ch.hammerscale.controller.presentation.dto.format
import com.ch.hammerscale.controller.presentation.exception.ResourceNotFoundException
import org.springframework.stereotype.Service

@Service
class TestResultService(
    private val testPlanRepository: TestPlanRepository,
    private val testMetricRepository: TestMetricRepository
) {
    /**
     * 상세한 테스트 결과 반환
     * 테스트가 완료된 경우에만 결과를 반환
     */
    suspend fun getTestResult(
        testId: String
    ): DetailedTestResult {
        val testPlan = testPlanRepository.findById(testId)
            ?: throw ResourceNotFoundException(
                message = "Test not found",
                resourceType = "Test",
                resourceId = testId
            )

        // 테스트가 완료되지 않은 경우 에러 반환
        if (testPlan.status.name != "FINISHED") {
            throw IllegalStateException("Test is not finished yet. Current status: ${testPlan.status.name}")
        }

        val metrics = testMetricRepository.getMetrics(testId, null, null)

        if (metrics.isEmpty()) {
            throw IllegalStateException("Test finished but no metrics collected. Please check if the test actually ran.")
        }

        val firstMetric = metrics.first()
        val lastMetric = metrics.last()
        val durationSeconds = java.time.Duration.between(firstMetric.timestamp, lastMetric.timestamp).seconds.toInt()
            .coerceAtLeast(1) // 최소 1초

        // HTTP 요청 메트릭
        val allTps = metrics.map { it.tps }
        val totalRequests = metrics.sumOf { it.tps }
        val totalErrors = metrics.sumOf { it.errorCount }

        // Latency 메트릭
        val allAvgLatencies = metrics.map { it.avgLatency }
        val allP50 = metrics.map { it.p50Latency }
        val allP95 = metrics.map { it.p95Latency }
        val allP99 = metrics.map { it.p99Latency }

        // 에러율
        val allErrorRates = metrics.map { it.errorRate }

        // Breaking Point
        val breakingPoint = metrics.firstOrNull { 
            it.healthStatus == "CRITICAL" || it.healthStatus == "FAILED" 
        }

        // TPS Saturation
        var maxTps = 0
        var tpsSaturationPoint: Int? = null
        for (i in 1 until metrics.size) {
            val currentTps = metrics[i].tps
            if (currentTps > maxTps) {
                maxTps = currentTps
            }
            if (currentTps < maxTps * 0.9 && tpsSaturationPoint == null) {
                tpsSaturationPoint = metrics[i].activeUsers
            }
        }

        return DetailedTestResult(
            testId = testId,
            testType = testPlan.config.testType.name,
            status = testPlan.status.name,
            createdAt = testPlan.createdAt.toString(),
            duration = durationSeconds,
            metrics = TestMetrics(
                httpReqs = HttpReqsMetric(
                    count = totalRequests,
                    rate = if (durationSeconds > 0) totalRequests.toDouble() / durationSeconds else 0.0,
                    failed = totalErrors,
                    successful = totalRequests - totalErrors
                ),
                httpReqDuration = HttpReqDurationMetric(
                    avg = allAvgLatencies.average(),
                    min = allAvgLatencies.minOrNull() ?: 0.0,
                    max = allAvgLatencies.maxOrNull() ?: 0.0,
                    med = allP50.average(),
                    p90 = allP95.average() * 0.95,
                    p95 = allP95.average(),
                    p99 = allP99.average()
                ),
                httpReqFailed = HttpReqFailedMetric(
                    count = totalErrors,
                    rate = if (totalRequests > 0) totalErrors.toDouble() / totalRequests else 0.0
                ),
                vus = VusMetric(
                    value = lastMetric.activeUsers,
                    min = metrics.minOfOrNull { it.activeUsers } ?: 0,
                    max = metrics.maxOfOrNull { it.activeUsers } ?: 0
                ),
                tps = TpsMetric(
                    value = lastMetric.tps,
                    avg = allTps.average(),
                    min = allTps.minOrNull() ?: 0,
                    max = allTps.maxOrNull() ?: 0
                ),
                errorRate = ErrorRateMetric(
                    value = lastMetric.errorRate,
                    avg = allErrorRates.average(),
                    max = allErrorRates.maxOrNull() ?: 0.0
                )
            ),
            summary = TestResultSummary(
                totalDuration = "${durationSeconds}s",
                totalRequests = totalRequests,
                successfulRequests = totalRequests - totalErrors,
                failedRequests = totalErrors,
                requestsPerSecond = if (durationSeconds > 0) totalRequests.toDouble() / durationSeconds else 0.0,
                avgLatency = "${allAvgLatencies.average().toInt()}ms",
                p95Latency = "${allP95.average().toInt()}ms",
                p99Latency = "${allP99.average().toInt()}ms",
                errorRate = "${(allErrorRates.average() * 100).format(2)}%",
                healthStatus = lastMetric.healthStatus
            ),
            breakingPoint = breakingPoint?.let {
                TestBreakingPoint(
                    detected = true,
                    activeUsers = it.activeUsers,
                    healthStatus = it.healthStatus,
                    timestamp = it.timestamp.toString(),
                    tps = it.tps,
                    latency = it.avgLatency,
                    errorRate = it.errorRate
                )
            } ?: TestBreakingPoint(false, null, null, null, null, null, null),
            tpsSaturation = if (tpsSaturationPoint != null) {
                TestTpsSaturation(
                    detected = true,
                    activeUsers = tpsSaturationPoint,
                    maxTps = maxTps,
                    timestamp = metrics.firstOrNull { it.activeUsers == tpsSaturationPoint }?.timestamp?.toString()
                )
            } else {
                TestTpsSaturation(false, null, maxTps, null)
            },
            thresholds = evaluateThresholds(testPlan.config.testType.name, allAvgLatencies.average(), allErrorRates.average(), maxTps)
        )
    }

    private fun evaluateThresholds(
        testType: String,
        avgLatency: Double,
        avgErrorRate: Double,
        maxTps: Int
    ): TestThresholds {
        val results = mutableMapOf<String, ThresholdResult>()

        results["http_req_duration_p95"] = ThresholdResult(
            threshold = "< 500ms",
            passed = avgLatency < 500.0,
            value = "${avgLatency.toInt()}ms"
        )

        results["http_req_failed"] = ThresholdResult(
            threshold = "< 1%",
            passed = avgErrorRate < 0.01,
            value = "${(avgErrorRate * 100).format(2)}%"
        )

        val minTps = when (testType) {
            "LOAD", "SOAK" -> 100
            "STRESS" -> 200
            "SPIKE" -> 500
            else -> 100
        }

        results["tps"] = ThresholdResult(
            threshold = "> $minTps",
            passed = maxTps > minTps,
            value = "$maxTps"
        )

        val allPassed = results.values.all { it.passed }

        return TestThresholds(
            passed = allPassed,
            failed = !allPassed,
            results = results
        )
    }
}
