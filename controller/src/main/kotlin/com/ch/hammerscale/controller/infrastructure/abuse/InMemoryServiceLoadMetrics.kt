package com.ch.hammerscale.controller.infrastructure.abuse

import com.ch.hammerscale.controller.domain.port.out.PathMetrics
import com.ch.hammerscale.controller.domain.port.out.ServiceLoadMetrics
import com.ch.hammerscale.controller.domain.port.out.ServiceLoadSnapshot
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.LongAdder

@Component
class InMemoryServiceLoadMetrics : ServiceLoadMetrics {
    private val totalRequests = AtomicLong(0)
    private val lastMinuteRequests = LongAdder()
    private val lastMinuteStart = AtomicLong(System.currentTimeMillis())
    private val latencySum = LongAdder()
    private val errorCountLastMinute = LongAdder()
    private val byPath = ConcurrentHashMap<String, PathStats>()

    override fun recordRequest(
        path: String,
        latencyMs: Long,
        statusCode: Int
    ) {
        resetWindowIfNeeded()

        totalRequests.incrementAndGet()
        lastMinuteRequests.increment()
        latencySum.add(latencyMs)

        if (statusCode >= 400) errorCountLastMinute.increment()

        byPath.compute(path) { _, old ->
            val stats = old ?: PathStats()
            stats.count.incrementAndGet()
            stats.latencySum.add(latencyMs)
            if (statusCode >= 400) stats.errorCount.increment()

            stats
        }
    }

    override fun getSnapshot(): ServiceLoadSnapshot {
        resetWindowIfNeeded()

        val total = totalRequests.get()
        val lastMin = lastMinuteRequests.sum()
        val latSum = latencySum.sum()
        val pathStats = byPath.mapValues { (_, s) ->
            val counts = s.count.get()
            PathMetrics(
                count = counts,
                avgLatencyMs = if (counts == 0L) 0.0 else s.latencySum.sum().toDouble() / counts,
                errorCount = s.errorCount.sum()
            )
        }

        return ServiceLoadSnapshot(
            totalRequests = total,
            requestsLastMinute = lastMin,
            avgLatencyMs = if (lastMin == 0L) 0.0 else latSum.toDouble() / lastMin,
            errorCountLastMinute = errorCountLastMinute.sum(),
            byPath = pathStats
        )
    }

    private fun resetWindowIfNeeded() {
        val now = System.currentTimeMillis()
        val start = lastMinuteStart.get()
        if (now - start > 60_000) {
            if (lastMinuteStart.compareAndSet(start, now)) {
                lastMinuteRequests.reset()
                errorCountLastMinute.reset()
            }
        }
    }

    private class PathStats {
        val count = AtomicLong(0)
        val latencySum = LongAdder()
        val errorCount = LongAdder()
    }
}
