package com.ch.hammerscale.controller.infrastructure.grpc

class AgentConnectionException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

