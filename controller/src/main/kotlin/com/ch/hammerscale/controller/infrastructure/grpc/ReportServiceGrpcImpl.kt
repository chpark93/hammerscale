package com.ch.hammerscale.controller.infrastructure.grpc

import com.ch.hammerscale.controller.domain.port.out.TestMetricRepository
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
    private val testMetricRepository: TestMetricRepository
) : ReportServiceGrpcKt.ReportServiceCoroutineImplBase() {

    private val logger = LoggerFactory.getLogger(ReportServiceGrpcImpl::class.java)

    private companion object {
        const val BATCH_SIZE = 10
    }

    override suspend fun streamStats(
        requests: Flow<TestStat>
    ): Ack {
        logger.info("[Report] 통계 스트림 수신 시작")

        try {
            var totalProcessed = 0
            var totalSaved = 0
            val batch = mutableListOf<TestStat>()

            requests
                .buffer()
                .catch { e ->
                    // 스트림 레벨 에러 처리 - 스트림이 끊기지 않도록
                    logger.error("[Report] 스트림 처리 중 오류 발생: ${e.message}", e)
                    throw e
                }
                .collect { stat ->
                    logger.info(
                        "[Report] TestID: ${stat.testId} | " +
                        "TPS: ${stat.requestsPerSecond} | " +
                        "Avg Latency: ${stat.avgLatencyMs}ms | " +
                        "Errors: ${stat.errorCount} | " +
                        "Active Users: ${stat.activeUsers}"
                    )

                    if (stat.errorCount > 0) {
                        logger.warn("🔴 [Warning] Error Detected! TestID: ${stat.testId}, Error Count: ${stat.errorCount}")
                    }

                    if (stat.avgLatencyMs > 1000) {
                        logger.warn("🟠 [Warning] High Latency! TestID: ${stat.testId}, Avg Latency: ${stat.avgLatencyMs}ms")
                    }

                    totalProcessed++
                    batch.add(stat)

                    // 배치 크기에 도달 -> InfluxDB에 저장
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
                            batch.clear() // 실패해도 배치는 초기화
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
}

