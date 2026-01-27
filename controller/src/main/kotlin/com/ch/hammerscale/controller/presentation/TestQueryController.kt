package com.ch.hammerscale.controller.presentation

import com.ch.hammerscale.controller.domain.service.TestQueryService
import com.ch.hammerscale.controller.presentation.dto.ApiResponse
import com.ch.hammerscale.controller.presentation.dto.TestPlanResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/test")
class TestQueryController(
    private val testQueryService: TestQueryService
) {
    @GetMapping("/{id}")
    fun getTestById(
        @PathVariable id: String
    ): ApiResponse<TestPlanResponse> {
        val testPlan = testQueryService.getTestById(id)
        return ApiResponse.success(
            data = testPlan,
            message = "Test plan retrieved successfully"
        )
    }
}
