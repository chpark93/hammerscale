package com.ch.hammerscale.controller.domain.port.out

import com.ch.hammerscale.controller.domain.model.ServiceLoadSnapshot

interface ServiceLoadMetrics {
    fun recordRequest(
        path: String,
        latencyMs: Long,
        statusCode: Int
    )

    fun getSnapshot(): ServiceLoadSnapshot
}
