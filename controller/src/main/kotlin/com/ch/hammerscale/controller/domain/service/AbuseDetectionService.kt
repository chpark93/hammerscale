package com.ch.hammerscale.controller.domain.service

import com.ch.hammerscale.controller.domain.model.abuse.*
import com.ch.hammerscale.controller.domain.port.out.AbuseEventRepository
import com.ch.hammerscale.controller.domain.port.out.BlockedClientStore
import com.ch.hammerscale.controller.domain.port.out.RateLimitStore
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.*

@Service
class AbuseDetectionService(
    private val blockedClientStore: BlockedClientStore,
    private val rateLimitStore: RateLimitStore,
    private val abuseEventRepository: AbuseEventRepository,
    @Value($$"${hammerscale.abuse.auto-block-after-violations:10}")
    private val autoBlockAfterViolations: Int,
    @Value($$"${hammerscale.abuse.auto-block-duration-minutes:60}")
    private val autoBlockDurationMinutes: Int
) {
    fun checkBlocked(
        clientId: String
    ): BlockCheckResult {
        if (!blockedClientStore.isBlocked(clientId)) return BlockCheckResult.Allowed

        return BlockCheckResult.Blocked
    }

    fun checkRateLimit(
        clientId: String
    ): RateLimitCheckResult {
        val allowed = rateLimitStore.allowRequest(
            clientId = clientId
        )
        val remaining = rateLimitStore.getRemainingRequests(
            clientId = clientId
        )

        return if (allowed) {
            RateLimitCheckResult.Allowed(
                remaining = remaining
            )
        } else {
            RateLimitCheckResult.Throttled(
                remaining = remaining
            )
        }
    }

    fun recordAbuse(
        clientId: String,
        eventType: AbuseEventType,
        path: String?,
        details: String?
    ) {
        val event = AbuseEvent(
            id = UUID.randomUUID().toString(),
            clientId = clientId,
            eventType = eventType,
            action = AbuseAction.THROTTLED,
            path = path,
            details = details,
            createdAt = Instant.now()
        )
        abuseEventRepository.save(
            event = event
        )

        if (autoBlockAfterViolations > 0) {
            val since = Instant.now().minusSeconds(3600)
            val recent = abuseEventRepository.findByClientId(
                clientId = clientId,
                since = since
            )
            val rateLimitEvents = recent.count { it.eventType == AbuseEventType.RATE_LIMIT_EXCEEDED }
            if (rateLimitEvents >= autoBlockAfterViolations) {
                val expiresAt = Instant.now().plusSeconds(autoBlockDurationMinutes * 60L)
                val blocked = BlockedClient(
                    id = UUID.randomUUID().toString(),
                    clientId = clientId,
                    reason = "Auto-blocked after $rateLimitEvents rate limit violations",
                    blockedBy = BlockedBy.AUTO,
                    blockedAt = Instant.now(),
                    expiresAt = expiresAt
                )

                blockedClientStore.block(
                    client = blocked
                )

                abuseEventRepository.save(
                    AbuseEvent(
                        id = UUID.randomUUID().toString(),
                        clientId = clientId,
                        eventType = AbuseEventType.AUTO_BLOCKED_AFTER_VIOLATIONS,
                        action = AbuseAction.BLOCKED,
                        path = path,
                        details = "Auto-blocked for $autoBlockDurationMinutes minutes",
                        createdAt = Instant.now()
                    )
                )
            }
        }
    }

    sealed class BlockCheckResult {
        data object Allowed : BlockCheckResult()
        data object Blocked : BlockCheckResult()
    }

    sealed class RateLimitCheckResult {
        data class Allowed(
            val remaining: Int
        ) : RateLimitCheckResult()

        data class Throttled(
            val remaining: Int
        ) : RateLimitCheckResult()
    }
}
