package com.ch.hammerscale.controller.presentation

import com.ch.hammerscale.controller.domain.model.*
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
        val testType = TestType.valueOf(req.testType.uppercase())
        
        val stressConfig = if (testType == TestType.STRESS && req.stressConfig != null) {
            StressTestConfig(
                startUsers = req.stressConfig.startUsers,
                maxUsers = req.stressConfig.maxUsers,
                stepDuration = req.stressConfig.stepDuration,
                stepIncrement = req.stressConfig.stepIncrement
            )
        } else null
        
        val spikeConfig = if (testType == TestType.SPIKE && req.spikeConfig != null) {
            SpikeTestConfig(
                baseUsers = req.spikeConfig.baseUsers,
                spikeUsers = req.spikeConfig.spikeUsers,
                spikeDuration = req.spikeConfig.spikeDuration,
                recoveryDuration = req.spikeConfig.recoveryDuration
            )
        } else null
        
        val testPlan = TestPlan.create(
            title = req.title ?: "Test Plan",
            config = LoadConfig(
                testType = testType,
                targetUrl = req.targetUrl,
                virtualUsers = req.virtualUsers ?: 0,
                durationSeconds = req.durationSeconds ?: 0,
                method = HttpMethod.valueOf(req.method.uppercase()),
                headers = req.headers ?: emptyMap(),
                queryParams = req.queryParams ?: emptyMap(),
                requestBody = req.requestBody,
                rampUpSeconds = req.rampUpSeconds ?: 0,
                stressTestConfig = stressConfig,
                spikeTestConfig = spikeConfig
            )
        )

        testPlanRepository.save(
            testPlan = testPlan
        )
        logger.info("[TestTrigger] TestPlan 생성 및 저장 완료 - ID: ${testPlan.id}, Status: READY, Type: ${testType.name}")

        try {
            // Agent에게 테스트 시작 요청
            loadAgentPort.runTest(testPlan)

            // Agent가 수락하면 RUNNING 상태로 업데이트
            val runningPlan = testPlan.start()
            testPlanRepository.save(runningPlan)
            logger.info("[TestTrigger] Agent가 테스트를 시작했습니다 - ID: ${testPlan.id}, Status: RUNNING")

            return "Test started successfully - testId=${testPlan.id}, type=${testType.name}"

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
    
    // 테스트 타입: LOAD | STRESS | SPIKE | SOAK
    val testType: String = "LOAD",

    // ===== LOAD / SOAK 테스트 전용 =====
    @field:Min(0)
    @field:Max(200_000)
    val virtualUsers: Int? = null, // LOAD / SOAK 타입에서 필수
    
    @field:Min(0)
    @field:Max(86400) // 최대 24시간 (For SOAK 테스트)
    val durationSeconds: Int? = null, // LOAD / SOAK 타입에서 필수
    
    // Ramp-up 시간 (초). 0 : 즉시 시작, 0 > : 점진적으로 Virtual User 증가
    @field:Min(0)
    val rampUpSeconds: Int? = null,

    // ===== STRESS 테스트 전용 =====
    val stressConfig: StressConfigRequest? = null,

    // ===== SPIKE 테스트 전용 =====
    val spikeConfig: SpikeConfigRequest? = null,

    // ===== 공통 설정 =====
    // HTTP Method: "GET" | "POST" | "PUT" | "PATCH" | "DELETE"
    val method: String = "GET",

    // HTTP Headers (예: {"Authorization": "Bearer token", "Content-Type": "application/json"})
    val headers: Map<String, String>? = null,

    // Query Parameters (예: {"page": "1", "size": "10"})
    val queryParams: Map<String, String>? = null,

    // Request Body (JSON 문자열 등)
    val requestBody: String? = null
)

data class StressConfigRequest(
    @field:Min(1)
    val startUsers: Int,
    
    @field:Min(2)
    @field:Max(200_000)
    val maxUsers: Int,
    
    @field:Min(1)
    val stepDuration: Int, // 각 단계 지속 시간 (초)
    
    @field:Min(1)
    val stepIncrement: Int // 각 단계마다 증가할 사용자 수
)

data class SpikeConfigRequest(
    @field:Min(1)
    val baseUsers: Int, // 기본 사용자 수
    
    @field:Min(2)
    @field:Max(200_000)
    val spikeUsers: Int, // 급증 시 사용자 수
    
    @field:Min(1)
    val spikeDuration: Int, // 급증 유지 시간 (초)
    
    @field:Min(0)
    val recoveryDuration: Int // 회복 시간 (초)
)

