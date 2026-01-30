package com.ch.hammerscale.controller.domain.model

data class ServiceLoadSnapshot(
    val totalRequests: Long,
    val requestsLastMinute: Long,
    val avgLatencyMs: Double,
    val errorCountLastMinute: Long,
    val byPath: Map<String, PathMetrics>
)