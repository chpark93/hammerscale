package com.ch.hammerscale.controller.domain.service

import com.ch.hammerscale.controller.domain.model.TestStatus
import com.ch.hammerscale.controller.domain.port.out.LoadAgentPort
import com.ch.hammerscale.controller.domain.port.out.TestPlanRepository
import com.ch.hammerscale.controller.presentation.dto.StopTestResponse
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

        // TestPlan 조회
        val testPlan = testPlanRepository.findById(
            id = testId
        ) ?: return StopTestResponse(
            success = false,
            message = "Test not found",
            testId = "",
            previousStatus = ""
        )

        // 이미 종료된 테스트인지 확인
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
            // Agent에 중지 명령 전송
            loadAgentPort.stopTest(
                planId = testId
            )
            logger.info("[TestStop] Agent에 중지 명령 전송 완료 - ID: $testId")

            // TestPlan 상태를 FINISHED로 업데이트 (사용자가 강제 종료한 경우)
            val updatedPlan = testPlan.copy(status = TestStatus.FINISHED)
            testPlanRepository.save(updatedPlan)
            logger.info("[TestStop] TestPlan 상태 업데이트 완료 - ID: $testId, Status: FINISHED")

            val sanitizedStatus = sanitizeStatus(testPlan.status.name)
            StopTestResponse(
                success = true,
                message = "Test stopped successfully",
                testId = testId,
                previousStatus = sanitizedStatus
            )
        } catch (e: Exception) {
            logger.error("[TestStop] 테스트 중지 실패 - ID: $testId, Error: ${e.message}", e)
            val sanitizedStatus = sanitizeStatus(
                status = testPlan.status.name
            )

            StopTestResponse(
                success = false,
                message = "Failed to stop test. Please check server logs for details.",
                testId = "",
                previousStatus = sanitizedStatus
            )
        }
    }
    
    private fun sanitizeStatus(
        status: String
    ): String {
        return when (status) {
            "READY", "RUNNING", "FINISHED", "FAILED" -> status
            else -> "UNKNOWN"
        }
    }
}
