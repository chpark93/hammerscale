package com.ch.hammerscale.controller.presentation

import com.ch.hammerscale.controller.domain.service.TestTriggerService
import com.ch.hammerscale.controller.presentation.dto.ApiResponse
import com.ch.hammerscale.controller.presentation.dto.TriggerRequest
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/test")
@Validated
class TestTriggerController(
    private val testTriggerService: TestTriggerService
) {
    @PostMapping("/trigger")
    fun triggerTest(
        @RequestBody request: TriggerRequest
    ): ApiResponse<String> {
        val result = testTriggerService.triggerTest(request)
        return ApiResponse.success(
            data = result,
            message = "Test started successfully"
        )
    }
}
