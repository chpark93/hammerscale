package com.ch.hammerscale.controller.presentation.dto

data class DetailedTestResult(
    val testId: String,
    val testType: String,
    val status: String,
    val createdAt: String,
    val duration: Int,
    val metrics: TestMetrics,
    val summary: TestResultSummary,
    val breakingPoint: TestBreakingPoint,
    val tpsSaturation: TestTpsSaturation,
    val thresholds: TestThresholds
) {
    companion object {
        fun empty(
            testId: String,
            testType: String
        ) = DetailedTestResult(
            testId = testId,
            testType = testType,
            status = "NO_DATA",
            createdAt = "",
            duration = 0,
            metrics = TestMetrics.empty(),
            summary = TestResultSummary.empty(),
            breakingPoint = TestBreakingPoint(
                detected = false,
                activeUsers = null,
                healthStatus = null,
                timestamp = null,
                tps = null,
                latency = null,
                errorRate = null
            ),
            tpsSaturation = TestTpsSaturation(
                detected = false,
                activeUsers = null,
                maxTps = 0,
                timestamp = null
            ),
            thresholds = TestThresholds(
                passed = true,
                failed = false,
                results = emptyMap()
            )
        )
    }
}

data class TestMetrics(
    val httpReqs: HttpReqsMetric,
    val httpReqDuration: HttpReqDurationMetric,
    val httpReqFailed: HttpReqFailedMetric,
    val vus: VusMetric,
    val tps: TpsMetric,
    val errorRate: ErrorRateMetric
) {
    companion object {
        fun empty() = TestMetrics(
            httpReqs = HttpReqsMetric(
                count = 0,
                rate = 0.0,
                failed = 0,
                successful = 0
            ),
            httpReqDuration = HttpReqDurationMetric(
                avg = 0.0,
                min = 0.0,
                max = 0.0,
                med = 0.0,
                p90 = 0.0,
                p95 = 0.0,
                p99 = 0.0
            ),
            httpReqFailed = HttpReqFailedMetric(
                count = 0,
                rate = 0.0
            ),
            vus = VusMetric(
                value = 0,
                min = 0,
                max = 0
            ),
            tps = TpsMetric(
                value = 0,
                avg = 0.0,
                min = 0,
                max = 0
            ),
            errorRate = ErrorRateMetric(
                value = 0.0,
                avg = 0.0,
                max = 0.0
            )
        )
    }
}

data class HttpReqsMetric(
    val count: Int,
    val rate: Double,
    val failed: Int,
    val successful: Int
)
data class HttpReqDurationMetric(
    val avg: Double,
    val min: Double,
    val max: Double,
    val med: Double,
    val p90: Double,
    val p95: Double,
    val p99: Double
)
data class HttpReqFailedMetric(
    val count: Int,
    val rate: Double
)
data class VusMetric(
    val value: Int,
    val min: Int,
    val max: Int
)
data class TpsMetric(
    val value: Int,
    val avg: Double,
    val min: Int,
    val max: Int
)
data class ErrorRateMetric(
    val value: Double,
    val avg: Double,
    val max: Double
)

data class TestResultSummary(
    val totalDuration: String,
    val totalRequests: Int,
    val successfulRequests: Int,
    val failedRequests: Int,
    val requestsPerSecond: Double,
    val avgLatency: String,
    val p95Latency: String,
    val p99Latency: String,
    val errorRate: String,
    val healthStatus: String
) {
    companion object {
        fun empty() = TestResultSummary(
            totalDuration = "0s",
            totalRequests = 0,
            successfulRequests = 0,
            failedRequests = 0,
            requestsPerSecond = 0.0,
            avgLatency = "0ms",
            p95Latency = "0ms",
            p99Latency = "0ms",
            errorRate = "0%",
            healthStatus = "UNKNOWN"
        )
    }
}

data class TestBreakingPoint(
    val detected: Boolean,
    val activeUsers: Int?,
    val healthStatus: String?,
    val timestamp: String?,
    val tps: Int?,
    val latency: Double?,
    val errorRate: Double?
)

data class TestTpsSaturation(
    val detected: Boolean,
    val activeUsers: Int?,
    val maxTps: Int,
    val timestamp: String?
)

data class TestThresholds(
    val passed: Boolean,
    val failed: Boolean,
    val results: Map<String, ThresholdResult>
)

data class ThresholdResult(
    val threshold: String,
    val passed: Boolean,
    val value: String
)
