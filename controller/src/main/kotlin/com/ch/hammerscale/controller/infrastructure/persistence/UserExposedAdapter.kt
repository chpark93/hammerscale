package com.ch.hammerscale.controller.infrastructure.persistence

import com.ch.hammerscale.controller.domain.model.user.User
import com.ch.hammerscale.controller.domain.model.user.UserRole
import com.ch.hammerscale.controller.domain.port.out.UserRepository
import com.ch.hammerscale.controller.infrastructure.persistence.table.Users
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.ZoneId

@Repository
class UserExposedAdapter : UserRepository {

    @Transactional
    override fun save(
        user: User
    ): User {
        val existing = Users
            .selectAll()
            .where {
                Users.id eq user.id
            }.singleOrNull()

        val now = Instant.now()
        return if (existing != null) {
            Users.update({
                Users.id eq user.id
            }) {
                it[email] = user.email
                it[passwordHash] = user.passwordHash
                it[name] = user.name
                it[role] = user.role.name
                it[updatedAt] = now
            }

            user.copy(
                updatedAt = now.atZone(ZoneId.systemDefault()).toInstant()
            )
        } else {
            Users.insert {
                it[id] = user.id
                it[email] = user.email
                it[passwordHash] = user.passwordHash
                it[name] = user.name
                it[role] = user.role.name
                it[createdAt] = now
                it[updatedAt] = now
            }

            user
        }
    }

    @Transactional(readOnly = true)
    override fun findById(
        id: String
    ): User? {
        return Users
            .selectAll()
            .where {
                Users.id eq id
            }.singleOrNull()?.toUser()
    }

    @Transactional(readOnly = true)
    override fun findByEmail(
        email: String
    ): User? {
        return Users
            .selectAll()
            .where {
                Users.email eq email
            }.singleOrNull()?.toUser()
    }

    @Transactional(readOnly = true)
    override fun existsByEmail(
        email: String
    ): Boolean {
        return Users
            .selectAll()
            .where {
                Users.email eq email
            }.count() > 0
    }

    private fun ResultRow.toUser(): User {
        val createdAtInstant = this[Users.createdAt].atZone(ZoneId.systemDefault()).toInstant()
        val updatedAtInstant = this[Users.updatedAt].atZone(ZoneId.systemDefault()).toInstant()

        return User(
            id = this[Users.id],
            email = this[Users.email],
            passwordHash = this[Users.passwordHash],
            name = this[Users.name],
            role = UserRole.valueOf(this[Users.role]),
            createdAt = createdAtInstant,
            updatedAt = updatedAtInstant
        )
    }
}
