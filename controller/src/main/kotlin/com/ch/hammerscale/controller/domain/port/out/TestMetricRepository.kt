package com.ch.hammerscale.controller.domain.port.out

import com.ch.hammerscale.controller.domain.dto.TestMetricData
import com.project.common.proto.TestStat
import java.time.Instant

interface TestMetricRepository {
    suspend fun saveMetrics(
        stats: List<TestStat>
    )

    suspend fun getMetrics(
        testId: String,
        startTime: Instant? = null,
        endTime: Instant? = null
    ): List<TestMetricData>
}

