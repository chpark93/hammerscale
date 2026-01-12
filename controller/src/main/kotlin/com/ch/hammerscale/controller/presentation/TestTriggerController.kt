package com.ch.hammerscale.controller.presentation

import com.ch.hammerscale.controller.domain.model.HttpMethod
import com.ch.hammerscale.controller.domain.model.LoadConfig
import com.ch.hammerscale.controller.domain.model.TestPlan
import com.ch.hammerscale.controller.domain.port.out.LoadAgentPort
import com.ch.hammerscale.controller.domain.port.out.TestPlanRepository
import com.ch.hammerscale.controller.infrastructure.grpc.AgentConnectionException
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import org.slf4j.LoggerFactory
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/test")
@Validated
class TestTriggerController(
    private val loadAgentPort: LoadAgentPort,
    private val testPlanRepository: TestPlanRepository
) {

    private val logger = LoggerFactory.getLogger(TestTriggerController::class.java)

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
                method = HttpMethod.valueOf(req.method.uppercase()),
                headers = req.headers ?: emptyMap(),
                queryParams = req.queryParams ?: emptyMap(),
                requestBody = req.requestBody,
                rampUpSeconds = req.rampUpSeconds ?: 0
            )
        )

        testPlanRepository.save(
            testPlan = testPlan
        )
        logger.info("[TestTrigger] TestPlan 생성 및 저장 완료 - ID: ${testPlan.id}, Status: READY")

        try {
            // Agent에게 테스트 시작 요청
            loadAgentPort.runTest(testPlan)

            // Agent가 수락하면 RUNNING 상태로 업데이트
            val runningPlan = testPlan.start()
            testPlanRepository.save(runningPlan)
            logger.info("[TestTrigger] Agent가 테스트를 시작했습니다 - ID: ${testPlan.id}, Status: RUNNING")

            return "Test started successfully - testId=${testPlan.id}"

        } catch (e: AgentConnectionException) {
            // Agent 호출 실패 시 FAILED 상태로 업데이트
            val failedPlan = testPlan.markAsFailed()
            testPlanRepository.save(failedPlan)
            logger.error("[TestTrigger] Agent 호출 실패 - ID: ${testPlan.id}, Status: FAILED", e)

            throw e
        }
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

    // HTTP Method: "GET" | "POST" | "PUT" | "PATCH" | "DELETE"
    val method: String = "GET",

    // HTTP Headers (예: {"Authorization": "Bearer token", "Content-Type": "application/json"})
    val headers: Map<String, String>? = null,

    // Query Parameters (예: {"page": "1", "size": "10"})
    val queryParams: Map<String, String>? = null,

    // Request Body (JSON 문자열 등)
    val requestBody: String? = null,

    // Ramp-up 시간 (초). 0 = 즉시 시작, >0 = 점진적으로 Virtual User 증가
    // 예: virtualUsers=1000, rampUpSeconds=60 → 1초에 약 16명씩 증가
    @field:Min(0)
    val rampUpSeconds: Int? = null
)

