#!/bin/bash

# 风险预警平台停止脚本

echo "停止风险预警平台..."

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 停止微服务
stop_microservices() {
    echo -e "${GREEN}步骤1: 停止微服务...${NC}"

    # 定义服务列表
    services=(
        "risk-warning-gateway"
        "risk-warning-user"
        "risk-warning-enterprise"
        "risk-warning-project"
        "risk-warning-data-collection"
        "risk-warning-behavior-processing"
        "risk-warning-knowledge"
        "risk-warning-matching-calculation"
        "risk-warning-risk-assessment"
        "risk-warning-report"
        "risk-warning-event"
        "risk-warning-visualization"
        "risk-warning-notification"
    )

    # 停止每个服务
    for service in "${services[@]}"; do
        pid_file="logs/${service}.pid"

        if [ -f "$pid_file" ]; then
            pid=$(cat "$pid_file")
            if ps -p $pid > /dev/null; then
                echo "停止 $service (PID: $pid)..."
                kill $pid
                sleep 2

                # 如果进程仍在运行，强制杀死
                if ps -p $pid > /dev/null; then
                    echo "强制停止 $service..."
                    kill -9 $pid
                fi

                rm "$pid_file"
                echo "✓ $service 已停止"
            else
                echo "✗ $service 进程不存在"
                rm "$pid_file"
            fi
        else
            echo "✗ $service PID文件不存在"
        fi
    done
}

# 停止基础设施服务
stop_infrastructure() {
    echo -e "${GREEN}步骤2: 停止基础设施服务...${NC}"

    # 停止Docker Compose服务
    docker-compose down

    echo "✓ 基础设施服务已停止"
}

# 清理资源
cleanup() {
    echo -e "${GREEN}步骤3: 清理资源...${NC}"

    # 清理孤儿进程
    pkill -f "risk-warning"

    # 清理临时文件
    if [ -d "logs" ]; then
        rm -f logs/*.pid
        echo "✓ 清理PID文件"
    fi

    echo "✓ 资源清理完成"
}

# 显示停止状态
show_status() {
    echo -e "${GREEN}步骤4: 检查停止状态...${NC}"

    # 检查端口占用
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

    echo "服务停止状态:"
    for service in "${!services[@]}"; do
        port=${services[$service]}
        if lsof -Pi :$port -sTCP:LISTEN -t >/dev/null; then
            echo -e "✗ $service (端口 $port): ${RED}仍在运行${NC}"
        else
            echo -e "✓ $service (端口 $port): ${GREEN}已停止${NC}"
        fi
    done

    # 检查Docker容器
    echo ""
    echo "Docker容器状态:"
    running_containers=$(docker-compose ps --services --filter "status=running" 2>/dev/null)
    if [ -z "$running_containers" ]; then
        echo -e "✓ ${GREEN}所有容器已停止${NC}"
    else
        echo -e "✗ ${RED}以下容器仍在运行:${NC}"
        echo "$running_containers"
    fi
}

# 主函数
main() {
    echo -e "${GREEN}=== 风险预警平台停止脚本 ===${NC}"

    stop_microservices
    stop_infrastructure
    cleanup
    show_status

    echo ""
    echo -e "${GREEN}=== 停止完成 ===${NC}"
    echo "重新启动: ./start.sh"
}

# 执行主函数
main
