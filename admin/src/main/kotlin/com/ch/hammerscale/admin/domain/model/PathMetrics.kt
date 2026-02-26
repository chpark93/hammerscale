package com.ch.hammerscale.admin.domain.model

data class PathMetrics(
    val count: Long,
    val avgLatencyMs: Double,
    val errorCount: Long
)
