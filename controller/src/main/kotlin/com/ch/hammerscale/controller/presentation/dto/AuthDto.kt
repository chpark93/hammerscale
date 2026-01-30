package com.ch.hammerscale.controller.presentation.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class RegisterRequest(
    @field:NotBlank
    @field:Email
    val email: String,
    @field:NotBlank
    @field:Size(min = 8, max = 100)
    val password: String,
    @field:NotBlank
    @field:Size(max = 100)
    val name: String
)

data class LoginRequest(
    @field:NotBlank
    @field:Email
    val email: String,
    @field:NotBlank
    val password: String
)

data class RefreshRequest(
    @field:NotBlank
    val refreshToken: String
)

data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpiresAt: String,
    val refreshTokenExpiresAt: String,
    val tokenType: String = "Bearer"
)

data class UserResponse(
    val id: String,
    val email: String,
    val name: String,
    val role: String,
    val createdAt: String,
    val updatedAt: String
)

data class UpdateProfileRequest(
    @field:Size(max = 100)
    val name: String? = null,
    val currentPassword: String? = null,
    @field:Size(min = 8, max = 100)
    val newPassword: String? = null
)
