package com.ch.hammerscale.controller.presentation

import com.ch.hammerscale.controller.domain.model.user.User
import com.ch.hammerscale.controller.domain.model.user.UserRole
import com.ch.hammerscale.controller.domain.service.AuthService
import com.ch.hammerscale.controller.presentation.dto.*
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
@Validated
class AuthController(
    private val authService: AuthService
) {
    @PostMapping("/register")
    fun register(
        @RequestBody @Validated request: RegisterRequest
    ): ApiResponse<UserResponse> {
        val user = authService.register(
            email = request.email,
            rawPassword = request.password,
            name = request.name,
            role = UserRole.USER
        )
        return ApiResponse.success(
            data = toUserResponse(
                user = user
            ),
            message = "Registration successful"
        )
    }

    @PostMapping("/register/admin")
    fun registerAdmin(
        @RequestBody @Validated request: RegisterRequest,
        @Value($$"${hammerscale.admin.secret:}") adminSecret: String,
        requestHttp: HttpServletRequest
    ): ResponseEntity<ApiResponse<UserResponse>> {
        val secret = requestHttp.getHeader("X-Admin-Secret")
        if (adminSecret.isBlank() || secret != adminSecret) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                ApiResponse.error(
                    message = "Forbidden",
                    code = "FORBIDDEN"
                )
            )
        }

        val user = authService.register(
            email = request.email,
            rawPassword = request.password,
            name = request.name,
            role = UserRole.ADMIN
        )

        return ResponseEntity.ok(
            ApiResponse.success(
                data = toUserResponse(
                    user = user
                ),
                message = "Admin registration successful"
            )
        )
    }

    @PostMapping("/login")
    fun login(
        @RequestBody @Validated request: LoginRequest
    ): ApiResponse<TokenResponse> {
        val pair = authService.login(
            email = request.email,
            rawPassword = request.password
        )

        return ApiResponse.success(
            data = TokenResponse(
                accessToken = pair.accessToken,
                refreshToken = pair.refreshToken,
                accessTokenExpiresAt = pair.accessTokenExpiresAt.toString(),
                refreshTokenExpiresAt = pair.refreshTokenExpiresAt.toString()
            ),
            message = "Login successful"
        )
    }

    @PostMapping("/logout")
    fun logout(
        request: HttpServletRequest
    ): ApiResponse<Unit> {
        val token = extractBearerToken(request) ?: return ApiResponse.success(
            message = "Logged out"
        )

        authService.logout(
            accessToken = token
        )

        return ApiResponse.success(
            message = "Logged out successfully"
        )
    }

    @PostMapping("/refresh")
    fun refresh(
        @RequestBody @Validated body: RefreshRequest
    ): ApiResponse<TokenResponse> {
        val pair = authService.refresh(
            refreshToken = body.refreshToken
        )

        return ApiResponse.success(
            data = TokenResponse(
                accessToken = pair.accessToken,
                refreshToken = pair.refreshToken,
                accessTokenExpiresAt = pair.accessTokenExpiresAt.toString(),
                refreshTokenExpiresAt = pair.refreshTokenExpiresAt.toString()
            ),
            message = "Token refreshed successfully"
        )
    }

    private fun extractBearerToken(
        request: HttpServletRequest
    ): String? {
        val auth = request.getHeader("Authorization") ?: return null
        if (!auth.startsWith("Bearer ")) return null

        return auth.removePrefix("Bearer ").trim()
    }

    private fun toUserResponse(
        user: User
    ): UserResponse {
        return UserResponse(
            id = user.id,
            email = user.email,
            name = user.name,
            role = user.role.name,
            createdAt = user.createdAt.toString(),
            updatedAt = user.updatedAt.toString()
        )
    }
}
