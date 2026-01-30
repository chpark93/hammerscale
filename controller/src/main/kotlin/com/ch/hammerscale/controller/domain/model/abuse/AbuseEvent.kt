package com.ch.hammerscale.controller.domain.model.abuse

import java.time.Instant

data class AbuseEvent(
    val id: String,
    val clientId: String,
    val eventType: AbuseEventType,
    val action: AbuseAction,
    val path: String?,
    val details: String?,
    val createdAt: Instant
)
