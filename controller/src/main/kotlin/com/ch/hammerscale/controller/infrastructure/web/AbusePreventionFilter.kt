package com.ch.hammerscale.controller.infrastructure.web

import com.ch.hammerscale.controller.domain.model.abuse.AbuseEventType
import com.ch.hammerscale.controller.domain.port.out.ServiceLoadMetrics
import com.ch.hammerscale.controller.domain.service.AbuseDetectionService
import com.ch.hammerscale.controller.presentation.dto.ApiResponse
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
@Order(1)
class AbusePreventionFilter(
    private val abuseDetectionService: AbuseDetectionService,
    private val serviceLoadMetrics: ServiceLoadMetrics,
    private val objectMapper: ObjectMapper
) : OncePerRequestFilter() {

    private val logger = LoggerFactory.getLogger(AbusePreventionFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val path = request.requestURI ?: ""
        if (path.startsWith("/api/admin") || path.startsWith("/api/health")) {
            filterChain.doFilter(request, response)
            return
        }
        val clientId = resolveClientId(
            request = request
        )
        val startMs = System.currentTimeMillis()

        when (val blockResult = abuseDetectionService.checkBlocked(clientId = clientId)) {
            is AbuseDetectionService.BlockCheckResult.Blocked -> {
                logger.warn("[Abuse] Blocked client access - clientId: $clientId, path: ${request.requestURI}")
                sendErrorResponse(
                    response,
                    HttpStatus.FORBIDDEN.value(),
                    "Access denied. Your client has been blocked."
                )
                serviceLoadMetrics.recordRequest(request.requestURI ?: "", System.currentTimeMillis() - startMs, 403)
                return
            }
            else -> { }
        }

        when (val rateResult = abuseDetectionService.checkRateLimit(clientId = clientId)) {
            is AbuseDetectionService.RateLimitCheckResult.Throttled -> {
                abuseDetectionService.recordAbuse(
                    clientId = clientId,
                    eventType = AbuseEventType.RATE_LIMIT_EXCEEDED,
                    path = request.requestURI,
                    details = "Rate limit exceeded"
                )
                logger.warn("[Abuse] Rate limit exceeded - clientId: $clientId, path: ${request.requestURI}")
                response.setHeader("X-RateLimit-Remaining", rateResult.remaining.toString())
                sendErrorResponse(
                    response,
                    HttpStatus.TOO_MANY_REQUESTS.value(),
                    "Rate limit exceeded. Please try again later."
                )
                serviceLoadMetrics.recordRequest(
                    path = request.requestURI ?: "",
                    latencyMs = System.currentTimeMillis() - startMs,
                    statusCode = 429
                )

                return
            }
            is AbuseDetectionService.RateLimitCheckResult.Allowed -> {
                response.setHeader("X-RateLimit-Remaining", rateResult.remaining.toString())
            }
        }

        try {
            filterChain.doFilter(request, response)
        } finally {
            val status = response.status
            val latencyMs = System.currentTimeMillis() - startMs

            serviceLoadMetrics.recordRequest(
                path = request.requestURI ?: "",
                latencyMs = latencyMs,
                statusCode = status
            )
        }
    }

    private fun resolveClientId(
        request: HttpServletRequest
    ): String {
        val forwarded = request.getHeader("X-Forwarded-For")
        return if (forwarded.isNullOrBlank()) {
            request.remoteAddr ?: "unknown"
        } else {
            forwarded.split(",").firstOrNull()?.trim() ?: request.remoteAddr ?: "unknown"
        }
    }

    private fun sendErrorResponse(
        response: HttpServletResponse,
        status: Int,
        message: String
    ) {
        response.status = status
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        val body = ApiResponse.error<Nothing>(
            message = message,
            code = status.toString()
        )
        response.writer.write(objectMapper.writeValueAsString(body))
    }
}
