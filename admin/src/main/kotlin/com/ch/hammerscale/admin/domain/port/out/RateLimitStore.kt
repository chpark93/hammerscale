package com.ch.hammerscale.admin.domain.port.out

interface RateLimitStore {
    fun allowRequest(
        clientId: String
    ): Boolean

    fun getRemainingRequests(
        clientId: String
    ): Int
}
