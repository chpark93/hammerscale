package com.ch.hammerscale.controller.infrastructure.persistence.table

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object Users : Table("users") {
    val id = varchar("id", 36).uniqueIndex()
    val email = varchar("email", 255).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val name = varchar("name", 100)
    val role = varchar("role", 20)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
}
