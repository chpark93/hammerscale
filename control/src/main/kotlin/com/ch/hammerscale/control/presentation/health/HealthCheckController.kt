package com.ch.hammerscale.control.presentation.health

import com.ch.hammerscale.control.application.HealthCheckService
import com.project.common.api.ApiResponse
import com.ch.hammerscale.control.presentation.health.dto.HealthStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/health")
class HealthCheckController(
    private val healthCheckService: HealthCheckService
) {
    /**
     * 시스템 상태 체크
     */
    @GetMapping
    fun checkHealth(): ApiResponse<HealthStatus> {
        val healthStatus = healthCheckService.checkHealth()

        return ApiResponse.success(
            data = healthStatus,
            message = "Health check completed"
        )
    }
}
