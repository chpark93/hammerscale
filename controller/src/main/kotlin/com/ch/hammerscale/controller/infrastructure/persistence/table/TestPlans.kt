package com.ch.hammerscale.controller.infrastructure.persistence.table

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object TestPlans : Table("test_plans") {
    val id = varchar("id", 36).uniqueIndex()
    val title = varchar("title", 100)
    val targetUrl = varchar("target_url", 255)
    val testType = varchar("test_type", 10).default("LOAD") // LOAD 또는 STRESS
    val virtualUsers = integer("virtual_users").default(0) // LOAD 타입에서 사용
    val durationSeconds = integer("duration_seconds").default(0) // LOAD 타입에서 사용
    val rampUpSeconds = integer("ramp_up_seconds").default(0) // LOAD 타입에서 사용
    val method = varchar("method", 10)
    val status = varchar("status", 20)
    val createdAt = timestamp("created_at")
    val headers = text("headers").nullable() // JSON 문자열
    val queryParams = text("query_params").nullable() // JSON 문자열
    val requestBody = text("request_body").nullable()

    // Stress Test 전용 필드
    val stressStartUsers = integer("stress_start_users").nullable()
    val stressMaxUsers = integer("stress_max_users").nullable()
    val stressStepDuration = integer("stress_step_duration").nullable()
    val stressStepIncrement = integer("stress_step_increment").nullable()

    // Spike Test 전용 필드
    val spikeBaseUsers = integer("spike_base_users").nullable()
    val spikeSpikeUsers = integer("spike_spike_users").nullable()
    val spikeSpikeDuration = integer("spike_spike_duration").nullable()
    val spikeRecoveryDuration = integer("spike_recovery_duration").nullable()
}
