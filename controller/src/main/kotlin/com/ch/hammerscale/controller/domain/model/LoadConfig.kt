package com.ch.hammerscale.controller.domain.model

import java.net.URI

data class LoadConfig(
    val targetUrl: String,
    val virtualUsers: Int,
    val durationSeconds: Int,
    val method: HttpMethod,
    val headers: Map<String, String> = emptyMap(),
    val queryParams: Map<String, String> = emptyMap(),
    val requestBody: String? = null,
    val rampUpSeconds: Int = 0  // 0 = 즉시 시작, >0 = 점진적 증가
) {
    init {
        require(targetUrl.isNotBlank()) { "targetUrl cannot be empty." }
        require(isValidUrl(targetUrl)) { "targetUrl must be a valid URL format. input value: $targetUrl" }
        require(virtualUsers >= 1) { "virtualUsers must be at least 1. input value: $virtualUsers" }
        require(durationSeconds >= 1) { "durationSeconds must be greater than or equal to 1. input value: $durationSeconds" }
        require(rampUpSeconds >= 0) { "rampUpSeconds must be greater than or equal to 0. input value: $rampUpSeconds" }
        require(rampUpSeconds <= durationSeconds) { "rampUpSeconds ($rampUpSeconds) cannot be greater than durationSeconds ($durationSeconds)" }

        // TODO: POST/PUT/PATCH의 경우 -> 추가 로직 필요
        // POST/PUT/PATCH는 일반적으로 Body를 가질 수 있음
        if (method in listOf(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH)) {
            // Body가 있으면 Content-Type 헤더 권장 (하지만 필수는 아님)
            if (requestBody != null && requestBody.isNotBlank() && !headers.containsKey("Content-Type")) {
                // 경고는 로깅으로 처리하거나, 여기서는 통과
            }
        }
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

            val host = uri.host ?: return false
            if (host.isBlank()) return false

            val port = uri.port
            if (port != -1 && (port !in 1..65535)) return false

            true
        } catch (_: Exception) {
            false
        }
    }
}

