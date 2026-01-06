package com.ch.hammerscale.controller.infrastructure.grpc

import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class GrpcClientConfig {

    @Value($$"${grpc.agent.host:localhost}")
    private lateinit var agentHost: String

    @Value($$"${grpc.agent.port:50051}")
    private var agentPort: Int = 50051

    @Bean
    fun agentChannel(): ManagedChannel {
        return ManagedChannelBuilder.forAddress(agentHost, agentPort)
            .usePlaintext()
            .build()
    }
}

