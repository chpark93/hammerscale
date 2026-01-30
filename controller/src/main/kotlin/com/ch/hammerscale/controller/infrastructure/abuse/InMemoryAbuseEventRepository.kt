package com.ch.hammerscale.controller.infrastructure.abuse

import com.ch.hammerscale.controller.domain.model.abuse.AbuseEvent
import com.ch.hammerscale.controller.domain.port.out.AbuseEventRepository
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

@Component
class InMemoryAbuseEventRepository(
    private val maxEvents: Int = 10_000
) : AbuseEventRepository {
    private val events = ConcurrentLinkedQueue<AbuseEvent>()
    private val size = AtomicInteger(0)

    override fun save(
        event: AbuseEvent
    ) {
        events.offer(event)
        if (size.incrementAndGet() > maxEvents) {
            repeat(1000) {
                events.poll();
                size.decrementAndGet()
            }
        }
    }

    override fun findByClientId(
        clientId: String,
        since: Instant
    ): List<AbuseEvent> {
        return events.filter {
            it.clientId == clientId &&
                    !it.createdAt.isBefore(since)
        }.toList()
    }

    override fun findRecent(
        limit: Int
    ): List<AbuseEvent> {
        return events.toList().takeLast(limit).reversed()
    }
}
