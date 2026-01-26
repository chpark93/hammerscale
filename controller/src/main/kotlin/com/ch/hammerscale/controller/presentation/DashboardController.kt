package com.ch.hammerscale.controller.presentation

import com.ch.hammerscale.controller.domain.port.out.TestMetricData
import com.ch.hammerscale.controller.domain.port.out.TestMetricRepository
import com.ch.hammerscale.controller.domain.port.out.TestPlanRepository
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@RestController
@RequestMapping("/api/dashboard")
class DashboardController(
    private val testMetricRepository: TestMetricRepository,
    private val testPlanRepository: TestPlanRepository,
    private val objectMapper: ObjectMapper
) {

    private val logger = LoggerFactory.getLogger(DashboardController::class.java)
    private val emitters = ConcurrentHashMap<String, MutableSet<SseEmitter>>()

    /**
     * SSE 스트림: 실시간 메트릭 전송
     */
    @GetMapping("/stream/{testId}", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun streamMetrics(
        @PathVariable testId: String
    ): SseEmitter {
        val emitter = SseEmitter(0L)
        
        emitters.computeIfAbsent(testId) { ConcurrentHashMap.newKeySet() }.add(emitter)
        
        logger.info("[Dashboard] SSE 연결 시작 - TestID: $testId")
        
        emitter.onCompletion {
            emitters[testId]?.remove(emitter)
            logger.info("[Dashboard] SSE 연결 종료 (완료) - TestID: $testId")
        }
        
        emitter.onTimeout {
            emitters[testId]?.remove(emitter)
            logger.warn("[Dashboard] SSE 연결 종료 (타임아웃) - TestID: $testId")
        }
        
        emitter.onError {
            emitters[testId]?.remove(emitter)
            logger.error("[Dashboard] SSE 연결 종료 (에러) - TestID: $testId", it)
        }
        
        // 백그라운드 스레드에서 주기적으로 데이터 전송
        Thread {
            try {
                var lastTimestamp: Instant? = null
                var withoutNewDataCount = 0
                val maxWithoutNewDataCount = 10 // 10번 연속 새 데이터 없으면 종료
                
                while (true) {
                    try {
                        // TestPlan 상태 체크
                        val testPlan = testPlanRepository.findById(testId)
                        if (testPlan != null && (testPlan.status.name == "FINISHED" || testPlan.status.name == "FAILED")) {
                            logger.info("[Dashboard] 테스트 종료 감지 - TestID: $testId, Status: ${testPlan.status}")
                            
                            // 마지막 메트릭 전송
                            val finalMetrics = runBlocking {
                                testMetricRepository.getMetrics(
                                    testId = testId,
                                    startTime = lastTimestamp,
                                    endTime = null
                                )
                            }
                            
                            if (finalMetrics.isNotEmpty()) {
                                val latestMetric = finalMetrics.last()
                                val data = objectMapper.writeValueAsString(latestMetric)
                                emitter.send(
                                    SseEmitter.event()
                                        .name("metric")
                                        .data(data)
                                )
                            }
                            
                            // 종료 이벤트 전송
                            emitter.send(
                                SseEmitter.event()
                                    .name("testCompleted")
                                    .data("""{"status": "${testPlan.status}", "testId": "$testId"}""")
                            )
                            
                            logger.info("[Dashboard] SSE 스트림 정상 종료 - TestID: $testId")
                            break
                        }
                        
                        val metrics = runBlocking {
                            testMetricRepository.getMetrics(
                                testId = testId,
                                startTime = lastTimestamp ?: Instant.now().minusSeconds(60),
                                endTime = null
                            )
                        }
                        
                        if (metrics.isNotEmpty()) {
                            val latestMetric = metrics.last()
                            
                            // 새로운 데이터인지 확인
                            if (lastTimestamp == null || latestMetric.timestamp.isAfter(lastTimestamp)) {
                                lastTimestamp = latestMetric.timestamp
                                withoutNewDataCount = 0
                                
                                val data = objectMapper.writeValueAsString(latestMetric)
                                emitter.send(
                                    SseEmitter.event()
                                        .name("metric")
                                        .data(data)
                                )
                                
                                logger.debug("[Dashboard] 메트릭 전송 - TestID: $testId, TPS: ${latestMetric.tps}")
                            } else {
                                withoutNewDataCount++
                                logger.debug("[Dashboard] 새 데이터 없음 - TestID: $testId, Count: $withoutNewDataCount")
                                
                                if (withoutNewDataCount >= maxWithoutNewDataCount) {
                                    logger.warn("[Dashboard] 새 데이터 없음 (${withoutNewDataCount}회) - 스트림 종료 - TestID: $testId")
                                    break
                                }
                            }
                        } else {
                            withoutNewDataCount++
                            if (withoutNewDataCount >= maxWithoutNewDataCount) {
                                logger.warn("[Dashboard] 메트릭 없음 (${withoutNewDataCount}회) - 스트림 종료 - TestID: $testId")
                                break
                            }
                        }
                        
                        Thread.sleep(2000)
                        
                    } catch (e: Exception) {
                        logger.error("[Dashboard] 메트릭 전송 중 에러: ${e.message}")
                        break
                    }
                }
            } catch (e: Exception) {
                logger.error("[Dashboard] SSE 스트림 에러: ${e.message}", e)
            } finally {
                emitter.complete()
                emitters[testId]?.remove(emitter)
            }
        }.start()
        
        return emitter
    }

    /**
     * 전체 메트릭 조회
     */
    @GetMapping("/metrics/{testId}")
    suspend fun getMetrics(
        @PathVariable testId: String,
        @RequestParam(required = false) startTime: String?,
        @RequestParam(required = false) endTime: String?
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
     * 테스트 목록 조회
     */
    @GetMapping("/tests")
    fun getTests(): List<TestSummary> {
        // TODO: 모든 TestPlan을 조회
        return emptyList()
    }

    /**
     * 테스트 결과 분석
     */
    @GetMapping("/analysis/{testId}")
    suspend fun getAnalysis(
        @PathVariable testId: String
    ): TestAnalysis {
        val testPlan = testPlanRepository.findById(testId)
            ?: throw IllegalArgumentException("TestPlan not found: $testId")

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
    val totalDuration: Int, // 초
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

