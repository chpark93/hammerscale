package com.ch.hammerscale.auth.domain.port.out

import com.ch.hammerscale.auth.domain.model.user.User

interface UserRepository {
    fun save(
        user: User
    ): User

    fun findById(
        id: String
    ): User?

    fun findByEmail(
        email: String
    ): User?

    fun existsByEmail(
        email: String
    ): Boolean
}
