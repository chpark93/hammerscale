package com.ch.hammerscale.controller.presentation

import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import kotlin.math.max

@RestController
@RequestMapping("/__target")
class LoadTargetController {

    private val logger = LoggerFactory.getLogger(LoadTargetController::class.java)

    /**
     * 정상 응답(200) + 지정된 지연(ms) + (선택) CPU 바쁨 시간(ms)
     */
    @GetMapping("/ok")
    fun ok(
        @RequestParam(required = false, defaultValue = "0") delayMs: Long,
        @RequestParam(required = false, defaultValue = "0") cpuMs: Long
    ): String {
        if (delayMs > 0) Thread.sleep(delayMs)
        if (cpuMs > 0) burnCpu(cpuMs)
        return "OK"
    }

    /**
     * 지정한 비율로 에러를 섞는다. (0.0 ~ 1.0)
     */
    @GetMapping("/flaky")
    fun flaky(
        response: HttpServletResponse,
        @RequestParam(required = false, defaultValue = "0.0") errorRate: Double,
        @RequestParam(required = false, defaultValue = "0") delayMs: Long,
        @RequestParam(required = false, defaultValue = "0") cpuMs: Long
    ): String {
        val rate = errorRate.coerceIn(0.0, 1.0)
        if (delayMs > 0) Thread.sleep(delayMs)
        if (cpuMs > 0) burnCpu(cpuMs)

        val isError = Math.random() < rate
        if (isError) {
            response.status = 500
            return "ERROR"
        }

        return "OK"
    }

    /**
     * 타임아웃/지연 상황을 만들기 위해, delayMs를 크게 준다.
     */
    @GetMapping("/slow")
    fun slow(
        @RequestParam(required = false, defaultValue = "0") delayMs: Long
    ): String {
        val d = max(0L, delayMs)
        if (d > 0) {
            logger.info("[Target] slow endpoint sleeping ${d}ms")
            Thread.sleep(d)
        }
        return "SLOW_OK"
    }

    private fun burnCpu(
        cpuMs: Long
    ) {
        val end = System.nanoTime() + cpuMs.coerceAtLeast(0) * 1_000_000
        var x = 0L
        while (System.nanoTime() < end) {
            x = x xor 0x9E3779B97F4A7C15uL.toLong()
            x = x * 0xBF58476D1CE4E5B9uL.toLong() + 0x94D049BB133111EBuL.toLong()
        }
        if (x == 42L) logger.debug("burnCpu noop: $x")
    }
}


