let eventSource = null;
let currentTestId = null;
let currentTestType = 'LOAD';
let tpsChart = null;
let usersChart = null;
let startTime = null;
let breakingPointDetected = false;

const maxDataPoints = 50;

const tpsData = {
    labels: [],
    datasets: [
        {
            label: 'TPS',
            data: [],
            borderColor: '#667eea',
            backgroundColor: 'rgba(102, 126, 234, 0.1)',
            tension: 0.4,
            yAxisID: 'y'
        },
        {
            label: 'Avg Latency (ms)',
            data: [],
            borderColor: '#f59e0b',
            backgroundColor: 'rgba(245, 158, 11, 0.1)',
            tension: 0.4,
            yAxisID: 'y1'
        }
    ]
};

const usersData = {
    labels: [],
    datasets: [
        {
            label: 'Active Users',
            data: [],
            borderColor: '#10b981',
            backgroundColor: 'rgba(16, 185, 129, 0.1)',
            tension: 0.4,
            yAxisID: 'y'
        },
        {
            label: 'Error Rate (%)',
            data: [],
            borderColor: '#ef4444',
            backgroundColor: 'rgba(239, 68, 68, 0.1)',
            tension: 0.4,
            yAxisID: 'y1'
        }
    ]
};

function initCharts() {
    const tpsCtx = document.getElementById('tpsChart').getContext('2d');
    tpsChart = new Chart(tpsCtx, {
        type: 'line',
        data: tpsData,
        options: {
            responsive: true,
            maintainAspectRatio: false,
            interaction: {
                mode: 'index',
                intersect: false,
            },
            scales: {
                y: {
                    type: 'linear',
                    display: true,
                    position: 'left',
                    title: {
                        display: true,
                        text: 'TPS'
                    }
                },
                y1: {
                    type: 'linear',
                    display: true,
                    position: 'right',
                    title: {
                        display: true,
                        text: 'Latency (ms)'
                    },
                    grid: {
                        drawOnChartArea: false,
                    }
                }
            }
        }
    });

    const usersCtx = document.getElementById('usersChart').getContext('2d');
    usersChart = new Chart(usersCtx, {
        type: 'line',
        data: usersData,
        options: {
            responsive: true,
            maintainAspectRatio: false,
            interaction: {
                mode: 'index',
                intersect: false,
            },
            scales: {
                y: {
                    type: 'linear',
                    display: true,
                    position: 'left',
                    title: {
                        display: true,
                        text: 'Active Users'
                    }
                },
                y1: {
                    type: 'linear',
                    display: true,
                    position: 'right',
                    title: {
                        display: true,
                        text: 'Error Rate (%)'
                    },
                    grid: {
                        drawOnChartArea: false,
                    }
                }
            }
        }
    });
}

function switchTab(tabName, clickedButton) {
    // Update tab buttons
    document.querySelectorAll('.tab-button').forEach(btn => btn.classList.remove('active'));
    if (clickedButton) {
        clickedButton.classList.add('active');
    }

    // Update tab contents
    document.querySelectorAll('.tab-content').forEach(content => content.classList.remove('active'));
    document.getElementById(tabName).classList.add('active');
}

function selectTestType(testType) {
    currentTestType = testType;
    
    // Update UI
    document.querySelectorAll('.test-type-option').forEach(opt => opt.classList.remove('selected'));
    event.target.closest('.test-type-option').classList.add('selected');

    // Show/hide config sections
    document.querySelectorAll('.config-section').forEach(section => section.classList.remove('active'));
    
    if (testType === 'LOAD' || testType === 'SOAK') {
        document.getElementById('loadConfig').classList.add('active');
    } else if (testType === 'STRESS') {
        document.getElementById('stressConfig').classList.add('active');
    } else if (testType === 'SPIKE') {
        document.getElementById('spikeConfig').classList.add('active');
    }
}

async function startNewTest() {
    const btn = document.getElementById('startTestBtn');
    btn.disabled = true;
    btn.textContent = '⏳ Starting Test...';

    try {
        const testConfig = buildTestConfig();
        
        const response = await fetch('/api/test/trigger', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(testConfig)
        });

        if (!response.ok) {
            let errorMsg = `HTTP ${response.status}`;
            try {
                const errorText = await response.text();
                errorMsg += `: ${errorText}`;
            } catch (e) {
                // ignore
            }
            throw new Error(errorMsg);
        }

        const result = await response.text();
        const testIdMatch = result.match(/testId=([a-f0-9-]+)/);
        
        if (!testIdMatch) {
            throw new Error('Failed to extract test ID from response');
        }
        
        const testId = testIdMatch[1];
        
        showAlert(`✅ Test started successfully! Test ID: ${testId}`, 'success');
        
        // 즉시 SSE 연결 및 모니터링 시작 (대기 없음!)
        connectToTest(testId);
        
        // Switch to monitor tab
        document.querySelectorAll('.tab-content').forEach(content => content.classList.remove('active'));
        document.getElementById('monitor').classList.add('active');
        document.querySelectorAll('.tab-button').forEach((btn, idx) => {
            btn.classList.remove('active');
            if (idx === 1) btn.classList.add('active');
        });

    } catch (error) {
        console.error('Test start error:', error);
        let errorMsg = error.message;
        
        // 일반적인 에러 케이스 처리
        if (errorMsg.includes('Failed to connect') || errorMsg.includes('Connection refused') || errorMsg.includes('AgentConnectionException')) {
            errorMsg = `
                ❌ Agent 연결 실패!<br/><br/>
                <strong>해결 방법:</strong><br/>
                1. Agent가 실행 중인지 확인하세요<br/>
                2. Agent 실행: <code>./gradlew :agent:bootRun</code><br/>
                3. 또는: <code>cd agent && ./gradlew bootRun</code><br/><br/>
                <small>Agent는 기본적으로 50051 포트에서 실행됩니다.</small>
            `;
        } else if (errorMsg.includes('400')) {
            errorMsg = `❌ 잘못된 요청: 입력값을 확인하세요<br/><small>${errorMsg}</small>`;
        } else if (errorMsg.includes('500')) {
            errorMsg = `❌ 서버 내부 오류<br/><small>${errorMsg}</small>`;
        } else {
            errorMsg = `❌ 테스트 시작 실패<br/><small>${errorMsg}</small>`;
        }
        
        showAlert(errorMsg, 'critical');
    } finally {
        btn.disabled = false;
        btn.textContent = '🚀 Start Test & Monitor';
    }
}

function buildTestConfig() {
    const config = {
        title: document.getElementById('testTitle').value || 'Load Test',
        targetUrl: document.getElementById('targetUrl').value,
        testType: currentTestType,
        method: document.getElementById('httpMethod').value,
        requestBody: document.getElementById('requestBody').value || null
    };

    if (currentTestType === 'LOAD' || currentTestType === 'SOAK') {
        config.virtualUsers = parseInt(document.getElementById('virtualUsers').value);
        config.durationSeconds = parseInt(document.getElementById('durationSeconds').value);
        config.rampUpSeconds = parseInt(document.getElementById('rampUpSeconds').value) || 0;
    } else if (currentTestType === 'STRESS') {
        config.stressConfig = {
            startUsers: parseInt(document.getElementById('stressStartUsers').value),
            maxUsers: parseInt(document.getElementById('stressMaxUsers').value),
            stepDuration: parseInt(document.getElementById('stressStepDuration').value),
            stepIncrement: parseInt(document.getElementById('stressStepIncrement').value)
        };
    } else if (currentTestType === 'SPIKE') {
        config.spikeConfig = {
            baseUsers: parseInt(document.getElementById('spikeBaseUsers').value),
            spikeUsers: parseInt(document.getElementById('spikeSpikeUsers').value),
            spikeDuration: parseInt(document.getElementById('spikeSpikeDuration').value),
            recoveryDuration: parseInt(document.getElementById('spikeRecoveryDuration').value)
        };
    }

    return config;
}

function connectToExistingTest() {
    const testId = document.getElementById('monitorTestId').value.trim();
    if (!testId) {
        alert('Please enter a Test ID');
        return;
    }
    connectToTest(testId);
}

async function connectToTest(testId) {
    if (eventSource) {
        eventSource.close();
    }

    currentTestId = testId;
    startTime = Date.now();
    breakingPointDetected = false;
    
    // Reset data
    tpsData.labels = [];
    tpsData.datasets[0].data = [];
    tpsData.datasets[1].data = [];
    usersData.labels = [];
    usersData.datasets[0].data = [];
    usersData.datasets[1].data = [];
    
    if (tpsChart) {
        tpsChart.update();
        usersChart.update();
    }

    // Show monitoring sections
    document.getElementById('testInfo').classList.add('active');
    document.getElementById('metricsGrid').classList.add('active');
    document.getElementById('chartContainer').classList.add('active');
    document.getElementById('chartContainer2').classList.add('active');
    document.getElementById('testIdDisplay').textContent = testId;
    document.getElementById('alertContainer').innerHTML = '';
    
    // Show control buttons
    const controlButtons = document.getElementById('testControlButtons');
    if (controlButtons) {
        controlButtons.style.display = 'flex';
    }

    if (!tpsChart) {
        initCharts();
    }

    // Check if test is already finished
    const urlParams = new URLSearchParams(window.location.search);
    const fromReport = urlParams.get('fromReport');
    
    if (fromReport === 'true') {
        try {
            const response = await fetch(`/api/test/${testId}`);
            const testData = await response.json();
            
            if (testData.status === 'FINISHED') {
                showAlert(
                    `📊 Viewing completed test. <a href="/report.html?testId=${testId}" style="color: #667eea; font-weight: bold;">View Full Report →</a>`,
                    'success'
                );
                updateConnectionStatus(false);
            }
        } catch (error) {
            console.error('Failed to check test status:', error);
        }
    }

    eventSource = new EventSource(`/api/dashboard/stream/${testId}`);

    eventSource.onopen = () => {
        updateConnectionStatus(true);
        console.log('Connected to SSE stream');
    };

    eventSource.addEventListener('metric', (event) => {
        console.log('📥 metric 이벤트 수신:', event.data);
        const metric = JSON.parse(event.data);
        console.log('📊 파싱된 메트릭:', metric);
        updateDashboard(metric);
    });

    eventSource.addEventListener('testCompleted', (event) => {
        const data = JSON.parse(event.data);
        console.log('Test completed:', data);
        
        // SSE 연결 종료
        if (eventSource) {
            eventSource.close();
            eventSource = null;
        }
        
        // 상태를 "Completed"로 변경
        updateConnectionStatus(false, true);
        
        // Stop 버튼 비활성화
        const stopBtn = document.getElementById('stopTestBtn');
        if (stopBtn) {
            stopBtn.disabled = true;
            stopBtn.textContent = '⏹️ Test Completed';
            stopBtn.style.background = '#9ca3af';
            stopBtn.style.cursor = 'not-allowed';
        }
        
        // Test Info에 최종 상태 표시
        const testInfoDiv = document.getElementById('testInfo');
        if (testInfoDiv) {
            // Duration을 최종값으로 고정
            const finalDuration = Math.floor((Date.now() - startTime) / 1000);
            document.getElementById('duration').textContent = `${finalDuration}s (Completed)`;
            document.getElementById('duration').style.color = '#10b981';
            document.getElementById('duration').style.fontWeight = 'bold';
        }
        
        // Check if we came from report page (don't redirect back)
        const urlParams = new URLSearchParams(window.location.search);
        const fromReport = urlParams.get('fromReport');
        
        if (fromReport === 'true') {
            showAlert(
                `✅ Test was completed with status: ${data.status}. This is the historical view.`,
                'success'
            );
        } else {
            // 자동 리다이렉션 제거 - 사용자가 버튼을 클릭해야 Report로 이동
            const reportButton = `
                <div style="margin-top: 10px;">
                    <a href="/report.html?testId=${data.testId}" 
                       style="display: inline-block; padding: 12px 30px; background: #667eea; 
                              color: white; text-decoration: none; border-radius: 10px; 
                              font-weight: bold; transition: background 0.3s;"
                       onmouseover="this.style.background='#5568d3'"
                       onmouseout="this.style.background='#667eea'">
                        📊 View Detailed Report
                    </a>
                </div>
            `;
            
            showAlert(
                `✅ Test completed with status: ${data.status}!<br/><br/>
                Dashboard shows final results. Click below for detailed analysis.${reportButton}`,
                'success'
            );
        }
    });

    eventSource.onerror = (error) => {
        console.error('SSE Error:', error);
        updateConnectionStatus(false);
        showAlert('Connection lost. Trying to reconnect...', 'warning');
    };
}

function updateDashboard(metric) {
    console.log('🎯 updateDashboard 호출됨!', metric);
    
    document.getElementById('activeUsers').textContent = metric.activeUsers;
    const elapsed = Math.floor((Date.now() - startTime) / 1000);
    document.getElementById('duration').textContent = `${elapsed}s`;

    const healthBadge = document.getElementById('healthStatus');
    healthBadge.textContent = metric.healthStatus;
    healthBadge.className = 'status-badge status-' + metric.healthStatus.toLowerCase();

    if (!breakingPointDetected && (metric.healthStatus === 'CRITICAL' || metric.healthStatus === 'FAILED')) {
        breakingPointDetected = true;
        showAlert(`🔥 Breaking Point Detected! Users: ${metric.activeUsers}, Status: ${metric.healthStatus}`, 'critical');
    }

    document.getElementById('tpsValue').innerHTML = `${metric.tps}<span class="metric-unit">TPS</span>`;
    document.getElementById('avgLatencyValue').innerHTML = `${metric.avgLatency.toFixed(1)}<span class="metric-unit">ms</span>`;
    document.getElementById('p95LatencyValue').innerHTML = `${metric.p95Latency.toFixed(1)}<span class="metric-unit">ms</span>`;
    document.getElementById('errorRateValue').innerHTML = `${(metric.errorRate * 100).toFixed(2)}<span class="metric-unit">%</span>`;

    const timestamp = new Date(metric.timestamp).toLocaleTimeString();
    
    tpsData.labels.push(timestamp);
    tpsData.datasets[0].data.push(metric.tps);
    tpsData.datasets[1].data.push(metric.avgLatency);

    usersData.labels.push(timestamp);
    usersData.datasets[0].data.push(metric.activeUsers);
    usersData.datasets[1].data.push(metric.errorRate * 100);

    if (tpsData.labels.length > maxDataPoints) {
        tpsData.labels.shift();
        tpsData.datasets[0].data.shift();
        tpsData.datasets[1].data.shift();
        usersData.labels.shift();
        usersData.datasets[0].data.shift();
        usersData.datasets[1].data.shift();
    }

    tpsChart.update('none');
    usersChart.update('none');
}

function updateConnectionStatus(connected, completed = false) {
    const statusEl = document.getElementById('connectionStatus');
    if (completed) {
        statusEl.innerHTML = '<span class="connected">● Test Completed</span>';
    } else if (connected) {
        statusEl.innerHTML = '<span class="connected">● Connected</span>';
    } else {
        statusEl.innerHTML = '<span class="disconnected">● Disconnected</span>';
    }
}

function showAlert(message, type) {
    const alertContainer = document.getElementById('alertContainer');
    let alertClass = 'alert';
    if (type === 'critical') alertClass = 'alert alert-critical';
    else if (type === 'success') alertClass = 'alert alert-success';
    else if (type === 'warning') alertClass = 'alert';
    
    alertContainer.innerHTML = `<div class="${alertClass}">${message}</div>`;
}

// Check system health on page load
async function checkSystemHealth() {
    try {
        const response = await fetch('/api/health');
        const health = await response.json();
        
        console.log('System health:', health);
        
        if (health.agent !== 'OK') {
            showAlert(`
                ⚠️ <strong>Agent is not running!</strong><br/><br/>
                ${health.message}<br/><br/>
                <small>Tests cannot be started until the agent is running.</small>
            `, 'warning');
            updateConnectionStatus(false);
        } else {
            updateConnectionStatus(true);
        }
    } catch (error) {
        console.error('Health check failed:', error);
    }
}

// View Report 버튼
function viewReport() {
    if (!currentTestId) {
        alert('No active test');
        return;
    }
    window.open(`/report.html?testId=${currentTestId}`, '_blank');
}

// Stop Test 버튼
async function stopTest() {
    if (!currentTestId) {
        alert('No active test');
        return;
    }

    if (!confirm('Are you sure you want to stop this test?')) {
        return;
    }

    const stopBtn = document.getElementById('stopTestBtn');
    if (stopBtn) {
        stopBtn.disabled = true;
        stopBtn.textContent = '⏹️ Stopping...';
    }

    try {
        const response = await fetch(`/api/test/${currentTestId}/stop`, {
            method: 'POST'
        });

        const result = await response.json();

        if (result.success) {
            showAlert('✅ Test stopped successfully', 'success');
            
            // SSE 연결 종료
            if (eventSource) {
                eventSource.close();
                eventSource = null;
            }
            
            updateConnectionStatus(false, true);
        } else {
            showAlert(`⚠️ ${result.message}`, 'warning');
        }
    } catch (error) {
        showAlert(`❌ Failed to stop test: ${error.message}`, 'critical');
    } finally {
        if (stopBtn) {
            stopBtn.disabled = false;
            stopBtn.textContent = '⏹️ Stop Test';
        }
    }
}

// Auto-connect if testId is in URL
window.addEventListener('DOMContentLoaded', () => {
    // Check system health
    checkSystemHealth();
    
    const params = new URLSearchParams(window.location.search);
    const testId = params.get('testId');
    if (testId) {
        document.getElementById('monitorTestId').value = testId;
        connectToTest(testId);
        switchTab('monitor');
        document.querySelectorAll('.tab-button').forEach((btn, idx) => {
            btn.classList.remove('active');
            if (idx === 1) btn.classList.add('active');
        });
    }
});

