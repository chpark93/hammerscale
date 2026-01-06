package com.ch.hammerscale.agent.grpc

import com.project.common.proto.AgentServiceGrpcKt
import com.project.common.proto.Ack
import com.project.common.proto.Empty
import com.project.common.proto.Pong
import com.project.common.proto.TestConfig
import kotlinx.coroutines.flow.Flow
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class AgentServiceGrpcImpl : AgentServiceGrpcKt.AgentServiceCoroutineImplBase() {

    private val logger = LoggerFactory.getLogger(AgentServiceGrpcImpl::class.java)

    override suspend fun startTest(request: TestConfig): Ack {
        logger.info(
            "[Agent] 부하 테스트 시작 요청 수신 - ID: ${request.testId}, Users: ${request.virtualUsers}"
        )

        return Ack.newBuilder()
            .setSuccess(true)
            .setMessage("Test started successfully")
            .build()
    }

    override suspend fun stopTest(request: com.project.common.proto.TestId): Ack {
        logger.info("[Agent] 부하 테스트 중지 요청 수신 - ID: ${request.id}")

        return Ack.newBuilder()
            .setSuccess(true)
            .setMessage("Test stopped successfully")
            .build()
    }

    override suspend fun ping(request: Empty): Pong {
        return Pong.newBuilder()
            .setAlive(true)
            .build()
    }
}

