package com.ch.hammerscale.controller.domain.model.abuse

data class RateLimitConfig(
    val requestsPerMinute: Int,
    val burstSize: Int = requestsPerMinute,
    val autoBlockAfterViolations: Int = 0,
    val autoBlockDurationMinutes: Int = 60
)
