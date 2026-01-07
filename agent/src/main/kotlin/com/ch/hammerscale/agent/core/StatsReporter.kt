package com.ch.hammerscale.agent.core

import com.project.common.proto.ReportServiceGrpcKt
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flow
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean

class StatsReporter(
    private val collector: WindowedStatsCollector,
    private val reportStub: ReportServiceGrpcKt.ReportServiceCoroutineStub,
    private val onWindowStat: ((stat: com.project.common.proto.TestStat) -> Unit)? = null
) {
    private val logger = LoggerFactory.getLogger(StatsReporter::class.java)
    
    private var reportingJob: Job? = null
    private val isRunning = AtomicBoolean(false)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    /**
     * 통계 전송 시작
     */
    fun start(
        activeUsers: Int
    ) {
        if (isRunning.getAndSet(true)) {
            logger.warn("StatsReporter is already running. Ignoring start request.")
            return
        }
        
        logger.info("[StatsReporter] 통계 전송 시작")
        
        reportingJob = scope.launch {
            try {
                while (isRunning.get()) {
                    delay(1000)
                    
                    if (!isRunning.get()) {
                        break
                    }
                    
                    try {
                        // 1초간의 통계를 가져오고 초기화
                        val stat = collector.snapshotAndReset(activeUsers)

                        // 로컬 보호 로직(에러율/레이턴시 등) 평가 훅
                        try {
                            onWindowStat?.invoke(stat)
                        } catch (e: Exception) {
                            logger.debug("[StatsReporter] onWindowStat hook error: ${e.message}")
                        }
                        
                        // 통계가 있는 경우에만 전송
                        if (stat.requestsPerSecond > 0) {
                            // gRPC streamStats를 통해 전송
                            reportStub.streamStats(flow {
                                emit(stat)
                            })
                            
                            logger.debug(
                                "[StatsReporter] 통계 전송 완료 - " +
                                "TPS: ${stat.requestsPerSecond}, " +
                                "AvgLatency: ${stat.avgLatencyMs}ms, " +
                                "Errors: ${stat.errorCount}"
                            )
                        }
                    } catch (e: Exception) {
                        logger.error("[StatsReporter] 통계 전송 실패: ${e.message}", e)
                        // 전송 실패해도 계속 진행
                    }
                }
            } catch (e: CancellationException) {
                logger.info("[StatsReporter] 통계 전송 취소됨")
                throw e
            } catch (e: Exception) {
                logger.error("[StatsReporter] 통계 전송 중 오류 발생: ${e.message}", e)
            } finally {
                isRunning.set(false)
            }
        }
    }
    
    /**
     * 통계 전송 중지
     */
    fun stop() {
        if (!isRunning.getAndSet(false)) {
            logger.warn("StatsReporter is not running. Ignoring stop request.")
            return
        }
        
        logger.info("[StatsReporter] 통계 전송 중지 요청")
        
        reportingJob?.cancel()
        reportingJob = null
        
        try {
            val finalStat = collector.getCurrentSnapshot(0)
            if (finalStat.requestsPerSecond > 0) {
                runBlocking {
                    try {
                        reportStub.streamStats(flow {
                            emit(finalStat)
                        })
                        logger.info("[StatsReporter] 최종 통계 전송 완료")
                    } catch (e: Exception) {
                        logger.warn("[StatsReporter] 최종 통계 전송 실패: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            logger.warn("[StatsReporter] 최종 통계 수집 실패: ${e.message}")
        }
    }
}

