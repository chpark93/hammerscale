package com.ch.hammerscale.control.presentation.test

import com.ch.hammerscale.control.application.TestResultService
import com.project.common.api.ApiResponse
import com.ch.hammerscale.control.presentation.test.dto.DetailedTestResult
import kotlinx.coroutines.runBlocking
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/result")
class TestResultController(
    private val testResultService: TestResultService
) {
    @GetMapping("/{testId}")
    fun getTestResult(
        @PathVariable testId: String
    ): ApiResponse<DetailedTestResult> = runBlocking {
        val result = testResultService.getTestResult(
            testId = testId
        )

        ApiResponse.success(
            data = result,
            message = "Test result retrieved successfully"
        )
    }
}
