package com.ch.hammerscale.controller.presentation

import com.ch.hammerscale.controller.domain.service.TestStopService
import com.ch.hammerscale.controller.presentation.dto.ApiResponse
import com.ch.hammerscale.controller.presentation.dto.StopTestResponse
import kotlinx.coroutines.runBlocking
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/test")
class TestStopController(
    private val testStopService: TestStopService
) {
    /**
     * 실행 중인 테스트를 강제 종료
     */
    @PostMapping("/{testId}/stop")
    fun stopTest(
        @PathVariable testId: String
    ): ApiResponse<StopTestResponse> = runBlocking {
        val response = testStopService.stopTest(
            testId = testId
        )
        
        if (response.success) {
            ApiResponse.success(
                data = response,
                message = response.message
            )
        } else {
            ApiResponse.error(
                message = response.message,
                code = if (response.testId.isEmpty()) "NOT_FOUND" else "STOP_FAILED"
            )
        }
    }
}
