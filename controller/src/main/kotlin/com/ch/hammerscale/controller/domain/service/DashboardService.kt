package com.ch.hammerscale.controller.domain.service

import com.ch.hammerscale.controller.domain.dto.TestMetricData
import com.ch.hammerscale.controller.domain.model.TestPlan
import com.ch.hammerscale.controller.domain.port.out.TestMetricRepository
import com.ch.hammerscale.controller.domain.port.out.TestPlanRepository
import com.ch.hammerscale.controller.domain.service.dto.StreamMetricsData
import com.ch.hammerscale.controller.presentation.dto.*
import com.ch.hammerscale.controller.presentation.exception.ResourceNotFoundException
import kotlinx.coroutines.runBlocking
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class DashboardService(
    private val testMetricRepository: TestMetricRepository,
    private val testPlanRepository: TestPlanRepository
) {
    /**
     * TestPlan 조회 및 상태 확인
     */
    suspend fun getTestPlanStatus(testId: String): String? {
        val testPlan = testPlanRepository.findById(testId)
        return testPlan?.status?.name
    }

    /**
     * TestPlan 조회
     */
    suspend fun getTestPlan(testId: String): TestPlan? {
        return testPlanRepository.findById(testId)
    }

    /**
     * 새로운 메트릭만 필터링하여 반환 (중복 제거)
     */
    suspend fun getNewMetrics(
        testId: String,
        sentTimestamps: Set<Instant>
    ): List<TestMetricData> {
        val allMetrics = testMetricRepository.getMetrics(
            testId = testId,
            startTime = null,
            endTime = null
        )
        
        return allMetrics.filter { !sentTimestamps.contains(it.timestamp) }
    }

    /**
     * 스트림 메트릭 데이터 조회 (비즈니스 로직 포함)
     * 종료 조건 판단 및 새로운 메트릭 조회를 한 번에 처리
     */
    suspend fun getStreamMetricsData(
        testId: String,
        sentTimestamps: Set<Instant>,
        withoutNewDataCount: Int
    ): StreamMetricsData {
        val testStatus = getTestPlanStatus(testId)
        
        // TestPlan이 없으면 종료
        if (testStatus == null) {
            return StreamMetricsData(
                testStatus = null,
                newMetrics = emptyList(),
                shouldTerminate = true,
                terminateReason = "TestPlan not found"
            )
        }
        
        // FINISHED 또는 FAILED 상태면 종료
        if (testStatus == "FINISHED" || testStatus == "FAILED") {
            val finalMetrics = getNewMetrics(testId, sentTimestamps)
            return StreamMetricsData(
                testStatus = testStatus,
                newMetrics = finalMetrics,
                shouldTerminate = true,
                terminateReason = "Test completed with status: $testStatus"
            )
        }
        
        // 새로운 메트릭 조회
        val newMetrics = getNewMetrics(testId, sentTimestamps)
        
        // 종료 조건 판단: RUNNING 상태가 아닌데 메트릭이 없으면 종료 고려
        val shouldTerminate = if (testStatus != "RUNNING" && testStatus != "READY" && withoutNewDataCount >= 5) {
            true
        } else {
            false
        }
        
        return StreamMetricsData(
            testStatus = testStatus,
            newMetrics = newMetrics,
            shouldTerminate = shouldTerminate,
            terminateReason = if (shouldTerminate) {
                "Abnormal termination - Status: $testStatus, WithoutNewDataCount: $withoutNewDataCount"
            } else {
                null
            }
        )
    }

    /**
     * 전체 메트릭 조회
     */
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

    /**
     * 테스트 결과 분석
     */
    suspend fun getAnalysis(testId: String): TestAnalysis {
        val testPlan = testPlanRepository.findById(testId)
            ?: throw ResourceNotFoundException(
                message = "TestPlan not found",
                resourceType = "TestPlan",
                resourceId = testId
            )

        val metrics = testMetricRepository.getMetrics(testId, null, null)

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
        val durationSeconds = java.time.Duration.between(firstMetric.timestamp, lastMetric.timestamp).seconds

        val totalRequests = metrics.sumOf { it.tps } * 2 // 2초마다 수집
        val totalErrors = metrics.sumOf { it.errorCount }

        val avgTps = metrics.map { it.tps }.average()
        val maxTps = metrics.maxOfOrNull { it.tps } ?: 0
        val minTps = metrics.minOfOrNull { it.tps } ?: 0

        val avgLatency = metrics.map { it.avgLatency }.average()
        val maxLatency = metrics.maxOfOrNull { it.avgLatency } ?: 0.0
        val avgP95 = metrics.map { it.p95Latency }.average()
        val avgP99 = metrics.map { it.p99Latency }.average()

        val avgErrorRate = metrics.map { it.errorRate }.average()

        val breakingPoint = metrics.firstOrNull {
            it.healthStatus == "CRITICAL" || it.healthStatus == "FAILED" 
        }?.let {
            BreakingPoint(
                timestamp = it.timestamp.toString(),
                activeUsers = it.activeUsers,
                tps = it.tps,
                avgLatency = it.avgLatency,
                errorRate = it.errorRate,
                healthStatus = it.healthStatus
            )
        }

        // TPS Saturation 감지
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
