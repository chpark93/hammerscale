package com.ch.hammerscale.controller.domain.model

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
        
        val urlPattern = Regex(
            "^https?://" +
            "([a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)+" +
            "[a-zA-Z]{2,}" +
            "(/.*)?$"
        )
        
        if (!urlPattern.matches(url)) {
            return false
        }
        
        if (url.length > 2048) {
            return false
        }
        
        return true
    }
}

