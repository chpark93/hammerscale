package com.ch.hammerscale.control.application

import com.ch.hammerscale.control.domain.model.TestStatus
import com.ch.hammerscale.control.domain.port.out.LoadAgentPort
import com.ch.hammerscale.control.domain.port.out.TestPlanRepository
import com.ch.hammerscale.control.presentation.test.dto.StopTestResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class TestStopService(
    private val testPlanRepository: TestPlanRepository,
    private val loadAgentPort: LoadAgentPort
) {
    private val logger = LoggerFactory.getLogger(TestStopService::class.java)

    suspend fun stopTest(
        testId: String
    ): StopTestResponse {
        logger.info("[TestStop] 테스트 중지 요청 - ID: $testId")
        val testPlan = testPlanRepository.findById(
            id = testId
        ) ?: return StopTestResponse(
            success = false,
            message = "Test not found",
            testId = "",
            previousStatus = ""
        )

        val currentStatus = testPlan.status
        if (currentStatus == TestStatus.FINISHED || currentStatus == TestStatus.FAILED) {
            val sanitizedStatus = sanitizeStatus(currentStatus.name)
            logger.warn("[TestStop] 이미 종료된 테스트 - ID: $testId, Status: $sanitizedStatus")

            return StopTestResponse(
                success = false,
                message = "Test already finished",
                testId = testId,
                previousStatus = sanitizedStatus
            )
        }

        return try {
            loadAgentPort.stopTest(
                planId = testId
            )

            logger.info("[TestStop] Agent에 중지 명령 전송 완료 - ID: $testId")

            val updatedPlan = testPlan.copy(status = TestStatus.FINISHED)
            testPlanRepository.save(
                testPlan = updatedPlan
            )
            logger.info("[TestStop] TestPlan 상태 업데이트 완료 - ID: $testId, Status: FINISHED")

            StopTestResponse(
                success = true,
                message = "Test stopped successfully",
                testId = testId,
                previousStatus = sanitizeStatus(
                    status = testPlan.status.name
                )
            )
        } catch (e: Exception) {
            logger.error("[TestStop] 테스트 중지 실패 - ID: $testId, Error: ${e.message}", e)
            StopTestResponse(
                success = false,
                message = "Failed to stop test. Please check server logs for details.",
                testId = "",
                previousStatus = sanitizeStatus(testPlan.status.name)
            )
        }
    }

    private fun sanitizeStatus(
        status: String
    ): String = when (status) {
        "READY", "RUNNING", "FINISHED", "FAILED" -> status
        else -> "UNKNOWN"
    }
}
