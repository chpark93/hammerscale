package com.ch.hammerscale.controller.infrastructure.grpc

import com.ch.hammerscale.controller.domain.port.out.TestMetricRepository
import com.ch.hammerscale.controller.domain.port.out.TestPlanRepository
import com.project.common.proto.Ack
import com.project.common.proto.ReportServiceGrpcKt
import com.project.common.proto.TestStat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.catch
import net.devh.boot.grpc.server.service.GrpcService
import org.slf4j.LoggerFactory

@GrpcService
class ReportServiceGrpcImpl(
    private val testMetricRepository: TestMetricRepository,
    private val testPlanRepository: TestPlanRepository
) : ReportServiceGrpcKt.ReportServiceCoroutineImplBase() {

    private val logger = LoggerFactory.getLogger(ReportServiceGrpcImpl::class.java)

    private companion object {
        const val BATCH_SIZE = 10
    }

    override suspend fun streamStats(
        requests: Flow<TestStat>
    ): Ack {
        logger.info("[Report] 통계 스트림 수신 시작")

        var testId: String? = null

        try {
            var totalProcessed = 0
            var totalSaved = 0
            val batch = mutableListOf<TestStat>()

            requests
                .buffer()
                .catch { e ->
                    logger.error("[Report] 스트림 처리 중 오류 발생: ${e.message}", e)
                    throw e
                }
                .collect { stat ->
                    if (testId == null) {
                        testId = stat.testId
                    }

                    val emoji = when (stat.healthStatus) {
                        "HEALTHY" -> "✅"
                        "DEGRADED" -> "⚠️"
                        "CRITICAL" -> "🔥"
                        "FAILED" -> "❌"
                        else -> "❓"
                    }
                    
                    logger.info(
                        "$emoji [Report] TestID: ${stat.testId} | " +
                        "Status: ${stat.healthStatus} | " +
                        "TPS: ${stat.requestsPerSecond} | " +
                        "Latency: Avg=${String.format("%.1f", stat.avgLatencyMs)}ms " +
                        "p50=${String.format("%.1f", stat.p50LatencyMs)}ms " +
                        "p95=${String.format("%.1f", stat.p95LatencyMs)}ms " +
                        "p99=${String.format("%.1f", stat.p99LatencyMs)}ms | " +
                        "Errors: ${stat.errorCount} (${String.format("%.2f", stat.errorRate * 100)}%) | " +
                        "Active Users: ${stat.activeUsers}"
                    )

                    totalProcessed++
                    batch.add(stat)

                    if (batch.size >= BATCH_SIZE) {
                        try {
                            testMetricRepository.saveMetrics(batch.toList())
                            totalSaved += batch.size
                            logger.debug("[Report] 배치 저장 완료 - Size: ${batch.size}, Total Saved: $totalSaved")
                            batch.clear()
                        } catch (e: Exception) {
                            // 배치 저장 실패해도 스트림은 계속 진행
                            logger.error(
                                "[Report] 배치 저장 실패 (스트림 계속 진행) - " +
                                "Batch Size: ${batch.size}, Error: ${e.message}",
                                e
                            )

                            // 실패해도 배치는 초기화
                            batch.clear()
                        }
                    }
                }

            // 남은 메트릭 저장
            if (batch.isNotEmpty()) {
                try {
                    testMetricRepository.saveMetrics(batch)
                    totalSaved += batch.size
                    logger.debug("[Report] 마지막 배치 저장 완료 - Size: ${batch.size}, Total Saved: $totalSaved")
                } catch (e: Exception) {
                    logger.error(
                        "[Report] 마지막 배치 저장 실패 - " +
                        "Batch Size: ${batch.size}, Error: ${e.message}",
                        e
                    )
                }
            }

            logger.info(
                "[Report] 통계 스트림 수신 완료 - " +
                "Total Processed: $totalProcessed, Total Saved: $totalSaved"
            )

            if (testId != null) {
                updateTestStatusToFinished(
                    testId = testId
                )
            }

            return Ack.newBuilder()
                .setSuccess(true)
                .setMessage("Statistics stream processed successfully. Processed: $totalProcessed, Saved: $totalSaved")
                .build()

        } catch (e: Exception) {
            logger.error("[Report] 통계 스트림 처리 중 치명적 오류 발생: ${e.message}", e)

            return Ack.newBuilder()
                .setSuccess(false)
                .setMessage("Failed to process statistics stream: ${e.message}")
                .build()
        }
    }

    private fun updateTestStatusToFinished(
        testId: String
    ) {
        if (testId.isBlank()) {
            logger.warn("[Report] TestID가 비어있어 상태 업데이트를 건너뜁니다.")
            return
        }

        try {
            val testPlan = testPlanRepository.findById(
                id = testId
            )

            if (testPlan != null) {
                val finishedPlan = testPlan.stop()
                testPlanRepository.save(
                    testPlan = finishedPlan
                )

                logger.info("[Report] TestPlan 상태를 FINISHED로 업데이트했습니다 - ID: $testId")
            } else {
                logger.warn("[Report] TestPlan을 찾을 수 없습니다 - ID: $testId")
            }
        } catch (e: Exception) {
            logger.error("[Report] TestPlan 상태 업데이트 실패 - ID: $testId", e)
        }
    }
}

