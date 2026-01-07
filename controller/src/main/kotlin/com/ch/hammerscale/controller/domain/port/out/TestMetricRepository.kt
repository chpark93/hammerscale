package com.ch.hammerscale.controller.domain.port.out

import com.project.common.proto.TestStat

interface TestMetricRepository {
    suspend fun saveMetrics(
        stats: List<TestStat>
    )
}

