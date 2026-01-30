package com.ch.hammerscale.controller.domain.port.out

import com.ch.hammerscale.controller.domain.model.abuse.BlockedClient

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
