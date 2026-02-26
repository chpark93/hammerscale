package com.ch.hammerscale.control.infrastructure.grpc

import com.ch.hammerscale.control.domain.model.TestPlan
import com.ch.hammerscale.control.domain.port.out.LoadAgentPort
import com.project.common.proto.AgentServiceGrpcKt
import com.project.common.proto.Empty
import com.project.common.proto.TestId
import io.grpc.ManagedChannel
import io.grpc.StatusException
import kotlinx.coroutines.runBlocking
import org.springframework.stereotype.Component

@Component
class LoadAgentGrpcAdapter(
    private val agentChannel: ManagedChannel
) : LoadAgentPort {

    private val stub: AgentServiceGrpcKt.AgentServiceCoroutineStub by lazy {
        AgentServiceGrpcKt.AgentServiceCoroutineStub(agentChannel)
    }

    override fun runTest(
        plan: TestPlan
    ) {
        try {
            val testConfig = plan.toProto()
            runBlocking {
                val ack = stub.startTest(
                    request = testConfig
                )

                if (!ack.success) throw AgentConnectionException("Agent refused to start testing: ${ack.message}")
            }
        } catch (e: StatusException) {
            throw AgentConnectionException("error occurred while communicating with the Agent service. Status: ${e.status}, Plan ID: ${plan.id}", e)
        } catch (e: Exception) {
            throw AgentConnectionException("unexpected error occurred while running the test. Plan ID: ${plan.id}", e)
        }
    }

    override fun stopTest(
        planId: String
    ) {
        try {
            val testId = TestId.newBuilder().setId(planId).build()
            runBlocking {
                val ack = stub.stopTest(
                    request = testId
                )

                if (!ack.success) throw AgentConnectionException("Agent refused to stop testing: ${ack.message}")
            }
        } catch (e: StatusException) {
            throw AgentConnectionException("error occurred while communicating with the Agent service. Status: ${e.status}, Plan ID: $planId", e)
        } catch (e: Exception) {
            throw AgentConnectionException("unexpected error occurred while stopping the test. Plan ID: $planId", e)
        }
    }

    override fun ping(): Boolean {
        return try {
            runBlocking {
                stub.ping(Empty.getDefaultInstance()).alive
            }
        } catch (e: StatusException) {
            throw AgentConnectionException("Failed to ping Agent. Status: ${e.status}", e)
        } catch (e: Exception) {
            throw AgentConnectionException("Failed to connect to Agent: ${e.message}", e)
        }
    }
}
