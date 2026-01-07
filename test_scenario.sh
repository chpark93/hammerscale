#!/bin/bash

set -e

echo "=========================================="
echo "Hammerscale 부하 테스트 시나리오"
echo "=========================================="
echo ""

# 색상 정의
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

# 1. 인프라 상태 확인
echo -e "${YELLOW}[1/5] 인프라 상태 확인${NC}"
echo "PostgreSQL, InfluxDB 컨테이너 확인 중..."
if (docker compose ps 2>/dev/null || docker-compose ps 2>/dev/null) | grep -q "hammerscale-postgres.*Up" && \
   (docker compose ps 2>/dev/null || docker-compose ps 2>/dev/null) | grep -q "hammerscale-influxdb.*Up"; then
    echo -e "${GREEN}✓ 인프라 실행 중${NC}"
else
    echo -e "${YELLOW}⚠ 인프라가 실행되지 않았습니다.${NC}"
    echo "  실행 방법: docker compose up -d (또는 docker-compose up -d)"
    echo "  계속 진행합니다..."
fi
echo ""

# 2. Controller 서비스 확인
echo -e "${YELLOW}[2/5] Controller 서비스 확인${NC}"
if curl -s -f http://localhost:8080/actuator/health > /dev/null 2>&1; then
    echo -e "${GREEN}✓ Controller 서비스 실행 중 (포트 8080)${NC}"
else
    echo -e "${RED}✗ Controller 서비스가 실행되지 않았습니다.${NC}"
    echo "  실행 방법: ./gradlew :controller:bootRun"
    exit 1
fi
echo ""

# 3. Agent 서비스 확인
echo -e "${YELLOW}[3/5] Agent 서비스 확인${NC}"
if nc -z localhost 50051 2>/dev/null; then
    echo -e "${GREEN}✓ Agent gRPC 서버 실행 중 (포트 50051)${NC}"
else
    echo -e "${RED}✗ Agent 서비스가 실행되지 않았습니다.${NC}"
    echo "  실행 방법: ./gradlew :agent:bootRun"
    exit 1
fi
echo ""

# 4. 부하 테스트 트리거
echo -e "${YELLOW}[4/5] 부하 테스트 트리거${NC}"
echo "테스트 설정:"
echo "  - Target URL: http://google.com"
echo "  - Virtual Users: 100"
echo "  - Duration: 60초"
echo "  - Method: GET"
echo ""
echo "테스트 시작 요청 전송 중..."
RESPONSE=$(curl -s -X POST http://localhost:8080/api/test/trigger)
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ 테스트 시작 요청 성공${NC}"
    echo "  응답: $RESPONSE"
else
    echo -e "${RED}✗ 테스트 시작 요청 실패${NC}"
    exit 1
fi
echo ""

# 5. 테스트 모니터링
echo -e "${YELLOW}[5/5] 테스트 모니터링 (10초간)${NC}"
echo "로그를 확인하세요:"
echo "  - Agent 로그: [LoadGenerator] 메시지 확인"
echo "  - Controller 로그: [Report] 메시지 확인"
echo "  - InfluxDB 저장: [InfluxDB] 메시지 확인"
echo ""
echo "10초 대기 중... (Ctrl+C로 중단 가능)"
sleep 10
echo ""

echo "=========================================="
echo -e "${GREEN}테스트 시나리오 완료${NC}"
echo "=========================================="
echo ""
echo "다음 단계:"
echo "1. Agent 로그에서 부하 테스트 진행 상황 확인"
echo "2. Controller 로그에서 통계 수신 및 InfluxDB 저장 확인"
echo "3. InfluxDB UI (http://localhost:8086)에서 메트릭 확인"
echo "4. PostgreSQL (pgAdmin: http://localhost:5050)에서 TestPlan 확인"

