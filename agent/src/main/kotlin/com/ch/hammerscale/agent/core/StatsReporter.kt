package com.ch.hammerscale.agent.core

import com.project.common.proto.ReportServiceGrpcKt
import com.project.common.proto.TestStat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flow
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean

class StatsReporter(
    private val collector: WindowedStatsCollector,
    private val reportStub: ReportServiceGrpcKt.ReportServiceCoroutineStub,
    private val getActiveUsers: () -> Int,
    private val onWindowStat: ((stat: TestStat) -> Unit)? = null
) {
    private val logger = LoggerFactory.getLogger(StatsReporter::class.java)
    
    private var reportingJob: Job? = null
    private val isRunning = AtomicBoolean(false)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // 상태 변화 추적
    @Volatile
    private var previousHealthStatus: String? = null
    
    // 이전 stat 추적 (TPS 변화율 계산용)
    @Volatile
    private var previousStat: TestStat? = null
    
    // Breaking Point 추적
    @Volatile
    private var breakingPointDetected = false
    @Volatile
    private var breakingPointUsers: Int? = null
    @Volatile
    private var breakingPointStatus: String? = null
    @Volatile
    private var tpsSaturationDetected = false
    
    /**
     * 통계 전송 시작
     */
    fun start() {
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
                        // 1초간의 통계를 가져오고 초기화 (실시간 활성 사용자 수 전달)
                        val stat = collector.snapshotAndReset(getActiveUsers())

                        try {
                            onWindowStat?.invoke(stat)
                        } catch (e: Exception) {
                            logger.debug("[StatsReporter] onWindowStat hook error: ${e.message}")
                        }
                        
                        // 상태 변화 감지 및 로그 출력
                        if (stat.requestsPerSecond > 0) {
                            checkHealthStatusChange(stat)
                            checkTPSSaturation(stat)
                            
                            // 현재 stat -> 이전 stat으로 저장
                            previousStat = stat
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
                                "Errors: ${stat.errorCount}, " +
                                "Status: ${stat.healthStatus}"
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
            val finalStat = collector.getCurrentSnapshot(getActiveUsers())
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
    
    /**
     * Health Status 변화 감지 및 로그 출력
     */
    private fun checkHealthStatusChange(
        stat: TestStat
    ) {
        val currentStatus = stat.healthStatus
        val prevStatus = previousHealthStatus
        
        // Breaking Point 감지 (CRITICAL 또는 FAILED로 처음 진입하는 시점)
        if (!breakingPointDetected && (currentStatus == "CRITICAL" || currentStatus == "FAILED")) {
            breakingPointDetected = true
            breakingPointUsers = stat.activeUsers
            breakingPointStatus = currentStatus
            
            logger.error(
                "💥 [Breaking Point 감지!] " +
                "상태: $currentStatus | " +
                "사용자 수: ${stat.activeUsers}명 | " +
                "TPS: ${stat.requestsPerSecond} | " +
                "Latency: ${"%.1f".format(stat.avgLatencyMs)}ms | " +
                "ErrorRate: ${"%.2f".format(stat.errorRate * 100)}%"
            )
        }
        
        if (prevStatus != currentStatus) {
            val emoji = when (currentStatus) {
                "HEALTHY" -> "✅"
                "DEGRADED" -> "⚠️"
                "CRITICAL" -> "🔥"
                "FAILED" -> "❌"
                else -> "❓"
            }
            
            val statusDescription = when (currentStatus) {
                "HEALTHY" -> "정상"
                "DEGRADED" -> "성능 저하"
                "CRITICAL" -> "임계 상태"
                "FAILED" -> "실패"
                else -> "알 수 없음"
            }
            
            if (prevStatus == null) {
                logger.info(
                    "$emoji [상태] 초기 상태: $statusDescription | " +
                    "Users: ${stat.activeUsers}, " +
                    "TPS: ${stat.requestsPerSecond}, " +
                    "Latency: ${"%.1f".format(stat.avgLatencyMs)}ms, " +
                    "ErrorRate: ${"%.2f".format(stat.errorRate * 100)}%"
                )
            } else {
                val prevEmoji = when (prevStatus) {
                    "HEALTHY" -> "✅"
                    "DEGRADED" -> "⚠️"
                    "CRITICAL" -> "🔥"
                    "FAILED" -> "❌"
                    else -> "❓"
                }
                
                logger.warn(
                    "$prevEmoji ➜ $emoji [상태 변화] $prevStatus → $currentStatus ($statusDescription) | " +
                    "Users: ${stat.activeUsers}, " +
                    "TPS: ${stat.requestsPerSecond}, " +
                    "Latency: ${"%.1f".format(stat.avgLatencyMs)}ms, " +
                    "ErrorRate: ${"%.2f".format(stat.errorRate * 100)}%"
                )
            }
            
            previousHealthStatus = currentStatus
        }
    }
    
    /**
     * TPS Saturation 감지
     */
    private fun checkTPSSaturation(
        stat: TestStat
    ) {
        val prev = previousStat ?: return
        
        // 요청이 너무 적으면 판단X
        if (stat.requestsPerSecond < 10 || prev.requestsPerSecond < 10) {
            return
        }
        
        val currentTps = stat.requestsPerSecond
        val currentUsers = stat.activeUsers
        val prevTps = prev.requestsPerSecond
        val prevUsers = prev.activeUsers
        
        // 사용자 수가 증가했는지 확인
        if (currentUsers <= prevUsers) {
            return
        }
        
        // 사용자당 처리량 계산
        val currentTpsPerUser = currentTps.toDouble() / currentUsers
        val prevTpsPerUser = prevTps.toDouble() / prevUsers
        
        val tpsChange = currentTps - prevTps
        val userChange = currentUsers - prevUsers
        val tpsPerUserChange = currentTpsPerUser - prevTpsPerUser
        val tpsPerUserChangePercent = (tpsPerUserChange / prevTpsPerUser) * 100
        
        // TPS Saturation 감지 조건
        val absoluteTpsDecreased = tpsChange < 0  // 절대 TPS 감소
        val tpsPerUserDecreased = tpsPerUserChangePercent < -15.0  // 사용자당 처리량 15% 이상 감소
        val tpsStagnant = tpsChange < (userChange * 0.5)  // TPS 증가율이 사용자 증가율의 50% 미만
        
        if (!tpsSaturationDetected && (absoluteTpsDecreased || tpsPerUserDecreased)) {
            tpsSaturationDetected = true
            
            val emoji = if (absoluteTpsDecreased) "📉" else "⚠️"
            
            logger.error(
                "$emoji [TPS Saturation 감지!] " +
                "사용자: ${prevUsers}명 → ${currentUsers}명 (+${userChange}명) | " +
                "TPS: $prevTps → $currentTps (${if (tpsChange >= 0) "+" else ""}${tpsChange}) | " +
                "사용자당 TPS: ${"%.2f".format(prevTpsPerUser)} → ${"%.2f".format(currentTpsPerUser)} " +
                "(${"%.1f".format(tpsPerUserChangePercent)}%) | " +
                "Latency: ${"%.1f".format(stat.avgLatencyMs)}ms | " +
                "ErrorRate: ${"%.2f".format(stat.errorRate * 100)}%"
            )
        } else if (tpsStagnant && !absoluteTpsDecreased && currentUsers > 50) {
            // TPS 정체 경고 (한 번만)
            logger.warn(
                "⚠️ [TPS 정체 경고] " +
                "사용자: ${prevUsers}명 → ${currentUsers}명 (+${userChange}명) | " +
                "TPS: $prevTps → $currentTps (+${tpsChange}) | " +
                "TPS 증가율이 사용자 증가 대비 낮음 (포화 징후)"
            )
        }
    }
    
    /**
     * Breaking Point 정보 조회
     */
    fun getBreakingPoint(): BreakingPointInfo? {
        return if (breakingPointDetected) {
            BreakingPointInfo(
                users = breakingPointUsers ?: 0,
                status = breakingPointStatus ?: "UNKNOWN",
                tpsSaturated = tpsSaturationDetected
            )
        } else null
    }
}

data class BreakingPointInfo(
    val users: Int,
    val status: String,
    val tpsSaturated: Boolean
)

