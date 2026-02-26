package com.ch.hammerscale.control.application

import com.ch.hammerscale.control.domain.model.*
import com.ch.hammerscale.control.domain.port.out.LoadAgentPort
import com.ch.hammerscale.control.domain.port.out.TestPlanRepository
import com.ch.hammerscale.control.infrastructure.grpc.AgentConnectionException
import com.ch.hammerscale.control.presentation.test.dto.TriggerRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class TestTriggerService(
    private val loadAgentPort: LoadAgentPort,
    private val testPlanRepository: TestPlanRepository
) {
    private val logger = LoggerFactory.getLogger(TestTriggerService::class.java)

    fun triggerTest(
        request: TriggerRequest
    ): String {
        val testType = TestType.valueOf(request.testType.uppercase())
        val stressConfig = if (testType == TestType.STRESS && request.stressConfig != null) {
            StressTestConfig(
                startUsers = request.stressConfig.startUsers,
                maxUsers = request.stressConfig.maxUsers,
                stepDuration = request.stressConfig.stepDuration,
                stepIncrement = request.stressConfig.stepIncrement
            )
        } else null

        val spikeConfig = if (testType == TestType.SPIKE && request.spikeConfig != null) {
            SpikeTestConfig(
                baseUsers = request.spikeConfig.baseUsers,
                spikeUsers = request.spikeConfig.spikeUsers,
                spikeDuration = request.spikeConfig.spikeDuration,
                recoveryDuration = request.spikeConfig.recoveryDuration
            )
        } else null

        val testPlan = TestPlan.create(
            title = request.title ?: "Test Plan",
            config = LoadConfig(
                testType = testType,
                targetUrl = request.targetUrl,
                virtualUsers = request.virtualUsers ?: 0,
                durationSeconds = request.durationSeconds ?: 0,
                method = HttpMethod.valueOf(request.method.uppercase()),
                headers = request.headers ?: emptyMap(),
                queryParams = request.queryParams ?: emptyMap(),
                requestBody = request.requestBody,
                rampUpSeconds = request.rampUpSeconds ?: 0,
                stressTestConfig = stressConfig,
                spikeTestConfig = spikeConfig
            )
        )

        testPlanRepository.save(
            testPlan = testPlan
        )

        logger.info("[TestTrigger] TestPlan 생성 및 저장 완료 - ID: ${testPlan.id}, Status: READY, Type: ${testType.name}")

        try {
            loadAgentPort.runTest(
                plan = testPlan
            )

            val runningPlan = testPlan.start()

            testPlanRepository.save(
                testPlan = runningPlan
            )

            logger.info("[TestTrigger] Agent가 테스트를 시작했습니다 - ID: ${testPlan.id}, Status: RUNNING")

            return "Test started successfully - testId=${testPlan.id}, type=${testType.name}"
        } catch (e: AgentConnectionException) {
            val failedPlan = testPlan.markAsFailed()

            testPlanRepository.save(
                testPlan = failedPlan
            )

            logger.error("[TestTrigger] Agent 호출 실패 - ID: ${testPlan.id}, Status: FAILED", e)

            throw e
        }
    }
}
