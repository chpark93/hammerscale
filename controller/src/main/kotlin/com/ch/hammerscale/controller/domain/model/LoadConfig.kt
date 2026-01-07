package com.ch.hammerscale.controller.domain.model

import java.net.URI

data class LoadConfig(
    val targetUrl: String,
    val virtualUsers: Int,
    val durationSeconds: Int,
    val method: HttpMethod
) {
    init {
        require(targetUrl.isNotBlank()) { "targetUrl cannot be empty." }
        require(isValidUrl(targetUrl)) { "targetUrl must be a valid URL format. input value: $targetUrl" }
        require(virtualUsers >= 1) { "virtualUsers must be at least 1. input value: $virtualUsers" }
        require(durationSeconds >= 1) { "durationSeconds must be greater than or equal to 1. input value: $durationSeconds" }
    }

    private fun isValidUrl(
        url: String
    ): Boolean {
        if (url.isBlank()) return false
        if (url.length > 2048) return false

        return try {
            val uri = URI(url)

            val schemeOk = uri.scheme == "http" || uri.scheme == "https"
            if (!schemeOk) return false

            // URI 파서 기준으로 host가 없으면 (예: "http://localhost:8080"은 OK)
            val host = uri.host ?: return false
            if (host.isBlank()) return false

            val port = uri.port
            if (port != -1 && (port < 1 || port > 65535)) return false

            true
        } catch (_: Exception) {
            false
        }
    }
}

