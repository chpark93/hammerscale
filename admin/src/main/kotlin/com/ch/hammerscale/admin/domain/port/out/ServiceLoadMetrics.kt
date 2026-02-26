package com.ch.hammerscale.admin.domain.port.out

import com.ch.hammerscale.admin.domain.model.ServiceLoadSnapshot

interface ServiceLoadMetrics {
    fun recordRequest(
        path: String,
        latencyMs: Long,
        statusCode: Int
    )

    fun getSnapshot(): ServiceLoadSnapshot
}
