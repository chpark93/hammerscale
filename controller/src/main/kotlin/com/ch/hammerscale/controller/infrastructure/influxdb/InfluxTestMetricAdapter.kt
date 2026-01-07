package com.ch.hammerscale.controller.infrastructure.influxdb

import com.ch.hammerscale.controller.domain.port.out.TestMetricRepository
import com.influxdb.client.domain.WritePrecision
import com.influxdb.client.kotlin.InfluxDBClientKotlin
import com.influxdb.client.write.Point
import com.project.common.proto.TestStat
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class InfluxTestMetricAdapter(
    private val influxDBClient: InfluxDBClientKotlin,
    @Value($$"${influxdb.bucket:metrics}")
    private val bucket: String,
    @Value($$"${influxdb.org:hammerscale}")
    private val org: String
) : TestMetricRepository {

    private val logger = LoggerFactory.getLogger(InfluxTestMetricAdapter::class.java)

    override suspend fun saveMetrics(
        stats: List<TestStat>
    ) {
        if (stats.isEmpty()) {
            logger.debug("[InfluxDB] 저장할 메트릭이 없습니다.")
            return
        }

        try {
            val points = stats.map { stat -> stat.toPoint() }

            influxDBClient.getWriteKotlinApi().writePoints(
                points = points,
                bucket = bucket,
                org = org
            )

            logger.info(
                "[InfluxDB] 메트릭 저장 완료 - TestID: ${stats.first().testId}, " +
                "Count: ${stats.size}, Bucket: $bucket"
            )
        } catch (e: Exception) {
            logger.error("[InfluxDB] 메트릭 저장 실패: ${e.message}", e)
            throw e
        }
    }

    /**
     * TestStat을 InfluxDB Point로 변환
     */
    private fun TestStat.toPoint(): Point {
        return Point.measurement("load_test_metrics")
            .addTag("testId", this.testId)
            .addField("tps", this.requestsPerSecond)
            .addField("avg_latency", this.avgLatencyMs)
            .addField("error_count", this.errorCount)
            .addField("active_users", this.activeUsers)
            .time(Instant.ofEpochMilli(this.timestamp), WritePrecision.MS)
    }
}

