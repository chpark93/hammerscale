package com.ch.hammerscale.controller.infrastructure.persistence

import com.ch.hammerscale.controller.domain.model.HttpMethod
import com.ch.hammerscale.controller.domain.model.LoadConfig
import com.ch.hammerscale.controller.domain.model.TestPlan
import com.ch.hammerscale.controller.domain.model.TestStatus
import com.ch.hammerscale.controller.domain.port.out.TestPlanRepository
import com.ch.hammerscale.controller.infrastructure.persistence.table.TestPlans
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.ZoneId

@Repository
class TestPlanExposedAdapter(
    private val objectMapper: ObjectMapper
) : TestPlanRepository {

    @Transactional
    override fun save(
        testPlan: TestPlan
    ): TestPlan {
        val existing = TestPlans.selectAll().where {
            TestPlans.id eq testPlan.id
        }.singleOrNull()

        val headersJson = if (testPlan.config.headers.isNotEmpty()) {
            objectMapper.writeValueAsString(testPlan.config.headers)
        } else null

        val queryParamsJson = if (testPlan.config.queryParams.isNotEmpty()) {
            objectMapper.writeValueAsString(testPlan.config.queryParams)
        } else null

        return if (existing != null) {
            TestPlans.update({
                TestPlans.id eq testPlan.id
            }) {
                it[title] = testPlan.title
                it[targetUrl] = testPlan.config.targetUrl
                it[virtualUsers] = testPlan.config.virtualUsers
                it[durationSeconds] = testPlan.config.durationSeconds
                it[method] = testPlan.config.method.name
                it[status] = testPlan.status.name
                it[createdAt] = testPlan.createdAt.atZone(ZoneId.systemDefault()).toInstant()
                it[headers] = headersJson
                it[queryParams] = queryParamsJson
                it[requestBody] = testPlan.config.requestBody
            }

            testPlan
        } else {
            TestPlans.insert {
                it[id] = testPlan.id
                it[title] = testPlan.title
                it[targetUrl] = testPlan.config.targetUrl
                it[virtualUsers] = testPlan.config.virtualUsers
                it[durationSeconds] = testPlan.config.durationSeconds
                it[method] = testPlan.config.method.name
                it[status] = testPlan.status.name
                it[createdAt] = testPlan.createdAt.atZone(ZoneId.systemDefault()).toInstant()
                it[headers] = headersJson
                it[queryParams] = queryParamsJson
                it[requestBody] = testPlan.config.requestBody
            }

            testPlan
        }
    }

    @Transactional(readOnly = true)
    override fun findById(
        id: String
    ): TestPlan? {
        return TestPlans
            .selectAll()
            .where {
                TestPlans.id eq id
            }
            .singleOrNull()
            ?.toDomain()
    }

    private fun ResultRow.toDomain(): TestPlan {
        val headersJson = this[TestPlans.headers]
        val queryParamsJson = this[TestPlans.queryParams]

        val headers: Map<String, String> = if (headersJson != null) {
            objectMapper.readValue(headersJson, object : TypeReference<Map<String, String>>() {})
        } else emptyMap()

        val queryParams: Map<String, String> = if (queryParamsJson != null) {
            objectMapper.readValue(queryParamsJson, object : TypeReference<Map<String, String>>() {})
        } else emptyMap()

        return TestPlan(
            id = this[TestPlans.id],
            title = this[TestPlans.title],
            config = LoadConfig(
                targetUrl = this[TestPlans.targetUrl],
                virtualUsers = this[TestPlans.virtualUsers],
                durationSeconds = this[TestPlans.durationSeconds],
                method = HttpMethod.valueOf(this[TestPlans.method]),
                headers = headers,
                queryParams = queryParams,
                requestBody = this[TestPlans.requestBody]
            ),
            status = TestStatus.valueOf(this[TestPlans.status]),
            createdAt = LocalDateTime.ofInstant(
                this[TestPlans.createdAt],
                ZoneId.systemDefault()
            )
        )
    }
}
