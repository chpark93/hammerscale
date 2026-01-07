package com.ch.hammerscale.controller.infrastructure.persistence.table

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object TestPlans : Table("test_plans") {
    val id = varchar("id", 36).uniqueIndex()
    val title = varchar("title", 100)
    val targetUrl = varchar("target_url", 255)
    val virtualUsers = integer("virtual_users")
    val durationSeconds = integer("duration_seconds")
    val method = varchar("method", 10)
    val status = varchar("status", 20)
    val createdAt = timestamp("created_at")
}
