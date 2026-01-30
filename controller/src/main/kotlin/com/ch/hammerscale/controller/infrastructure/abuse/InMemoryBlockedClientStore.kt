package com.ch.hammerscale.controller.infrastructure.abuse

import com.ch.hammerscale.controller.domain.model.abuse.BlockedClient
import com.ch.hammerscale.controller.domain.port.out.BlockedClientStore
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Component
class InMemoryBlockedClientStore : BlockedClientStore {
    private val blocked = ConcurrentHashMap<String, BlockedClient>()

    override fun isBlocked(
        clientId: String
    ): Boolean {
        val client = blocked[clientId] ?: return false
        if (client.expiresAt != null && client.expiresAt.isBefore(Instant.now())) {
            blocked.remove(clientId)
            return false
        }

        return true
    }

    override fun block(
        client: BlockedClient
    ) {
        blocked[client.clientId] = client
    }

    override fun unblock(
        clientId: String
    ) {
        blocked.remove(
            key = clientId
        )
    }

    override fun findAll(): List<BlockedClient> {
        val now = Instant.now()
        return blocked.values.filter { it.expiresAt == null || !it.expiresAt.isBefore(now) }.toList()
    }
}
