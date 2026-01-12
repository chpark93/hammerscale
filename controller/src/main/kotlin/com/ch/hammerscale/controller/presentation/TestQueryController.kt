package com.ch.hammerscale.controller.presentation

import com.ch.hammerscale.controller.domain.model.TestPlan
import com.ch.hammerscale.controller.domain.port.out.TestPlanRepository
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/test")
class TestQueryController(
    private val testPlanRepository: TestPlanRepository
) {

    @GetMapping("/{id}")
    fun getTestById(
        @PathVariable id: String
    ): TestPlanResponse {
        val testPlan = testPlanRepository.findById(id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "TestPlan not found - ID: $id")

        return testPlan.toResponse()
    }
}

data class TestPlanResponse(
    val id: String,
    val title: String,
    val targetUrl: String,
    val virtualUsers: Int,
    val durationSeconds: Int,
    val method: String,
    val status: String,
    val createdAt: String
)

private fun TestPlan.toResponse(): TestPlanResponse {
    return TestPlanResponse(
        id = this.id,
        title = this.title,
        targetUrl = this.config.targetUrl,
        virtualUsers = this.config.virtualUsers,
        durationSeconds = this.config.durationSeconds,
        method = this.config.method.name,
        status = this.status.name,
        createdAt = this.createdAt.toString()
    )
}

