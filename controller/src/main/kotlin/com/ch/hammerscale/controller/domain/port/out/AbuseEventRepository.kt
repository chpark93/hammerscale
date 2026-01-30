package com.ch.hammerscale.controller.domain.port.out

import com.ch.hammerscale.controller.domain.model.abuse.AbuseEvent
import java.time.Instant

interface AbuseEventRepository {
    fun save(
        event: AbuseEvent
    )

    fun findByClientId(
        clientId: String,
        since: Instant
    ): List<AbuseEvent>

    fun findRecent(
        limit: Int
    ): List<AbuseEvent>
}
