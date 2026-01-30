package com.ch.hammerscale.controller.presentation

import com.ch.hammerscale.controller.domain.model.abuse.BlockedBy
import com.ch.hammerscale.controller.domain.model.abuse.BlockedClient
import com.ch.hammerscale.controller.domain.port.out.AbuseEventRepository
import com.ch.hammerscale.controller.domain.port.out.BlockedClientStore
import com.ch.hammerscale.controller.domain.port.out.ServiceLoadMetrics
import com.ch.hammerscale.controller.presentation.dto.*
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.util.*

@RestController
@RequestMapping("/api/admin")
class AdminController(
    private val abuseEventRepository: AbuseEventRepository,
    private val blockedClientStore: BlockedClientStore,
    private val serviceLoadMetrics: ServiceLoadMetrics
) {
    @GetMapping("/abuse/events")
    fun listAbuseEvents(
        @RequestParam(defaultValue = "100") limit: Int
    ): ApiResponse<List<AbuseEventResponse>> {
        val events = abuseEventRepository.findRecent(
            limit = limit.coerceIn(1, 500)
        )

        val response = events.map { event ->
            AbuseEventResponse(
                id = event.id,
                clientId = event.clientId,
                eventType = event.eventType.name,
                action = event.action.name,
                path = event.path,
                details = event.details,
                createdAt = event.createdAt.toString()
            )
        }

        return ApiResponse.success(
            data = response,
            message = "Abuse events retrieved successfully"
        )
    }

    @GetMapping("/abuse/blocked")
    fun listBlockedClients(): ApiResponse<List<BlockedClientResponse>> {
        val blocked = blockedClientStore.findAll()
        val response = blocked.map {
            BlockedClientResponse(
                id = it.id,
                clientId = it.clientId,
                reason = it.reason,
                blockedBy = it.blockedBy.name,
                blockedAt = it.blockedAt.toString(),
                expiresAt = it.expiresAt?.toString()
            )
        }

        return ApiResponse.success(
            data = response,
            message = "Blocked clients retrieved successfully"
        )
    }

    @PostMapping("/abuse/block")
    fun blockClient(
        @RequestBody request: BlockClientRequest
    ): ApiResponse<BlockedClientResponse> {
        val expiresAt = request.durationMinutes?.let { durationMinutes ->
            Instant.now().plusSeconds(durationMinutes * 60L)
        }
        val blocked = BlockedClient(
            id = UUID.randomUUID().toString(),
            clientId = request.clientId,
            reason = request.reason,
            blockedBy = BlockedBy.ADMIN,
            blockedAt = Instant.now(),
            expiresAt = expiresAt
        )

        blockedClientStore.block(
            client = blocked
        )

        val response = BlockedClientResponse(
            id = blocked.id,
            clientId = blocked.clientId,
            reason = blocked.reason,
            blockedBy = blocked.blockedBy.name,
            blockedAt = blocked.blockedAt.toString(),
            expiresAt = blocked.expiresAt?.toString()
        )

        return ApiResponse.success(
            data = response,
            message = "Client blocked successfully"
        )
    }

    @DeleteMapping("/abuse/block/{clientId}")
    fun unblockClient(
        @PathVariable clientId: String
    ): ApiResponse<Unit> {
        blockedClientStore.unblock(
            clientId = clientId
        )

        return ApiResponse.success(
            message = "Client unblocked successfully"
        )
    }

    @GetMapping("/metrics/load")
    fun getServiceLoad(): ApiResponse<ServiceLoadResponse> {
        val snapshot = serviceLoadMetrics.getSnapshot()
        val response = ServiceLoadResponse(
            totalRequests = snapshot.totalRequests,
            requestsLastMinute = snapshot.requestsLastMinute,
            avgLatencyMs = snapshot.avgLatencyMs,
            errorCountLastMinute = snapshot.errorCountLastMinute,
            byPath = snapshot.byPath.mapValues {
                PathMetricsResponse(
                    count = it.value.count,
                    avgLatencyMs = it.value.avgLatencyMs,
                    errorCount = it.value.errorCount
                )
            }
        )

        return ApiResponse.success(
            data = response,
            message = "Service load metrics retrieved successfully"
        )
    }
}
