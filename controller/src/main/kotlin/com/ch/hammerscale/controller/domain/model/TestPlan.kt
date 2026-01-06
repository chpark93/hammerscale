package com.ch.hammerscale.controller.domain.model

import java.time.LocalDateTime
import java.util.UUID

data class TestPlan(
    val id: String,
    val title: String,
    val config: LoadConfig,
    val status: TestStatus,
    val createdAt: LocalDateTime
) {
    init {
        require(id.isNotBlank()) { "id cannot be empty." }
        require(title.isNotBlank()) { "title cannot be empty." }
    }

    fun start(): TestPlan {
        require(status == TestStatus.READY) {
            "Tests can only be started when the status is READY. current status: $status"
        }
        
        return copy(status = TestStatus.RUNNING)
    }

    fun stop(): TestPlan {
        return copy(status = TestStatus.FINISHED)
    }

    fun markAsFailed(): TestPlan {
        return copy(status = TestStatus.FAILED)
    }

    companion object {
        fun create(
            title: String,
            config: LoadConfig
        ): TestPlan {
            return TestPlan(
                id = UUID.randomUUID().toString(),
                title = title,
                config = config,
                status = TestStatus.READY,
                createdAt = LocalDateTime.now()
            )
        }
    }
}

