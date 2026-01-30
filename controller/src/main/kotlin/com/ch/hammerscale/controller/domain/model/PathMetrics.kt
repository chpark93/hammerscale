package com.ch.hammerscale.controller.domain.model

data class PathMetrics(
    val count: Long,
    val avgLatencyMs: Double,
    val errorCount: Long
)