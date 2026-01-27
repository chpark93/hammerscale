package com.ch.hammerscale.controller.domain.service.dto

import com.ch.hammerscale.controller.domain.dto.TestMetricData

data class StreamMetricsData(
    val testStatus: String?,
    val newMetrics: List<TestMetricData>,
    val shouldTerminate: Boolean,
    val terminateReason: String?
)
