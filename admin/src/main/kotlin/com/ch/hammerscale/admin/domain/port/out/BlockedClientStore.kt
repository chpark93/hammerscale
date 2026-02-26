package com.ch.hammerscale.admin.domain.port.out

import com.ch.hammerscale.admin.domain.model.abuse.BlockedClient

interface BlockedClientStore {
    fun isBlocked(
        clientId: String
    ): Boolean

    fun block(
        client: BlockedClient
    )

    fun unblock(
        clientId: String
    )

    fun findAll(): List<BlockedClient>
}
