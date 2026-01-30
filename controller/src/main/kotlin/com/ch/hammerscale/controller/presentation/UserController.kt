package com.ch.hammerscale.controller.presentation

import com.ch.hammerscale.controller.domain.model.user.User
import com.ch.hammerscale.controller.domain.service.UserService
import com.ch.hammerscale.controller.presentation.dto.ApiResponse
import com.ch.hammerscale.controller.presentation.dto.UpdateProfileRequest
import com.ch.hammerscale.controller.presentation.dto.UserResponse
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/user")
class UserController(
    private val userService: UserService
) {
    @GetMapping("/me")
    fun me(
        @AuthenticationPrincipal user: User
    ): ApiResponse<UserResponse> {
        val profile = userService.getProfile(
            userId = user.id
        )

        return ApiResponse.success(
            data = toUserResponse(
                user = profile
            ),
            message = "Profile retrieved successfully"
        )
    }

    @PutMapping("/me")
    fun updateMe(
        @AuthenticationPrincipal user: User,
        @RequestBody request: UpdateProfileRequest
    ): ApiResponse<UserResponse> {
        val updatedUser = userService.updateProfile(
            userId = user.id,
            name = request.name,
            currentPassword = request.currentPassword,
            newPassword = request.newPassword
        )

        return ApiResponse.success(
            data = toUserResponse(
                user = updatedUser
            ),
            message = "Profile updated successfully"
        )
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
