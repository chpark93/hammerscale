package com.ch.hammerscale.control.presentation.test

import com.ch.hammerscale.control.application.TestQueryService
import com.project.common.api.ApiResponse
import com.ch.hammerscale.control.presentation.test.dto.TestPlanResponse
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
        val testPlan = testQueryService.getTestById(
            id = id
        )

        return ApiResponse.success(
            data = testPlan,
            message = "Test plan retrieved successfully"
        )
    }
}
