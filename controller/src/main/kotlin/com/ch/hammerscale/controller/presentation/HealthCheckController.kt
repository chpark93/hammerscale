package com.ch.hammerscale.controller.presentation

import com.ch.hammerscale.controller.domain.service.HealthCheckService
import com.ch.hammerscale.controller.presentation.dto.ApiResponse
import com.ch.hammerscale.controller.presentation.dto.HealthStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/health")
class HealthCheckController(
    private val healthCheckService: HealthCheckService
) {
    /**
     * 시스템 전체 상태 체크
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
