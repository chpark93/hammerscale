package com.ch.hammerscale.agent.core

import com.project.common.proto.TestStat
import org.HdrHistogram.Histogram
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.LongAdder

class WindowedStatsCollector(
    private val testId: String
) {
    private val totalRequests = LongAdder()
    private val successRequests = LongAdder()
    private val failRequests = LongAdder()
    
    private val totalLatency = LongAdder()
    private val minLatency = AtomicLong(Long.MAX_VALUE)
    private val maxLatency = AtomicLong(Long.MIN_VALUE)
    
    // HdrHistogram for percentile calculation (1ns to 1 hour, 3 significant digits)
    @Volatile
    private var histogram = Histogram(3600000000000L, 3)

    /**
     * 요청 결과 기록
     */
    fun record(
        latencyMs: Long,
        isSuccess: Boolean
    ) {
        // 요청 수 카운트
        totalRequests.increment()
        
        if (isSuccess) {
            successRequests.increment()
        } else {
            failRequests.increment()
        }
        
        // 레이턴시 집계
        totalLatency.add(latencyMs)
        minLatency.updateAndGet { current -> if (current < latencyMs) current else latencyMs }
        maxLatency.updateAndGet { current -> if (current > latencyMs) current else latencyMs }
        
        // 히스토그램에 레이턴시 기록 (밀리초를 마이크로초로 변환)
        try {
            histogram.recordValue(latencyMs * 1000) // ms -> μs
        } catch (e: Exception) {
            // 값이 범위를 벗어나면 무시
        }
    }

    /**
     * 현재 통계를 스냅샷 반환 / 카운터 초기화 (Atomic 연산)
     */
    fun snapshotAndReset(
        activeUsers: Int
    ): TestStat {
        // 현재 값 조회
        val total = totalRequests.sumThenReset()
        val success = successRequests.sumThenReset()
        val fail = failRequests.sumThenReset()
        val latencySum = totalLatency.sumThenReset()
        
        // 레이턴시 통계
        val currentMin = minLatency.getAndSet(Long.MAX_VALUE)
        val currentMax = maxLatency.getAndSet(Long.MIN_VALUE)
        
        // 평균 레이턴시 계산
        val avgLatency = if (total > 0) {
            latencySum.toDouble() / total
        } else {
            0.0
        }
        
        // 최소/최대 레이턴시가 초기값 -> 0으로 설정
        val minLatencyValue = if (currentMin == Long.MAX_VALUE) 0L else currentMin
        val maxLatencyValue = if (currentMax == Long.MIN_VALUE) 0L else currentMax
        
        // TPS 계산
        val tps = total.toInt()
        
        // 히스토그램에서 퍼센타일 계산 (마이크로초 -> 밀리초로 변환)
        val oldHistogram = histogram
        histogram = Histogram(3600000000000L, 3) // 새 히스토그램으로 교체
        
        val p50 = if (oldHistogram.totalCount > 0) {
            oldHistogram.getValueAtPercentile(50.0) / 1000.0 // μs -> ms
        } else 0.0
        
        val p95 = if (oldHistogram.totalCount > 0) {
            oldHistogram.getValueAtPercentile(95.0) / 1000.0
        } else 0.0
        
        val p99 = if (oldHistogram.totalCount > 0) {
            oldHistogram.getValueAtPercentile(99.0) / 1000.0
        } else 0.0
        
        return TestStat.newBuilder()
            .setTestId(testId)
            .setTimestamp(System.currentTimeMillis())
            .setActiveUsers(activeUsers)
            .setRequestsPerSecond(tps)
            .setAvgLatencyMs(avgLatency)
            .setP50LatencyMs(p50)
            .setP95LatencyMs(p95)
            .setP99LatencyMs(p99)
            .setErrorCount(fail.toInt())
            .build()
    }

    /**
     * 현재 통계를 반환 (초기화하지 않음)
     */
    fun getCurrentSnapshot(
        activeUsers: Int
    ): TestStat {
        val total = totalRequests.sum()
        val success = successRequests.sum()
        val fail = failRequests.sum()
        val latencySum = totalLatency.sum()
        
        val avgLatency = if (total > 0) {
            latencySum.toDouble() / total
        } else {
            0.0
        }
        
        val tps = total.toInt()
        
        return TestStat.newBuilder()
            .setTestId(testId)
            .setTimestamp(System.currentTimeMillis())
            .setActiveUsers(activeUsers)
            .setRequestsPerSecond(tps)
            .setAvgLatencyMs(avgLatency)
            .setErrorCount(fail.toInt())
            .build()
    }

    /**
     * 모든 카운터를 초기화
     */
    fun reset() {
        totalRequests.reset()
        successRequests.reset()
        failRequests.reset()
        totalLatency.reset()
        minLatency.set(Long.MAX_VALUE)
        maxLatency.set(Long.MIN_VALUE)
        histogram = Histogram(3600000000000L, 3)
    }
}

