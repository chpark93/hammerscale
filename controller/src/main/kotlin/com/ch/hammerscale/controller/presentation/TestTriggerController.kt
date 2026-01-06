package com.ch.hammerscale.controller.presentation

import com.ch.hammerscale.controller.domain.model.HttpMethod
import com.ch.hammerscale.controller.domain.model.LoadConfig
import com.ch.hammerscale.controller.domain.model.TestPlan
import com.ch.hammerscale.controller.domain.port.out.LoadAgentPort
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/test")
class TestTriggerController(
    private val loadAgentPort: LoadAgentPort
) {

    @PostMapping("/trigger")
    fun triggerTest(): String {
        val testPlan = TestPlan.create(
            title = "Test Plan",
            config = LoadConfig(
                targetUrl = "http://google.com",
                virtualUsers = 100,
                durationSeconds = 60,
                method = HttpMethod.GET
            )
        )

        loadAgentPort.runTest(testPlan)

        return "success request to agent"
    }
}

