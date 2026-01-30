package com.ch.hammerscale.controller.domain.model.abuse

enum class AbuseEventType {
    RATE_LIMIT_EXCEEDED,
    BLOCKED_CLIENT_ACCESS,
    AUTO_BLOCKED_AFTER_VIOLATIONS,
    SUSPICIOUS_PATTERN
}