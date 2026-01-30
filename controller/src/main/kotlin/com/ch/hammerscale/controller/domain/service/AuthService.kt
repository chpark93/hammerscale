package com.ch.hammerscale.controller.domain.service

import com.ch.hammerscale.controller.domain.model.user.User
import com.ch.hammerscale.controller.domain.model.user.UserRole
import com.ch.hammerscale.controller.domain.port.out.UserRepository
import com.ch.hammerscale.controller.infrastructure.security.JwtService
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.*

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService
) {
    fun register(
        email: String,
        rawPassword: String,
        name: String,
        role: UserRole = UserRole.USER
    ): User {
        if (userRepository.existsByEmail(email = email)) {
            throw IllegalArgumentException("Email already registered: $email")
        }
        val user = User(
            id = UUID.randomUUID().toString(),
            email = email,
            passwordHash = passwordEncoder.encode(rawPassword),
            name = name,
            role = role,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        return userRepository.save(
            user = user
        )
    }

    fun login(
        email: String,
        rawPassword: String
    ): JwtService.TokenPair {
        val user = userRepository.findByEmail(email)
            ?: throw IllegalArgumentException("Invalid email or password")

        if (!passwordEncoder.matches(rawPassword, user.passwordHash)) {
            throw IllegalArgumentException("Invalid email or password")
        }

        return jwtService.createTokenPair(
            user = user
        )
    }

    fun logout(
        accessToken: String
    ) {
        val payload = jwtService.parseAccessToken(
            token = accessToken
        ) ?: return

        jwtService.blacklistAccessToken(
            jti = payload.jti,
            expiresAt = payload.expiresAt
        )
    }

    fun refresh(
        refreshToken: String
    ): JwtService.TokenPair {
        val userId = jwtService.consumeRefreshTokenAndGetUserId(
            refreshToken = refreshToken
        ) ?: throw IllegalArgumentException("Invalid or already used refresh token")

        val user = userRepository.findById(userId) ?: throw IllegalArgumentException("User not found")

        return jwtService.createTokenPair(
            user = user
        )
    }

    fun resolveUser(
        accessToken: String
    ): User? {
        val payload = jwtService.parseAccessToken(
            token = accessToken
        ) ?: return null

        return userRepository.findById(
            id = payload.userId
        )
    }
}
