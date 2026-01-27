package com.ch.hammerscale.controller.presentation

import com.ch.hammerscale.controller.domain.service.TestResultService
import com.ch.hammerscale.controller.presentation.dto.ApiResponse
import com.ch.hammerscale.controller.presentation.dto.DetailedTestResult
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
    /**
     * 상세한 테스트 결과 반환
     * 테스트가 완료된 경우에만 결과를 반환
     */
    @GetMapping("/{testId}")
    fun getTestResult(
        @PathVariable testId: String
    ): ApiResponse<DetailedTestResult> = runBlocking {
        val result = testResultService.getTestResult(testId)
        ApiResponse.success(
            data = result,
            message = "Test result retrieved successfully"
        )
    }
}
