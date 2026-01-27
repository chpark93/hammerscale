package com.ch.hammerscale.controller.domain.service

import com.ch.hammerscale.controller.domain.model.TestPlan
import com.ch.hammerscale.controller.domain.port.out.TestPlanRepository
import com.ch.hammerscale.controller.presentation.dto.TestPlanResponse
import com.ch.hammerscale.controller.presentation.exception.ResourceNotFoundException
import org.springframework.stereotype.Service

@Service
class TestQueryService(
    private val testPlanRepository: TestPlanRepository
) {
    fun getTestById(
        id: String
    ): TestPlanResponse {
        val testPlan = testPlanRepository.findById(id)
            ?: throw ResourceNotFoundException(
                message = "TestPlan not found",
                resourceType = "TestPlan",
                resourceId = id
            )

        return testPlan.toResponse()
    }
    
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
}
