package com.ch.hammerscale.control.infrastructure.influxdb

import com.influxdb.client.kotlin.InfluxDBClientKotlin
import com.influxdb.client.kotlin.InfluxDBClientKotlinFactory
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Lazy

@Configuration
class InfluxDBConfig {

    private val logger = LoggerFactory.getLogger(InfluxDBConfig::class.java)

    @Value($$"${influxdb.url:http://localhost:8086}")
    private lateinit var url: String

    @Value($$"${influxdb.token:}")
    private lateinit var token: String

    @Value($$"${influxdb.org:hammerscale}")
    private lateinit var org: String

    @Value($$"${influxdb.bucket:metrics}")
    private lateinit var bucket: String

    @Value($$"${influxdb.username:root}")
    private lateinit var username: String

    @Value($$"${influxdb.password:1234}")
    private lateinit var password: String

    @Bean
    @Lazy
    fun influxDBClientKotlin(): InfluxDBClientKotlin {
        return try {
            if (token.isNotBlank()) {
                InfluxDBClientKotlinFactory.create(
                    url = url,
                    token = token.toCharArray(),
                    org = org
                )
            } else {
                InfluxDBClientKotlinFactory.create(
                    url = url,
                    username = username,
                    password = password.toCharArray()
                )
            }
        } catch (e: Exception) {
            logger.warn("[InfluxDB] 연결 실패 (메트릭 저장 불가): {}. InfluxDB 기동 후 메트릭이 저장됩니다.", e.message)
            throw e
        }
    }

    @Bean
    fun influxDBOrg(): String = org

    @Bean
    fun influxDBBucket(): String = bucket
}
