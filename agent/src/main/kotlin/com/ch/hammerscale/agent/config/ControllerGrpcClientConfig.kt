package com.ch.hammerscale.agent.config

import com.project.common.proto.ReportServiceGrpcKt
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ControllerGrpcClientConfig {

    @Value("\${grpc.controller.host:localhost}")
    private lateinit var controllerHost: String

    @Value("\${grpc.controller.port:9090}")
    private var controllerPort: Int = 9090

    @Bean
    fun controllerChannel(): ManagedChannel {
        return ManagedChannelBuilder.forAddress(controllerHost, controllerPort)
            .usePlaintext()
            .build()
    }

    @Bean
    fun reportServiceStub(
        controllerChannel: ManagedChannel
    ): ReportServiceGrpcKt.ReportServiceCoroutineStub {
        return ReportServiceGrpcKt.ReportServiceCoroutineStub(controllerChannel)
    }
}

