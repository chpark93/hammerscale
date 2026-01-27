package com.ch.hammerscale.controller.presentation.exception

class ResourceNotFoundException(
    message: String,
    val resourceType: String? = null,
    val resourceId: String? = null
) : RuntimeException(message)
