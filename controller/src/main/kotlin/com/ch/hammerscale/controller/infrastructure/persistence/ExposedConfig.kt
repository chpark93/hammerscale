package com.ch.hammerscale.controller.infrastructure.persistence

import com.ch.hammerscale.controller.infrastructure.persistence.table.TestPlans
import com.ch.hammerscale.controller.infrastructure.persistence.table.Users
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Configuration

@Configuration
class ExposedConfig : ApplicationRunner {

    private val logger = LoggerFactory.getLogger(ExposedConfig::class.java)

    override fun run(
        args: ApplicationArguments?
    ) {
        logger.info("[Exposed] Database 테이블 생성 시작")

        transaction {
            SchemaUtils.createMissingTablesAndColumns(TestPlans)
            SchemaUtils.createMissingTablesAndColumns(Users)
        }

        logger.info("[Exposed] TestPlans, Users 테이블 생성 완료")
    }
}
