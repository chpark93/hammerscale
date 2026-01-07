package com.ch.hammerscale.controller.infrastructure.influxdb

import com.influxdb.client.kotlin.InfluxDBClientKotlin
import com.influxdb.client.kotlin.InfluxDBClientKotlinFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class InfluxDBConfig {

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
    fun influxDBClientKotlin(): InfluxDBClientKotlin {
        // Token이 설정되어 있으면 Token 사용, 없으면 username/password 사용
        return if (token.isNotBlank()) {
            InfluxDBClientKotlinFactory.create(url, token.toCharArray(), org)
        } else {
            InfluxDBClientKotlinFactory.create(url, username, password.toCharArray())
        }
    }

    @Bean
    fun influxDBOrg(): String = org

    @Bean
    fun influxDBBucket(): String = bucket
}

