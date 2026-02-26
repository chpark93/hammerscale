package com.ch.hammerscale.control.presentation.test

import com.ch.hammerscale.control.application.TestTriggerService
import com.project.common.api.ApiResponse
import com.ch.hammerscale.control.presentation.test.dto.TriggerRequest
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
        val result = testTriggerService.triggerTest(
            request = request
        )

        return ApiResponse.success(
            data = result,
            message = "Test started successfully"
        )
    }
}
