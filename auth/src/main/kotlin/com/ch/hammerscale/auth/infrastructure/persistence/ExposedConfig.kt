package com.ch.hammerscale.auth.infrastructure.persistence

import com.ch.hammerscale.auth.infrastructure.persistence.table.Users
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Configuration

@Configuration
class ExposedConfig : ApplicationRunner {

    private val logger = LoggerFactory.getLogger(ExposedConfig::class.java)

    override fun run(args: ApplicationArguments?) {
        logger.info("[Auth] Database 테이블 생성 시작")
        transaction {
            SchemaUtils.createMissingTablesAndColumns(Users)
        }
        logger.info("[Auth] Users 테이블 생성 완료")
    }
}
