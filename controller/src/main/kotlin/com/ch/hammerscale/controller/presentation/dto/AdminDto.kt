package com.ch.hammerscale.controller.presentation.dto

data class AbuseEventResponse(
    val id: String,
    val clientId: String,
    val eventType: String,
    val action: String,
    val path: String?,
    val details: String?,
    val createdAt: String
)

data class BlockedClientResponse(
    val id: String,
    val clientId: String,
    val reason: String,
    val blockedBy: String,
    val blockedAt: String,
    val expiresAt: String?
)

data class BlockClientRequest(
    val clientId: String,
    val reason: String,
    val durationMinutes: Int? = null
)

data class ServiceLoadResponse(
    val totalRequests: Long,
    val requestsLastMinute: Long,
    val avgLatencyMs: Double,
    val errorCountLastMinute: Long,
    val byPath: Map<String, PathMetricsResponse>
)

data class PathMetricsResponse(
    val count: Long,
    val avgLatencyMs: Double,
    val errorCount: Long
)
