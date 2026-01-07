package com.ch.hammerscale.controller.presentation

import com.ch.hammerscale.controller.domain.model.HttpMethod
import com.ch.hammerscale.controller.domain.model.LoadConfig
import com.ch.hammerscale.controller.domain.model.TestPlan
import com.ch.hammerscale.controller.domain.port.out.LoadAgentPort
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/test")
@Validated
class TestTriggerController(
    private val loadAgentPort: LoadAgentPort
) {

    @PostMapping("/trigger")
    fun triggerTest(
        @RequestBody req: TriggerRequest
    ): String {
        val testPlan = TestPlan.create(
            title = req.title ?: "Test Plan",
            config = LoadConfig(
                targetUrl = req.targetUrl,
                virtualUsers = req.virtualUsers,
                durationSeconds = req.durationSeconds,
                method = HttpMethod.valueOf(req.method.uppercase())
            )
        )

        loadAgentPort.runTest(testPlan)

        return "ok - testId=${testPlan.id}"
    }
}

data class TriggerRequest(
    val title: String? = null,
    @field:NotBlank
    val targetUrl: String,
    @field:Min(1)
    @field:Max(200_000)
    val virtualUsers: Int,
    @field:Min(1)
    @field:Max(3600)
    val durationSeconds: Int,
    /**
     * "GET" | "POST"
     */
    val method: String = "GET"
)

