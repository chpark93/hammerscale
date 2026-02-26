package com.ch.hammerscale.auth.infrastructure.redis

import com.ch.hammerscale.auth.domain.port.out.TokenStore
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

@Component
class RedisTokenStore(
    private val redis: StringRedisTemplate,
    @Value($$"${hammerscale.jwt.refresh-ttl-seconds:604800}")
    private val refreshTtlSeconds: Long = 604800L
) : TokenStore {

    private val keyRefresh = "auth:refresh:"
    private val keyBlacklist = "auth:blacklist:"

    override fun saveRefreshToken(
        tokenId: String,
        userId: String,
        expiresAt: Instant
    ) {
        val key = keyRefresh + tokenId
        val ttl = Duration.between(Instant.now(), expiresAt).seconds.coerceAtLeast(1)

        redis.opsForValue().set(
            key,
            userId,
            Duration.ofSeconds(ttl)
        )
    }

    override fun consumeRefreshToken(
        tokenId: String
    ): String? {
        val key = keyRefresh + tokenId
        val userId = redis.opsForValue().get(key) ?: return null
        redis.delete(key)

        return userId
    }

    override fun blacklistAccessToken(
        jti: String,
        expiresAt: Instant
    ) {
        val key = keyBlacklist + jti
        val ttl = Duration.between(Instant.now(), expiresAt).seconds.coerceAtLeast(1)

        redis.opsForValue().set(
            key,
            "1",
            Duration.ofSeconds(ttl)
        )
    }

    override fun isBlacklisted(
        jti: String
    ): Boolean = redis.hasKey(keyBlacklist + jti) == true
}
