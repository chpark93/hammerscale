package com.ch.hammerscale.controller.infrastructure.abuse

import com.ch.hammerscale.controller.domain.model.abuse.RateLimitConfig
import com.ch.hammerscale.controller.domain.port.out.RateLimitStore
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@Component
class InMemoryRateLimitStore(
    @Value($$"${hammerscale.rate-limit.requests-per-minute:120}")
    private val requestsPerMinute: Int,
    @Value($$"${hammerscale.rate-limit.window-seconds:60}")
    private val windowSeconds: Long = 60L
) : RateLimitStore {
    private val config = RateLimitConfig(
        requestsPerMinute = requestsPerMinute
    )
    private val windows = ConcurrentHashMap<String, Window>()

    override fun allowRequest(
        clientId: String
    ): Boolean {
        val now = System.currentTimeMillis()
        val slot = now / (windowSeconds * 1000)
        val windowKey = "$clientId:$slot"
        val w = windows.compute(windowKey) { _, old ->
            if (old == null || now - old.startMs > windowSeconds * 1000) {
                Window(
                    startMs = now,
                    count = AtomicInteger(0)
                )
            } else old
        }!!
        val count = w.count.incrementAndGet()

        return count <= config.requestsPerMinute
    }

    override fun getRemainingRequests(
        clientId: String
    ): Int {
        val now = System.currentTimeMillis()
        val slot = now / (windowSeconds * 1000)
        val windowKey = "$clientId:$slot"
        val w = windows[windowKey] ?: return config.requestsPerMinute

        return (config.requestsPerMinute - w.count.get()).coerceAtLeast(0)
    }

    private data class Window(
        val startMs: Long,
        val count: AtomicInteger
    )
}
