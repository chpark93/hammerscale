package com.ch.hammerscale.controller.domain.port.out

import java.time.Instant

interface TokenStore {
    fun saveRefreshToken(
        tokenId: String,
        userId: String,
        expiresAt: Instant
    )

    fun consumeRefreshToken(
        tokenId: String
    ): String?

    fun blacklistAccessToken(
        jti: String,
        expiresAt: Instant
    )

    fun isBlacklisted(
        jti: String
    ): Boolean
}
