package com.ch.hammerscale.controller.infrastructure.influxdb

import com.ch.hammerscale.controller.domain.port.out.TestMetricData
import com.ch.hammerscale.controller.domain.port.out.TestMetricRepository
import com.influxdb.client.domain.WritePrecision
import com.influxdb.client.kotlin.InfluxDBClientKotlin
import com.influxdb.client.write.Point
import com.project.common.proto.TestStat
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.toList
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

    override suspend fun getMetrics(
        testId: String,
        startTime: Instant?,
        endTime: Instant?
    ): List<TestMetricData> {
        try {
            val start = startTime?.toString() ?: "-30d"
            val stop = endTime?.toString() ?: "now()"

            val query = """
                from(bucket: "$bucket")
                  |> range(start: $start, stop: $stop)
                  |> filter(fn: (r) => r["_measurement"] == "load_test_metrics")
                  |> filter(fn: (r) => r["testId"] == "$testId")
                  |> pivot(rowKey:["_time"], columnKey: ["_field"], valueColumn: "_value")
            """.trimIndent()

            logger.debug("[InfluxDB] 메트릭 조회 시작 - TestID: $testId")

            val metrics = influxDBClient.getQueryKotlinApi()
                .query(query, org)
                .receiveAsFlow()
                .map { record ->
                    TestMetricData(
                        timestamp = record.time ?: Instant.now(),
                        tps = (record.values["tps"] as? Number)?.toInt() ?: 0,
                        avgLatency = (record.values["avg_latency"] as? Number)?.toDouble() ?: 0.0,
                        p50Latency = (record.values["p50_latency"] as? Number)?.toDouble() ?: 0.0,
                        p95Latency = (record.values["p95_latency"] as? Number)?.toDouble() ?: 0.0,
                        p99Latency = (record.values["p99_latency"] as? Number)?.toDouble() ?: 0.0,
                        errorCount = (record.values["error_count"] as? Number)?.toInt() ?: 0,
                        activeUsers = (record.values["active_users"] as? Number)?.toInt() ?: 0
                    )
                }
                .toList()

            logger.info("[InfluxDB] 메트릭 조회 완료 - TestID: $testId, Count: ${metrics.size}")

            return metrics

        } catch (e: Exception) {
            logger.error("[InfluxDB] 메트릭 조회 실패: ${e.message}", e)
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
            .addField("p50_latency", this.p50LatencyMs)
            .addField("p95_latency", this.p95LatencyMs)
            .addField("p99_latency", this.p99LatencyMs)
            .addField("error_count", this.errorCount)
            .addField("active_users", this.activeUsers)
            .time(Instant.ofEpochMilli(this.timestamp), WritePrecision.MS)
    }
}

