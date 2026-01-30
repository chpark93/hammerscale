package com.ch.hammerscale.controller.domain.model.user

import java.time.Instant

data class User(
    val id: String,
    val email: String,
    val passwordHash: String,
    val name: String,
    val role: UserRole,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    fun isAdmin(): Boolean = role == UserRole.ADMIN
}
