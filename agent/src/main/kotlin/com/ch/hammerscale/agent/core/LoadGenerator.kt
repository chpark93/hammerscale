package com.ch.hammerscale.agent.core

import com.project.common.proto.ReportServiceGrpcKt
import com.project.common.proto.TestConfig
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.LongAdder

@Component
class LoadGenerator(
    private val reportServiceStub: ReportServiceGrpcKt.ReportServiceCoroutineStub
) {

    private val logger = LoggerFactory.getLogger(LoadGenerator::class.java)

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .executor(Executors.newVirtualThreadPerTaskExecutor())
        .build()

    private val requestCount = LongAdder()
    private val errorCount = LongAdder()

    private var executorService: ExecutorService? = null
    private var monitoringThread: Thread? = null
    private var statsCollector: WindowedStatsCollector? = null
    private var statsReporter: StatsReporter? = null
    @Volatile private var isRunning = false
    @Volatile private var stopRequested = false

    fun start(
        config: TestConfig
    ) {
        if (isRunning) {
            logger.warn("Load test is already running. Ignoring start request.")
            return
        }

        logger.info(
            "[LoadGenerator] 부하 테스트 시작 - ID: ${config.testId}, URL: ${config.targetUrl}, " +
            "Users: ${config.virtualUsers}, Duration: ${config.durationSeconds}s, Method: ${config.httpMethod}"
        )

        // 통계 집계기 생성
        statsCollector = WindowedStatsCollector(config.testId)

        // 통계 리포터 생성 및 시작
        stopRequested = false

        // “부하/장애” 상황에서의 보호 로직:
        // - 1초 윈도우 평균 레이턴시가 임계치를 연속으로 넘으면 자동 중단
        val latencyStopThresholdMs = 2_000.0
        val latencyStopConsecutiveWindows = 3
        var highLatencyWindows = 0

        statsReporter = StatsReporter(
            collector = statsCollector!!,
            reportStub = reportServiceStub
        ) { stat ->
            if (stopRequested || !isRunning) return@StatsReporter

            // 통계가 없으면 판단하지 않음
            if (stat.requestsPerSecond <= 0) return@StatsReporter

            if (stat.avgLatencyMs >= latencyStopThresholdMs) {
                highLatencyWindows++
            } else {
                highLatencyWindows = 0
            }

            if (highLatencyWindows >= latencyStopConsecutiveWindows && !stopRequested) {
                stopRequested = true
                // StatsReporter 코루틴 안에서 바로 stop()을 호출하면 자기 자신을 cancel할 수 있어 별도 스레드에서 정리
                Thread {
                    logger.error(
                        "[LoadGenerator] Avg latency SLO violated for $highLatencyWindows windows " +
                            "(>=${latencyStopThresholdMs}ms). Stopping test. testId=${config.testId}"
                    )
                    stop()
                }.start()
            }
        }
        statsReporter?.start(config.virtualUsers)

        // 통계 초기화
        requestCount.reset()
        errorCount.reset()
        isRunning = true

        // ExecutorService 생성
        executorService = Executors.newVirtualThreadPerTaskExecutor()

        // 각 Virtual User에 대해 작업 제출
        val startTime = System.currentTimeMillis()
        val endTime = startTime + (config.durationSeconds * 1000L)

        repeat(config.virtualUsers) { userIndex ->
            executorService?.submit {
                runLoadTest(config, endTime, userIndex)
            }
        }

        // 모니터링 스레드 시작
        startMonitoring(config.testId, startTime, config.durationSeconds)

        logger.info("[LoadGenerator] ${config.virtualUsers}개의 Virtual Thread가 시작되었습니다.")
    }

    private fun runLoadTest(
        config: TestConfig,
        endTime: Long,
        userIndex: Int
    ) {
        val uri = URI.create(config.targetUrl)
        val httpMethod = config.httpMethod.uppercase()
        var consecutiveErrors = 0
        val maxConsecutiveErrors = 10  // 연속 10번 에러 시 해당 스레드 중단
        var totalRequestCount = 0L // 해당 스레드의 총 요청 수

        try {
            while (System.currentTimeMillis() < endTime && isRunning) {
                val startRequestTime = System.currentTimeMillis()
                
                try {
                    val request = when (httpMethod) {
                        "GET" -> HttpRequest.newBuilder()
                            .uri(uri)
                            .GET()
                            .timeout(Duration.ofSeconds(10))
                            .build()

                        "POST" -> HttpRequest.newBuilder()
                            .uri(uri)
                            .POST(HttpRequest.BodyPublishers.ofString(""))
                            .timeout(Duration.ofSeconds(10))
                            .build()

                        else -> {
                            logger.warn("Unsupported HTTP method: $httpMethod, using GET")
                            HttpRequest.newBuilder()
                                .uri(uri)
                                .GET()
                                .timeout(Duration.ofSeconds(10))
                                .build()
                        }
                    }

                    val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
                    val latency = System.currentTimeMillis() - startRequestTime

                    requestCount.increment()
                    totalRequestCount++

                    val statusCode = response.statusCode()
                    val isSuccess = statusCode in 200..399
                    
                    if (!isSuccess) {
                        errorCount.increment()
                        consecutiveErrors++
                        if (totalRequestCount <= 3) {
                            logger.warn(
                                "[LoadGenerator] User $userIndex - Request failed (req #$totalRequestCount): " +
                                    "Status=$statusCode, Latency=${latency}ms, URL=${config.targetUrl}"
                            )
                        }
                    } else {
                        consecutiveErrors = 0
                        if (logger.isDebugEnabled && totalRequestCount <= 3) {
                            logger.debug(
                                "[LoadGenerator] User $userIndex - Request success (req #$totalRequestCount): " +
                                    "Status=$statusCode, Latency=${latency}ms"
                            )
                        }
                    }
                    
                    statsCollector?.record(latency, isSuccess)

                    Thread.sleep(10)

                } catch (e: Exception) {
                    errorCount.increment()
                    requestCount.increment()
                    consecutiveErrors++
                    
                    // 예외 발생 시 latency 측정
                    val latency = System.currentTimeMillis() - startRequestTime
                    
                    // 예외 발생 시에도 통계 기록
                    statsCollector?.record(latency, false)

                    if (consecutiveErrors <= 3) {
                        logger.warn(
                            "[LoadGenerator] User $userIndex - Request error (${consecutiveErrors}/${maxConsecutiveErrors}): " +
                                "${e.javaClass.simpleName} - ${e.message?.take(120)}"
                        )
                    }
                    
                    if (consecutiveErrors >= maxConsecutiveErrors) {
                        logger.error(
                            "[LoadGenerator] User $userIndex - Too many consecutive errors ($consecutiveErrors). " +
                            "Stopping this thread. Target: ${config.targetUrl}"
                        )
                        break
                    }
                    
                    // 전체 에러율이 80% 이상이고 총 요청이 100개 이상이면 모든 스레드 중단
                    val totalRequests = requestCount.sum()
                    val totalErrors = errorCount.sum()
                    if (totalRequests >= 100) {
                        val errorRate = (totalErrors.toDouble() / totalRequests) * 100
                        if (errorRate >= 80.0) {
                            logger.error(
                                "[LoadGenerator] 전체 에러율이 ${"%.2f".format(errorRate)}%로 높습니다. " +
                                "서버 다운 가능성이 있습니다. 모든 스레드 중단."
                            )
                            isRunning = false
                            break
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("[LoadGenerator] User $userIndex - Unexpected error: ${e.message}", e)
        }
    }

    private fun startMonitoring(
        testId: String,
        startTime: Long,
        durationSeconds: Int
    ) {
        val endTime = startTime + (durationSeconds * 1000L)
        
        monitoringThread = Thread {
            var lastRequestCount = 0L
            var lastErrorCount = 0L
            var lastCheckTime = System.currentTimeMillis()

            while (isRunning && System.currentTimeMillis() < endTime) {
                try {
                    Thread.sleep(1000)

                    val currentRequestCount = requestCount.sum()
                    val currentErrorCount = errorCount.sum()
                    val currentTime = System.currentTimeMillis()

                    val elapsedSeconds = (currentTime - lastCheckTime) / 1000.0
                    if (elapsedSeconds > 0) {
                        val requestsDelta = currentRequestCount - lastRequestCount
                        val tps = requestsDelta / elapsedSeconds

                        logger.info(
                            "[LoadGenerator] Test ID: $testId | " +
                            "Total Requests: $currentRequestCount | " +
                            "Total Errors: $currentErrorCount | " +
                            "TPS: %.2f | ".format(tps) +
                            "Error Rate: %.2f%%".format(
                                if (currentRequestCount > 0) {
                                    (currentErrorCount * 100.0 / currentRequestCount)
                                } else {
                                    0.0
                                }
                            )
                        )
                    }

                    lastRequestCount = currentRequestCount
                    lastErrorCount = currentErrorCount
                    lastCheckTime = currentTime

                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
            
            if (System.currentTimeMillis() >= endTime && isRunning) {
                logger.info("[LoadGenerator] 테스트 시간 종료 - 자동 정리 시작")
                stop()
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        if (!isRunning) {
            logger.warn("Load test is not running. Ignoring stop request.")
            return
        }

        logger.info("[LoadGenerator] 부하 테스트 중지 요청")

        isRunning = false

        // 통계 리포터 중지
        statsReporter?.stop()
        statsReporter = null

        executorService?.shutdown()

        try {
            if (!executorService?.awaitTermination(5, TimeUnit.SECONDS)!!) {
                executorService?.shutdownNow()
            }
        } catch (e: InterruptedException) {
            executorService?.shutdownNow()
            Thread.currentThread().interrupt()
        }

        monitoringThread?.interrupt()
        try {
            monitoringThread?.join(1000)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            // 정상적인 종료 과정이므로 로그 출력하지 않음
        }

        // 통계 집계기 정리
        statsCollector = null

        logger.info(
            "[LoadGenerator] 부하 테스트 종료 - " +
            "Total Requests: ${requestCount.sum()}, Total Errors: ${errorCount.sum()}"
        )
    }

    fun getStats(): LoadGeneratorStats {
        return LoadGeneratorStats(
            requestCount = requestCount.sum(),
            errorCount = errorCount.sum()
        )
    }
}

data class LoadGeneratorStats(
    val requestCount: Long,
    val errorCount: Long
)

