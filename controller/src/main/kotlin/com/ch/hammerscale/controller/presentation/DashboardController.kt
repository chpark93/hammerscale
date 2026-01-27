package com.ch.hammerscale.controller.presentation

import com.ch.hammerscale.controller.domain.dto.TestMetricData
import com.ch.hammerscale.controller.domain.service.DashboardService
import com.ch.hammerscale.controller.presentation.dto.ApiResponse
import com.ch.hammerscale.controller.presentation.dto.TestAnalysis
import com.ch.hammerscale.controller.presentation.dto.TestSummary
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
    private val dashboardService: DashboardService,
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
                var lastSentTimestamp: Instant? = null
                var withoutNewDataCount = 0
                val sentTimestamps = mutableSetOf<Instant>() // 전송한 타임스탬프 추적
                
                logger.info("[Dashboard] 🚀 SSE 백그라운드 스레드 시작 - TestID: $testId")
                
                while (true) {
                    try {
                        // 스트림 메트릭 데이터 조회 (비즈니스 로직은 Service에서 처리)
                        val streamData = runBlocking {
                            dashboardService.getStreamMetricsData(
                                testId = testId,
                                sentTimestamps = sentTimestamps,
                                withoutNewDataCount = withoutNewDataCount
                            )
                        }
                        
                        val testStatus = streamData.testStatus
                        val newMetrics = streamData.newMetrics
                        
                        // 종료 조건 체크
                        if (streamData.shouldTerminate) {
                            if (streamData.terminateReason != null) {
                                logger.warn("[Dashboard] ${streamData.terminateReason} - TestID: $testId")
                            }
                            
                            // 종료 전 마지막 메트릭 전송
                            if (newMetrics.isNotEmpty()) {
                                var finalSentCount = 0
                                newMetrics.forEach { metric ->
                                    val data = objectMapper.writeValueAsString(metric)
                                    emitter.send(
                                        SseEmitter.event()
                                            .name("metric")
                                            .data(data)
                                    )
                                    sentTimestamps.add(metric.timestamp)
                                    finalSentCount++
                                }
                                if (finalSentCount > 0) {
                                    logger.info("[Dashboard] 🏁 최종 메트릭 전송 - Count: $finalSentCount")
                                }
                            }
                            
                            // 종료 이벤트 전송 (FINISHED/FAILED 상태일 때만)
                            if (testStatus == "FINISHED" || testStatus == "FAILED") {
                                emitter.send(
                                    SseEmitter.event()
                                        .name("testCompleted")
                                        .data("""{"status": "$testStatus", "testId": "$testId"}""")
                                )
                                logger.info("[Dashboard] SSE 스트림 정상 종료 - TestID: $testId")
                            }
                            
                            break
                        }
                        
                        logger.debug("[Dashboard] 테스트 상태: $testStatus, TestID: $testId")
                        
                        // 새로운 메트릭 전송
                        if (newMetrics.isNotEmpty()) {
                            logger.info("[Dashboard] 📊 메트릭 조회 - TestID: $testId, Count: ${newMetrics.size}, Status: $testStatus")
                            
                            var sentCount = 0
                            newMetrics.forEach { metric ->
                                sentTimestamps.add(metric.timestamp)
                                lastSentTimestamp = metric.timestamp
                                withoutNewDataCount = 0
                                
                                val data = objectMapper.writeValueAsString(metric)
                                emitter.send(
                                    SseEmitter.event()
                                        .name("metric")
                                        .data(data)
                                )
                                sentCount++
                                
                                logger.info("[Dashboard] 📤 메트릭 전송 - Time: ${metric.timestamp}, TPS: ${metric.tps}, Users: ${metric.activeUsers}")
                            }
                            
                            if (sentCount > 0) {
                                logger.info("[Dashboard] ✅ 메트릭 전송 완료 - TestID: $testId, Sent: $sentCount")
                            }
                        } else {
                            // 메트릭 없음 - RUNNING 상태라면 계속 대기
                            if (testStatus == "RUNNING" || testStatus == "READY") {
                                logger.debug("[Dashboard] ⏳ 메트릭 대기 중 - TestID: $testId, Status: $testStatus")
                            } else {
                                withoutNewDataCount++
                            }
                        }
                        
                        Thread.sleep(1000) // 1초로 단축 (더 빠른 업데이트)
                        
                    } catch (e: Exception) {
                        logger.error("[Dashboard] 메트릭 전송 중 에러: ${e.message}")
                        Thread.sleep(2000) // 에러 발생해도 계속 시도
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
    ): ApiResponse<List<TestMetricData>> {
        val metrics = dashboardService.getMetrics(
            testId = testId,
            startTime = startTime,
            endTime = endTime
        )
        return ApiResponse.success(
            data = metrics,
            message = "Metrics retrieved successfully"
        )
    }

    /**
     * 테스트 목록 조회
     */
    @GetMapping("/tests")
    fun getTests(): ApiResponse<List<TestSummary>> {
        // TODO: 모든 TestPlan을 조회
        return ApiResponse.success(
            data = emptyList(),
            message = "Test list retrieved successfully"
        )
    }

    /**
     * 테스트 결과 분석
     */
    @GetMapping("/analysis/{testId}")
    suspend fun getAnalysis(
        @PathVariable testId: String
    ): ApiResponse<TestAnalysis> {
        val analysis = dashboardService.getAnalysis(testId)
        return ApiResponse.success(
            data = analysis,
            message = "Test analysis completed successfully"
        )
    }
}
