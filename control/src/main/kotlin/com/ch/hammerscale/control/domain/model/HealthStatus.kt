package com.ch.hammerscale.control.domain.model

/**
 * 테스트 실행 중 시스템 Health Status
 */
enum class HealthStatus(
    val description: String,
    val emoji: String
) {
    HEALTHY(description = "정상", emoji = "✅"),
    DEGRADED(description = "성능 저하", emoji = "⚠️"),
    CRITICAL(description = "임계 상태", emoji = "🔥"),
    FAILED(description = "실패", emoji = "❌");

    companion object {
        fun fromMetrics(
            avgLatencyMs: Double,
            errorRate: Double,
            requestCount: Int
        ): HealthStatus {
            if (requestCount < 10) return HEALTHY
            if (errorRate > 0.20) return FAILED
            if (errorRate > 0.05) return CRITICAL
            if (errorRate > 0.01) return DEGRADED
            if (avgLatencyMs > 2000.0) return FAILED
            if (avgLatencyMs > 1000.0) return CRITICAL
            if (avgLatencyMs > 500.0) return DEGRADED

            return HEALTHY
        }
    }
}
