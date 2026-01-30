package com.ch.hammerscale.controller.domain.model.abuse

import java.time.Instant

data class BlockedClient(
    val id: String,
    val clientId: String,
    val reason: String,
    val blockedBy: BlockedBy,
    val blockedAt: Instant,
    val expiresAt: Instant?
)
