package com.ch.hammerscale.controller.domain.service

import com.ch.hammerscale.controller.domain.port.out.LoadAgentPort
import com.ch.hammerscale.controller.infrastructure.grpc.AgentConnectionException
import com.ch.hammerscale.controller.presentation.dto.HealthStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class HealthCheckService(
    private val loadAgentPort: LoadAgentPort
) {
    private val logger = LoggerFactory.getLogger(HealthCheckService::class.java)

    /**
     * 시스템 전체 상태 체크
     */
    fun checkHealth(): HealthStatus {
        val controllerStatus = "OK"
        
        val agentStatus = try {
            // Agent ping 시도
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
            message = if (agentStatus != "OK") {
                "Agent is not running. Please start the agent: ./gradlew :agent:bootRun"
            } else {
                "All systems operational"
            }
        )
    }
}
