package com.ch.hammerscale.control.infrastructure.grpc

class AgentConnectionException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
