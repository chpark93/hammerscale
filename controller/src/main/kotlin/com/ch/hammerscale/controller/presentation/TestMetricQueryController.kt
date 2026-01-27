package com.ch.hammerscale.controller.presentation

import com.ch.hammerscale.controller.domain.service.TestMetricQueryService
import com.ch.hammerscale.controller.presentation.dto.ApiResponse
import com.ch.hammerscale.controller.presentation.dto.TestMetricsResponse
import kotlinx.coroutines.runBlocking
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/test")
class TestMetricQueryController(
    private val testMetricQueryService: TestMetricQueryService
) {
    @GetMapping("/{testId}/metrics")
    fun getMetrics(
        @PathVariable testId: String,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) startTime: LocalDateTime?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) endTime: LocalDateTime?
    ): ApiResponse<TestMetricsResponse> = runBlocking {
        val result = testMetricQueryService.getMetrics(
            testId = testId,
            startTime = startTime,
            endTime = endTime
        )
        ApiResponse.success(
            data = result,
            message = "Metrics retrieved successfully"
        )
    }
}
