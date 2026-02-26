package com.ch.hammerscale.control.application

import com.ch.hammerscale.control.domain.port.out.LoadAgentPort
import com.ch.hammerscale.control.infrastructure.grpc.AgentConnectionException
import com.ch.hammerscale.control.presentation.health.dto.HealthStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class HealthCheckService(
    private val loadAgentPort: LoadAgentPort
) {
    private val logger = LoggerFactory.getLogger(HealthCheckService::class.java)

    fun checkHealth(): HealthStatus {
        val controllerStatus = "OK"
        val agentStatus = try {
            loadAgentPort.ping()

            "OK"
        } catch (e: AgentConnectionException) {
            logger.warn("[HealthCheck] Agent 연결 실패: ${e.message}")

            "DISCONNECTED"
        } catch (e: Exception) {
            logger.error("[HealthCheck] Agent 상태 확인 중 에러: ${e.message}")

            "ERROR"
        }

        val overallStatus = if (agentStatus == "OK") "HEALTHY" else "DEGRADED"

        return HealthStatus(
            overall = overallStatus,
            controller = controllerStatus,
            agent = agentStatus,
            message = if (agentStatus != "OK") "Agent is not running. Please start the agent: ./gradlew :agent:bootRun" else "All systems operational"
        )
    }
}
