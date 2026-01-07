package com.ch.hammerscale.agent

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.http.client.HttpClientAutoConfiguration
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration
import org.springframework.boot.runApplication

@SpringBootApplication(
    exclude = [
        HttpClientAutoConfiguration::class,
        RestClientAutoConfiguration::class
    ]
)
class AgentApplication

fun main(args: Array<String>) {
	runApplication<AgentApplication>(*args)
}

