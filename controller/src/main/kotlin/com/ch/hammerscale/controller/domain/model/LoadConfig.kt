package com.ch.hammerscale.controller.domain.model

import java.net.URI

data class LoadConfig(
    val testType: TestType = TestType.LOAD,
    val targetUrl: String,
    val virtualUsers: Int = 0, // LOAD 테스트에서만 사용
    val durationSeconds: Int = 0, // LOAD 테스트에서만 사용
    val method: HttpMethod,
    val headers: Map<String, String> = emptyMap(),
    val queryParams: Map<String, String> = emptyMap(),
    val requestBody: String? = null,
    val rampUpSeconds: Int = 0, // LOAD 테스트에서만 사용 (0 = 즉시 시작)
    val stressTestConfig: StressTestConfig? = null, // STRESS 테스트에서만 사용
    val spikeTestConfig: SpikeTestConfig? = null // SPIKE 테스트에서만 사용
) {
    init {
        require(targetUrl.isNotBlank()) { "targetUrl cannot be empty." }
        require(isValidUrl(targetUrl)) { "targetUrl must be a valid URL format. input value: $targetUrl" }

        when (testType) {
            TestType.LOAD, TestType.SOAK -> {
                require(virtualUsers >= 1) { "virtualUsers must be at least 1 for $testType test. input value: $virtualUsers" }
                require(durationSeconds >= 1) { "durationSeconds must be at least 1 for $testType test. input value: $durationSeconds" }
                require(rampUpSeconds >= 0) { "rampUpSeconds must be non-negative. input value: $rampUpSeconds" }
                require(rampUpSeconds <= durationSeconds) { "rampUpSeconds ($rampUpSeconds) cannot be greater than durationSeconds ($durationSeconds)" }
            }
            TestType.STRESS -> {
                requireNotNull(stressTestConfig) { "stressTestConfig is required for STRESS test" }
            }
            TestType.SPIKE -> {
                requireNotNull(spikeTestConfig) { "spikeTestConfig is required for SPIKE test" }
            }
        }

        // POST / PUT / PATCH는 일반적으로 Body를 가질 수 있음
        if (method in listOf(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH)) {
            // Body가 있으면 Content-Type 헤더 권장
            if (requestBody != null && requestBody.isNotBlank() && !headers.containsKey("Content-Type")) {
                // 경고는 로깅으로 처리 또는 통과
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

