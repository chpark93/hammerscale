package com.ch.hammerscale.control.domain.model

import java.net.URI

data class LoadConfig(
    val testType: TestType = TestType.LOAD,
    val targetUrl: String,
    val virtualUsers: Int = 0,
    val durationSeconds: Int = 0,
    val method: HttpMethod,
    val headers: Map<String, String> = emptyMap(),
    val queryParams: Map<String, String> = emptyMap(),
    val requestBody: String? = null,
    val rampUpSeconds: Int = 0,
    val stressTestConfig: StressTestConfig? = null,
    val spikeTestConfig: SpikeTestConfig? = null
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
