package com.ch.hammerscale.control.domain.model

data class PathMetrics(
    val count: Long,
    val avgLatencyMs: Double,
    val errorCount: Long
)
