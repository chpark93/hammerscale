package com.ch.hammerscale.agent.core

import com.ch.hammerscale.agent.core.dto.LoadGeneratorStats
import com.project.common.proto.ReportServiceGrpcKt
import com.project.common.proto.TestConfig
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.net.URI
import java.net.URLEncoder
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
    private val activeUserCount = LongAdder() // 현재 활성 Virtual User 수 추적

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

        when (val testType = config.testType.uppercase()) {
            "LOAD", "SOAK" -> startLoadTest(
                config = config
            )
            "STRESS" -> startStressTest(
                config = config
            )
            "SPIKE" -> startSpikeTest(
                config = config
            )
            else -> {
                logger.error("[LoadGenerator] Unknown test type: $testType")
                return
            }
        }
    }

    private fun startLoadTest(
        config: TestConfig
    ) {
        val rampUpInfo = if (config.rampUpSeconds > 0) {
            "Ramp-up: ${config.rampUpSeconds}s"
        } else {
            "Ramp-up: None (instant)"
        }
        
        val testTypeName = config.testType.uppercase()
        logger.info(
            "[LoadGenerator] $testTypeName 테스트 시작 - ID: ${config.testId}, URL: ${config.targetUrl}, " +
            "Users: ${config.virtualUsers}, Duration: ${config.durationSeconds}s, Method: ${config.httpMethod}, $rampUpInfo"
        )

        // 통계 집계기 생성
        statsCollector = WindowedStatsCollector(
            testId = config.testId
        )

        // 통계 리포터 생성 및 시작
        stopRequested = false

        // [부하 / 장애] 상황에서의 보호 로직
        val latencyStopThresholdMs = 2_000.0
        val latencyStopConsecutiveWindows = 3
        var highLatencyWindows = 0

        statsReporter = StatsReporter(
            collector = statsCollector!!,
            reportStub = reportServiceStub,
            getActiveUsers = { activeUserCount.sum().toInt() }
        ) { stat ->
            if (stopRequested || !isRunning) return@StatsReporter
            if (stat.requestsPerSecond <= 0) return@StatsReporter

            if (stat.avgLatencyMs >= latencyStopThresholdMs) {
                highLatencyWindows++
            } else {
                highLatencyWindows = 0
            }

            if (highLatencyWindows >= latencyStopConsecutiveWindows && !stopRequested) {
                stopRequested = true
                Thread {
                    logger.error(
                        "[LoadGenerator] Avg latency SLO violated for $highLatencyWindows windows " +
                            "(>=${latencyStopThresholdMs}ms). Stopping test. testId=${config.testId}"
                    )
                    stop()
                }.start()
            }
        }
        statsReporter?.start()

        // 통계 초기화
        requestCount.reset()
        errorCount.reset()
        activeUserCount.reset()
        isRunning = true

        executorService = Executors.newVirtualThreadPerTaskExecutor()

        val startTime = System.currentTimeMillis()

        if (config.rampUpSeconds > 0) {
            val delayBetweenUsers = (config.rampUpSeconds * 1000.0) / config.virtualUsers
            
            Thread {
                repeat(config.virtualUsers) { userIndex ->
                    if (!isRunning) return@Thread
                    
                    executorService?.submit {
                        activeUserCount.increment()
                        try {
                            val threadStartTime = System.currentTimeMillis()
                            val threadEndTime = threadStartTime + (config.durationSeconds * 1000L)
                            runLoadTest(
                                config = config,
                                endTime = threadEndTime,
                                userIndex = userIndex
                            )
                        } finally {
                            activeUserCount.decrement()
                        }
                    }
                    
                    if (userIndex < config.virtualUsers - 1) {
                        Thread.sleep(delayBetweenUsers.toLong())
                    }
                }
                
                logger.info("[LoadGenerator] Ramp-up 완료 - ${config.virtualUsers}개의 Virtual Thread가 모두 시작되었습니다.")
            }.start()
            
            logger.info("[LoadGenerator] Ramp-up 시작 - ${config.virtualUsers}명을 ${config.rampUpSeconds}초에 걸쳐 시작합니다.")
        } else {
            // 즉시 시작
            val endTime = startTime + (config.durationSeconds * 1000L)
            repeat(config.virtualUsers) { userIndex ->
                executorService?.submit {
                    activeUserCount.increment()
                    try {
                        runLoadTest(
                            config = config,
                            endTime = endTime,
                            userIndex = userIndex
                        )
                    } finally {
                        activeUserCount.decrement()
                    }
                }
            }
            
            logger.info("[LoadGenerator] ${config.virtualUsers}개의 Virtual Thread가 즉시 시작되었습니다.")
        }

        startMonitoring(
            testId = config.testId,
            startTime = startTime,
            durationSeconds = config.durationSeconds
        )
    }

    private fun startStressTest(
        config: TestConfig
    ) {
        val stressConfig = config.stressTestConfig
        
        val totalSteps = ((stressConfig.maxUsers - stressConfig.startUsers) / stressConfig.stepIncrement) + 1
        val totalDuration = totalSteps * stressConfig.stepDuration
        
        logger.info(
            "[LoadGenerator] STRESS 테스트 시작 - ID: ${config.testId}, URL: ${config.targetUrl}, " +
            "Users: ${stressConfig.startUsers} -> ${stressConfig.maxUsers} (Step: +${stressConfig.stepIncrement} / ${stressConfig.stepDuration}s), " +
            "Total Steps: $totalSteps, Total Duration: ${totalDuration}s, Method: ${config.httpMethod}"
        )

        // 통계 집계기 생성
        statsCollector = WindowedStatsCollector(
            testId = config.testId
        )

        // 통계 리포터 생성 및 시작
        stopRequested = false

        val latencyStopThresholdMs = 2_000.0
        val latencyStopConsecutiveWindows = 3
        var highLatencyWindows = 0

        statsReporter = StatsReporter(
            collector = statsCollector!!,
            reportStub = reportServiceStub,
            getActiveUsers = { activeUserCount.sum().toInt() }
        ) { stat ->
            if (stopRequested || !isRunning) return@StatsReporter
            if (stat.requestsPerSecond <= 0) return@StatsReporter

            if (stat.avgLatencyMs >= latencyStopThresholdMs) {
                highLatencyWindows++
            } else {
                highLatencyWindows = 0
            }

            if (highLatencyWindows >= latencyStopConsecutiveWindows && !stopRequested) {
                stopRequested = true
                Thread {
                    logger.error(
                        "[LoadGenerator] Avg latency SLO violated for $highLatencyWindows windows " +
                            "(>=${latencyStopThresholdMs}ms). Stopping stress test. testId=${config.testId}"
                    )
                    stop()
                }.start()
            }
        }
        statsReporter?.start()

        // 통계 초기화
        requestCount.reset()
        errorCount.reset()
        activeUserCount.reset()
        isRunning = true

        // ExecutorService 생성
        executorService = Executors.newVirtualThreadPerTaskExecutor()

        val testStartTime = System.currentTimeMillis()
        val testEndTime = testStartTime + (totalDuration * 1000L)
        
        // Stress Test 관리 스레드
        Thread {
            var currentStep = 0
            var currentUsers = stressConfig.startUsers
            
            while (currentUsers <= stressConfig.maxUsers && isRunning) {
                val stepStartTime = System.currentTimeMillis()
                val stepEndTime = stepStartTime + (stressConfig.stepDuration * 1000L)
                
                // 현재 단계의 사용자 수 계산
                val usersToStart = if (currentStep == 0) {
                    currentUsers
                } else {
                    stressConfig.stepIncrement
                }
                
                logger.info(
                    "[StressTest] Step ${currentStep + 1}/$totalSteps 시작 - " +
                    "사용자 추가: +$usersToStart (총 활성: ${currentUsers}명), " +
                    "단계 지속 시간: ${stressConfig.stepDuration}s"
                )
                
                // 이번 단계에서 추가할 사용자들 시작
                repeat(usersToStart) { userIndexInStep ->
                    if (!isRunning) return@Thread
                    
                    executorService?.submit {
                        activeUserCount.increment()
                        try {
                            runLoadTest(
                                config = config,
                                endTime = testEndTime,
                                userIndex = currentUsers + userIndexInStep
                            )
                        } finally {
                            activeUserCount.decrement()
                        }
                    }
                }
                
                // 다음 단계까지 대기
                try {
                    val remainingTime = stepEndTime - System.currentTimeMillis()
                    if (remainingTime > 0) {
                        Thread.sleep(remainingTime)
                    }
                } catch (_: InterruptedException) {
                    logger.warn("[StressTest] Stress test interrupted")
                    Thread.currentThread().interrupt()
                    return@Thread
                }
                
                logger.info(
                    "[StressTest] Step ${currentStep + 1}/$totalSteps 완료 - " +
                    "현재 활성 사용자: ${activeUserCount.sum()}명"
                )
                
                // 다음 단계로
                currentStep++
                currentUsers += stressConfig.stepIncrement
            }
            
            logger.info(
                "[StressTest] 모든 단계 완료 - 최대 사용자: ${stressConfig.maxUsers}명, " +
                "현재 활성 사용자: ${activeUserCount.sum()}명"
            )
        }.apply {
            isDaemon = false
            name = "stress-test-manager-${config.testId}"
            start()
        }

        startMonitoring(
            testId = config.testId,
            startTime = testStartTime,
            durationSeconds = totalDuration
        )
    }

    private fun startSpikeTest(
        config: TestConfig
    ) {
        val spikeConfig = config.spikeTestConfig
        
        val totalDuration = spikeConfig.recoveryDuration + spikeConfig.spikeDuration + spikeConfig.recoveryDuration
        
        logger.info(
            "[LoadGenerator] SPIKE 테스트 시작 - ID: ${config.testId}, URL: ${config.targetUrl}, " +
            "Base: ${spikeConfig.baseUsers}명 -> Spike: ${spikeConfig.spikeUsers}명 -> Base: ${spikeConfig.baseUsers}명, " +
            "Spike Duration: ${spikeConfig.spikeDuration}s, Recovery: ${spikeConfig.recoveryDuration}s, " +
            "Total Duration: ${totalDuration}s, Method: ${config.httpMethod}"
        )

        // 통계 집계기 생성
        statsCollector = WindowedStatsCollector(
            testId = config.testId
        )

        // 통계 리포터 생성 및 시작
        stopRequested = false

        val latencyStopThresholdMs = 2_000.0
        val latencyStopConsecutiveWindows = 3
        var highLatencyWindows = 0

        statsReporter = StatsReporter(
            collector = statsCollector!!,
            reportStub = reportServiceStub,
            getActiveUsers = { activeUserCount.sum().toInt() }
        ) { stat ->
            if (stopRequested || !isRunning) return@StatsReporter
            if (stat.requestsPerSecond <= 0) return@StatsReporter

            if (stat.avgLatencyMs >= latencyStopThresholdMs) {
                highLatencyWindows++
            } else {
                highLatencyWindows = 0
            }

            if (highLatencyWindows >= latencyStopConsecutiveWindows && !stopRequested) {
                stopRequested = true
                Thread {
                    logger.error(
                        "[LoadGenerator] Avg latency SLO violated for $highLatencyWindows windows " +
                            "(>=${latencyStopThresholdMs}ms). Stopping spike test. testId=${config.testId}"
                    )
                    stop()
                }.start()
            }
        }
        statsReporter?.start()

        // 통계 초기화
        requestCount.reset()
        errorCount.reset()
        activeUserCount.reset()
        isRunning = true

        // ExecutorService 생성
        executorService = Executors.newVirtualThreadPerTaskExecutor()

        val testStartTime = System.currentTimeMillis()
        val testEndTime = testStartTime + (totalDuration * 1000L)
        
        // Spike Test 관리 스레드
        Thread {
            try {
                // 기본 부하 (Recovery Duration)
                if (spikeConfig.recoveryDuration > 0) {
                    logger.info(
                        "[SpikeTest] Phase 1: 기본 부하 시작 - " +
                        "${spikeConfig.baseUsers}명, ${spikeConfig.recoveryDuration}초"
                    )
                    
                    repeat(spikeConfig.baseUsers) { userIndex ->
                        if (!isRunning) return@Thread
                        
                        executorService?.submit {
                            activeUserCount.increment()
                            try {
                                runLoadTest(
                                    config = config,
                                    endTime = testEndTime,
                                    userIndex = userIndex
                                )
                            } finally {
                                activeUserCount.decrement()
                            }
                        }
                    }
                    
                    Thread.sleep(spikeConfig.recoveryDuration * 1000L)
                    
                    logger.info(
                        "[SpikeTest] Phase 1 완료 - " +
                        "현재 활성 사용자: ${activeUserCount.sum()}명"
                    )
                }
                
                // 급증 (Spike)
                if (!isRunning) return@Thread
                
                val spikeIncrement = spikeConfig.spikeUsers - spikeConfig.baseUsers
                logger.warn(
                    "🔥 [SpikeTest] Phase 2: 부하 급증! - " +
                    "${spikeConfig.baseUsers}명 -> ${spikeConfig.spikeUsers}명 (+${spikeIncrement}명), " +
                    "${spikeConfig.spikeDuration}초 유지"
                )
                
                repeat(spikeIncrement) { userIndex ->
                    if (!isRunning) return@Thread
                    
                    executorService?.submit {
                        activeUserCount.increment()
                        try {
                            runLoadTest(
                                config = config,
                                endTime = testEndTime,
                                userIndex = spikeConfig.baseUsers + userIndex
                            )
                        } finally {
                            activeUserCount.decrement()
                        }
                    }
                }
                
                Thread.sleep(spikeConfig.spikeDuration * 1000L)
                
                logger.info(
                    "[SpikeTest] Phase 2 완료 - " +
                    "피크 활성 사용자: ${activeUserCount.sum()}명"
                )
                
                // 회복 (Recovery)
                if (!isRunning) return@Thread
                
                logger.info(
                    "📉 [SpikeTest] Phase 3: 부하 감소 및 회복 - " +
                    "${spikeConfig.recoveryDuration}초 동안 시스템 회복 관찰"
                )
                
                Thread.sleep(spikeConfig.recoveryDuration * 1000L)
                
                logger.info(
                    "[SpikeTest] Phase 3 완료 - " +
                    "회복 후 활성 사용자: ${activeUserCount.sum()}명"
                )
                
                logger.info("[SpikeTest] 모든 단계 완료")
                
            } catch (_: InterruptedException) {
                logger.warn("[SpikeTest] Spike test interrupted")
                Thread.currentThread().interrupt()
            } catch (e: Exception) {
                logger.error("[SpikeTest] Unexpected error: ${e.message}", e)
            }
        }.apply {
            isDaemon = false
            name = "spike-test-manager-${config.testId}"
            start()
        }

        startMonitoring(
            testId = config.testId,
            startTime = testStartTime,
            durationSeconds = totalDuration
        )
    }

    private fun runLoadTest(
        config: TestConfig,
        endTime: Long,
        userIndex: Int
    ) {
        val finalUrl = buildUrlWithQueryParams(
            baseUrl = config.targetUrl,
            queryParams = config.queryParamsMap
        )
        val uri = URI.create(finalUrl)
        val httpMethod = config.httpMethod.uppercase()
        var consecutiveErrors = 0
        val maxConsecutiveErrors = 10 // 연속 10번 에러 시 해당 스레드 중단
        var totalRequestCount = 0L // 해당 스레드의 총 요청 수

        try {
            while (System.currentTimeMillis() < endTime && isRunning) {
                val startRequestTime = System.currentTimeMillis()
                
                try {
                    val request = buildHttpRequest(
                        uri = uri,
                        httpMethod = httpMethod,
                        config = config
                    )

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
                    
                    statsCollector?.record(
                        latencyMs = latency,
                        isSuccess = isSuccess
                    )

                    Thread.sleep(10)

                } catch (e: Exception) {
                    errorCount.increment()
                    requestCount.increment()
                    consecutiveErrors++
                    
                    // 예외 발생 시 latency 측정
                    val latency = System.currentTimeMillis() - startRequestTime
                    
                    // 예외 발생 시에도 통계 기록
                    statsCollector?.record(
                        latencyMs = latency,
                        isSuccess = false
                    )

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

                } catch (_: InterruptedException) {
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

        // Breaking Point 정보 조회
        val breakingPoint = statsReporter?.getBreakingPoint()

        // 통계 리포터 중지
        statsReporter?.stop()
        statsReporter = null

        executorService?.shutdown()

        try {
            if (!executorService?.awaitTermination(5, TimeUnit.SECONDS)!!) {
                executorService?.shutdownNow()
            }
        } catch (_: InterruptedException) {
            executorService?.shutdownNow()
            Thread.currentThread().interrupt()
        }

        monitoringThread?.interrupt()
        try {
            monitoringThread?.join(1000)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }

        // 통계 집계기 정리
        statsCollector = null

        logger.info(
            "[LoadGenerator] 부하 테스트 종료 - " +
            "Total Requests: ${requestCount.sum()}, Total Errors: ${errorCount.sum()}"
        )
        
        // Breaking Point 정보 출력
        if (breakingPoint != null) {
            val saturationInfo = if (breakingPoint.tpsSaturated) {
                " | TPS Saturation 발생 ⚠️"
            } else {
                ""
            }
            
            logger.warn(
                "📊 [테스트 요약] Breaking Point가 감지되었습니다! " +
                "한계점: ${breakingPoint.users}명 (상태: ${breakingPoint.status})${saturationInfo}"
            )
        } else {
            logger.info("📊 [테스트 요약] Breaking Point가 감지되지 않았습니다. 시스템이 안정적으로 부하를 처리했습니다.")
        }
    }

    fun getStats(): LoadGeneratorStats {
        return LoadGeneratorStats(
            requestCount = requestCount.sum(),
            errorCount = errorCount.sum()
        )
    }

    private fun buildUrlWithQueryParams(
        baseUrl: String,
        queryParams: Map<String, String>
    ): String {
        if (queryParams.isEmpty()) {
            return baseUrl
        }

        val separator = if (baseUrl.contains('?')) '&' else '?'
        val queryString = queryParams.entries.joinToString("&") { (key, value) ->
            "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
        }

        return "$baseUrl$separator$queryString"
    }

    private fun buildHttpRequest(
        uri: URI,
        httpMethod: String,
        config: TestConfig
    ): HttpRequest {
        val builder = HttpRequest.newBuilder()
            .uri(uri)
            .timeout(Duration.ofSeconds(10))

        config.headersMap.forEach { (key, value) ->
            builder.header(key, value)
        }

        when (httpMethod) {
            HttpMethodType.GET.name -> builder.GET()
            HttpMethodType.POST.name -> {
                val body = config.requestBody.takeIf { it.isNotBlank() } ?: ""
                builder.POST(HttpRequest.BodyPublishers.ofString(body))
            }
            HttpMethodType.PUT.name -> {
                val body = config.requestBody.takeIf { it.isNotBlank() } ?: ""
                builder.PUT(HttpRequest.BodyPublishers.ofString(body))
            }
            HttpMethodType.PATCH.name -> {
                val body = config.requestBody.takeIf { it.isNotBlank() } ?: ""
                builder.method(HttpMethodType.PATCH.name, HttpRequest.BodyPublishers.ofString(body))
            }
            HttpMethodType.DELETE.name -> {
                builder.DELETE()
            }
            else -> {
                logger.warn("Unsupported HTTP method: $httpMethod, using GET")
                builder.GET()
            }
        }

        return builder.build()
    }
}

