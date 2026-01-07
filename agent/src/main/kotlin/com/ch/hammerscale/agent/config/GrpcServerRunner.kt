package com.ch.hammerscale.agent.config

import com.ch.hammerscale.agent.grpc.AgentServiceGrpcImpl
import io.grpc.Server
import io.grpc.ServerBuilder
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

@Component
class GrpcServerRunner(
    private val agentService: AgentServiceGrpcImpl
) : ApplicationRunner, DisposableBean {

    private val logger = LoggerFactory.getLogger(GrpcServerRunner::class.java)
    private var server: Server? = null

    override fun run(
        args: ApplicationArguments?
    ) {
        server = ServerBuilder.forPort(50051)
            .addService(agentService)
            .build()
            .also { it.start() }
        
        logger.info("gRPC Agent Server started on port 50051")
        
        Thread {
            try {
                server?.awaitTermination()
            } catch (e: InterruptedException) {
                logger.warn("gRPC server awaitTermination interrupted", e)
            }
        }.start()
    }

    override fun destroy() {
        server?.shutdown()
        logger.info("gRPC Agent Server shutdown")
    }
}

