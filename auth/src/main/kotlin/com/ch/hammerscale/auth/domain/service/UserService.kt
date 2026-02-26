package com.ch.hammerscale.auth.domain.service

import com.ch.hammerscale.auth.domain.model.user.User
import com.ch.hammerscale.auth.domain.port.out.UserRepository
import com.project.common.exception.ResourceNotFoundException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class UserService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder
) {
    fun getProfile(
        userId: String
    ): User {
        return userRepository.findById(
            id = userId
        ) ?: throw ResourceNotFoundException("User not found", "User", userId)
    }

    fun updateProfile(
        userId: String,
        name: String?,
        currentPassword: String?,
        newPassword: String?
    ): User {
        val user = userRepository.findById(
            id = userId
        ) ?: throw ResourceNotFoundException(
            message = "User not found",
            resourceType = "User",
            resourceId = userId
        )

        val newName = name ?: user.name
        val newHash = when {
            newPassword.isNullOrBlank() -> user.passwordHash
            else -> {
                if (currentPassword.isNullOrBlank() || !passwordEncoder.matches(currentPassword, user.passwordHash)) {
                    throw IllegalArgumentException("Current password is incorrect")
                }

                passwordEncoder.encode(newPassword)
            }
        }
        val updated = user.copy(
            name = newName,
            passwordHash = newHash,
            updatedAt = Instant.now()
        )

        return userRepository.save(
            user = updated
        )
    }
}
