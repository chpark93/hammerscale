package com.ch.hammerscale.agent.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.Executor
import java.util.concurrent.Executors

@Configuration
class VirtualThreadConfig {

    @Bean(name = ["virtualThreadExecutor"])
    fun virtualThreadExecutor(): Executor {
        return Executors.newVirtualThreadPerTaskExecutor()
    }
}

