package com.ch.hammerscale.agent.grpc

import com.ch.hammerscale.agent.core.LoadGenerator
import com.project.common.proto.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class AgentServiceGrpcImpl(
    private val loadGenerator: LoadGenerator
) : AgentServiceGrpcKt.AgentServiceCoroutineImplBase() {

    private val logger = LoggerFactory.getLogger(AgentServiceGrpcImpl::class.java)
    private val backgroundScope = CoroutineScope(Dispatchers.Default)

    override suspend fun startTest(
        request: TestConfig
    ): Ack {
        logger.info(
            "[Agent] 부하 테스트 시작 요청 수신 - ID: ${request.testId}, URL: ${request.targetUrl}, " +
            "Users: ${request.virtualUsers}, Duration: ${request.durationSeconds}s"
        )

        backgroundScope.launch {
            try {
                loadGenerator.start(request)
            } catch (e: Exception) {
                logger.error("[Agent] 부하 테스트 시작 실패: ${e.message}", e)
            }
        }

        return Ack.newBuilder()
            .setSuccess(true)
            .setMessage("Test start request accepted. Load test is running in background.")
            .build()
    }

    override suspend fun stopTest(
        request: TestId
    ): Ack {
        logger.info("[Agent] 부하 테스트 중지 요청 수신 - ID: ${request.id}")

        try {
            loadGenerator.stop()
            return Ack.newBuilder()
                .setSuccess(true)
                .setMessage("Test stopped successfully")
                .build()
        } catch (e: Exception) {
            logger.error("[Agent] 부하 테스트 중지 실패: ${e.message}", e)
            return Ack.newBuilder()
                .setSuccess(false)
                .setMessage("Failed to stop test: ${e.message}")
                .build()
        }
    }

    override suspend fun ping(
        request: Empty
    ): Pong {
        return Pong.newBuilder()
            .setAlive(true)
            .build()
    }
}

