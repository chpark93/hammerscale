package com.ch.hammerscale.admin.domain.port.out

import com.ch.hammerscale.admin.domain.model.abuse.AbuseEvent
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
