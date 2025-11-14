#!/bin/bash

# 风险预警平台启动脚本

echo "启动风险预警平台..."

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 检查Docker是否运行
check_docker() {
    if ! docker info > /dev/null 2>&1; then
        echo -e "${RED}错误: Docker未运行，请先启动Docker${NC}"
        exit 1
    fi
}

# 检查端口是否被占用
check_port() {
    local port=$1
    local service=$2
    if lsof -Pi :$port -sTCP:LISTEN -t >/dev/null ; then
        echo -e "${YELLOW}警告: 端口 $port ($service) 已被占用${NC}"
        return 1
    fi
    return 0
}

# 启动基础设施服务
start_infrastructure() {
    echo -e "${GREEN}步骤1: 启动基础设施服务...${NC}"

    # 检查关键端口
    check_port 5432 "PostgreSQL"
    check_port 6379 "Redis"
    check_port 9200 "Elasticsearch"
    check_port 9092 "Kafka"
    check_port 8848 "Nacos"

    # 启动Docker Compose
    docker-compose up -d

    echo "等待服务启动..."
    sleep 30

    # 检查服务状态
    echo "检查服务状态:"
    docker-compose ps
}

# 编译项目
build_project() {
    echo -e "${GREEN}步骤2: 编译项目...${NC}"
    mvn clean package -DskipTests

    if [ $? -ne 0 ]; then
        echo -e "${RED}编译失败${NC}"
        exit 1
    fi

    echo -e "${GREEN}编译完成${NC}"
}

# 启动微服务
start_microservices() {
    echo -e "${GREEN}步骤3: 启动微服务...${NC}"

    # 定义服务端口映射
    declare -A services=(
        ["risk-warning-gateway"]="8080"
        ["risk-warning-user"]="8081"
        ["risk-warning-enterprise"]="8082"
        ["risk-warning-project"]="8083"
        ["risk-warning-data-collection"]="8084"
        ["risk-warning-behavior-processing"]="8085"
        ["risk-warning-knowledge"]="8086"
        ["risk-warning-matching-calculation"]="8087"
        ["risk-warning-risk-assessment"]="8088"
        ["risk-warning-report"]="8089"
        ["risk-warning-event"]="8090"
        ["risk-warning-visualization"]="8091"
        ["risk-warning-notification"]="8092"
    )

    # 启动每个服务
    for service in "${!services[@]}"; do
        port=${services[$service]}
        echo "启动 $service (端口: $port)..."

        # 检查jar文件是否存在
        jar_file="${service}/target/${service}-1.0.0.jar"
        if [ ! -f "$jar_file" ]; then
            echo -e "${RED}错误: $jar_file 不存在${NC}"
            continue
        fi

        # 启动服务
        nohup java -jar "$jar_file" > "logs/${service}.log" 2>&1 &

        # 记录进程ID
        echo $! > "logs/${service}.pid"

        echo "✓ $service 已启动 (PID: $(cat logs/${service}.pid))"
        sleep 5
    done

    echo -e "${GREEN}所有微服务已启动${NC}"
}

# 检查服务状态
check_services() {
    echo -e "${GREEN}步骤4: 检查服务状态...${NC}"

    # 检查基础设施
    echo "基础设施服务状态:"
    curl -s http://localhost:8848/nacos/v1/console/health/readiness || echo "Nacos 未就绪"
    curl -s http://localhost:9200/_cluster/health | jq .status 2>/dev/null || echo "Elasticsearch 未就绪"
    redis-cli ping 2>/dev/null || echo "Redis 未就绪"

    echo ""
    echo "微服务状态:"

    # 检查微服务
    declare -A services=(
        ["网关服务"]="8080"
        ["用户服务"]="8081"
        ["企业服务"]="8082"
        ["项目服务"]="8083"
        ["数据收集服务"]="8084"
        ["行为处理服务"]="8085"
        ["知识库服务"]="8086"
        ["匹配计算服务"]="8087"
        ["风险评估服务"]="8088"
        ["报告服务"]="8089"
        ["事件服务"]="8090"
        ["可视化服务"]="8091"
        ["通知服务"]="8092"
    )

    for service in "${!services[@]}"; do
        port=${services[$service]}
        if curl -s "http://localhost:$port/actuator/health" | grep -q "UP"; then
            echo -e "✓ $service: ${GREEN}运行中${NC}"
        else
            echo -e "✗ $service: ${RED}未运行${NC}"
        fi
    done
}

# 创建日志目录
create_log_directory() {
    if [ ! -d "logs" ]; then
        mkdir logs
        echo "创建日志目录: logs/"
    fi
}

# 主函数
main() {
    echo -e "${GREEN}=== 风险预警平台启动脚本 ===${NC}"

    check_docker
    create_log_directory
    check_configs
    start_infrastructure
    build_project
    start_microservices
    check_services

    echo ""
    echo -e "${GREEN}=== 启动完成 ===${NC}"
    echo "访问地址:"
    echo "- 网关服务: http://localhost:8080"
    echo "- Nacos控制台: http://localhost:8848/nacos (nacos/nacos)"
    echo "- Elasticsearch: http://localhost:9200"
    echo ""
    echo "查看日志: tail -f logs/[服务名称].log"
    echo "停止服务: ./stop.sh"
}

# 执行主函数
main
