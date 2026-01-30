package com.ch.hammerscale.controller.infrastructure.security

import com.ch.hammerscale.controller.domain.model.user.User
import com.ch.hammerscale.controller.domain.model.user.UserRole
import com.ch.hammerscale.controller.domain.port.out.TokenStore
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.*
import javax.crypto.SecretKey

@Service
class JwtService(
    private val tokenStore: TokenStore,
    @Value("\${hammerscale.jwt.secret:default-secret-key-at-least-256-bits-for-hs256-please-change-in-production}")
    private val secret: String,
    @Value("\${hammerscale.jwt.access-ttl-seconds:900}")
    private val accessTtlSeconds: Long = 900L,
    @Value("\${hammerscale.jwt.refresh-ttl-seconds:604800}")
    private val refreshTtlSeconds: Long = 604800L
) {
    private val key: SecretKey by lazy {
        Keys.hmacShaKeyFor(secret.padEnd(32, '0').take(32).toByteArray(Charsets.UTF_8))
    }

    fun createTokenPair(
        user: User
    ): TokenPair {
        val accessJti = UUID.randomUUID().toString()
        val refreshJti = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val accessExp = now + accessTtlSeconds * 1000
        val refreshExp = now + refreshTtlSeconds * 1000

        val accessToken = Jwts.builder()
            .id(accessJti)
            .subject(user.id)
            .claim("email", user.email)
            .claim("role", user.role.name)
            .claim("type", "access")
            .issuedAt(Date(now))
            .expiration(Date(accessExp))
            .signWith(key)
            .compact()

        tokenStore.saveRefreshToken(
            tokenId = refreshJti,
            userId = user.id,
            expiresAt = Date(refreshExp).toInstant()
        )

        val refreshToken = Jwts.builder()
            .id(refreshJti)
            .subject(user.id)
            .claim("type", "refresh")
            .issuedAt(Date(now))
            .expiration(Date(refreshExp))
            .signWith(key)
            .compact()

        return TokenPair(
            accessToken = accessToken,
            refreshToken = refreshToken,
            accessTokenExpiresAt = Date(accessExp).toInstant(),
            refreshTokenExpiresAt = Date(refreshExp).toInstant()
        )
    }

    fun consumeRefreshTokenAndGetUserId(
        refreshToken: String
    ): String? {
        val claims = parseToken(
            token = refreshToken
        ) ?: return null

        if (claims["type"] != "refresh") return null
        val jti = claims.id ?: return null

        return tokenStore.consumeRefreshToken(
            tokenId = jti
        )
    }

    fun parseAccessToken(
        token: String
    ): TokenPayload? {
        val claims = parseToken(
            token = token
        ) ?: return null
        if (claims["type"] != "access") return null
        if (tokenStore.isBlacklisted(claims.id)) return null

        return TokenPayload(
            jti = claims.id,
            userId = claims.subject,
            email = claims["email"] as? String ?: "",
            role = (claims["role"] as? String)?.let { UserRole.valueOf(it) } ?: UserRole.USER,
            expiresAt = claims.expiration.toInstant()
        )
    }

    fun blacklistAccessToken(
        jti: String,
        expiresAt: Instant
    ) {
        tokenStore.blacklistAccessToken(
            jti = jti,
            expiresAt = expiresAt
        )
    }

    private fun parseToken(
        token: String
    ): Claims? {
        return try {
            Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .payload
        } catch (_: Exception) {
            null
        }
    }

    data class TokenPair(
        val accessToken: String,
        val refreshToken: String,
        val accessTokenExpiresAt: Instant,
        val refreshTokenExpiresAt: Instant
    )

    data class TokenPayload(
        val jti: String,
        val userId: String,
        val email: String,
        val role: UserRole,
        val expiresAt: Instant
    )
}
