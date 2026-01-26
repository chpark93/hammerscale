package com.ch.hammerscale.controller.domain.model

/**
 * 테스트 실행 중 시스템 Health Status
 * 
 * Metric(에러율, 레이턴시, TPS)을 기반으로 실시간 상태를 판단.
 */
enum class HealthStatus(
    val description: String,
    val emoji: String
) {
    /**
     * 정상 상태
     * - 에러율 < 1%
     * - 평균 레이턴시 < 500ms
     */
    HEALTHY(
        description = "정상",
        emoji = "✅"
    ),
    
    /**
     * 성능 저하 상태
     * - 에러율 1-5%
     * - 평균 레이턴시 500-1000ms
     */
    DEGRADED(
        description = "성능 저하",
        emoji = "⚠️"
    ),
    
    /**
     * 임계 상태 (한계점 근처)
     * - 에러율 5-20%
     * - 평균 레이턴시 1000-2000ms
     */
    CRITICAL(
        description = "임계 상태",
        emoji = "🔥"
    ),
    
    /**
     * 실패 상태 (시스템 한계 초과)
     * - 에러율 > 20%
     * - 평균 레이턴시 > 2000ms
     */
    FAILED(
        description = "실패",
        emoji = "❌"
    );
    
    companion object {
        /**
         * Metric을 기반으로 Health Status를 판단.
         */
        fun fromMetrics(
            avgLatencyMs: Double,
            errorRate: Double,
            requestCount: Int
        ): HealthStatus {
            // 요청이 너무 적으면 우선 판단 [HEALTHY]
            if (requestCount < 10) {
                return HEALTHY
            }
            
            // 에러율이 높으면 우선 판단 [FAILED]
            if (errorRate > 0.20) {
                return FAILED
            }
            
            if (errorRate > 0.05) {
                return CRITICAL
            }
            
            if (errorRate > 0.01) {
                return DEGRADED
            }
            
            // 에러율이 낮아도 레이턴시가 높으면 이슈
            if (avgLatencyMs > 2000.0) {
                return FAILED
            }
            
            if (avgLatencyMs > 1000.0) {
                return CRITICAL
            }
            
            if (avgLatencyMs > 500.0) {
                return DEGRADED
            }
            
            return HEALTHY
        }
    }
}

