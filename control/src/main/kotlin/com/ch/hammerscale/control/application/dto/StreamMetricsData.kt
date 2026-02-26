package com.ch.hammerscale.control.application.dto

import com.ch.hammerscale.control.domain.dto.TestMetricData

data class StreamMetricsData(
    val testStatus: String?,
    val newMetrics: List<TestMetricData>,
    val shouldTerminate: Boolean,
    val terminateReason: String?
)
