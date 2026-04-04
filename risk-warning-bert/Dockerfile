# 第一阶段：构建Java部分
FROM maven:3.9.14-eclipse-temurin-17 AS builder
WORKDIR /app

# 配置阿里云Maven镜像
RUN mkdir -p /root/.m2 && \
    echo '<settings><mirrors><mirror><id>aliyun</id><mirrorOf>central</mirrorOf><url>https://maven.aliyun.com/repository/public</url></mirror></mirrors></settings>' > /root/.m2/settings.xml

# 复制根目录的父POM
COPY pom.xml .

# 安装父POM到本地仓库
RUN mvn clean install -N -DskipTests -U

# 复制common模块的POM和源码
COPY risk-warning-common/pom.xml ./risk-warning-common/pom.xml
COPY risk-warning-common/src ./risk-warning-common/src

# 复制子模块的POM和源码
COPY risk-warning-bert/pom.xml ./risk-warning-bert/pom.xml
COPY risk-warning-bert/src ./risk-warning-bert/src

# 构建common模块
WORKDIR /app/risk-warning-common
RUN mvn clean install -DskipTests -U

# 构建当前模块
WORKDIR /app/risk-warning-bert
RUN mvn dependency:go-offline -B -U
# 先执行package，再执行spring-boot:repackage确保生成可执行jar
RUN mvn clean package spring-boot:repackage -DskipTests -U

# 第二阶段：构建Python部分
FROM python:3.10-slim AS python-builder
WORKDIR /app

# 安装系统依赖
RUN apt-get update && \
    apt-get install -y gcc python3-dev && \
    rm -rf /var/lib/apt/lists/*

# 复制Python代码和依赖文件
COPY risk-warning-bert/bert-service ./bert-service
COPY risk-warning-bert/classify-service ./classify-service

# 使用国内pip源安装依赖
WORKDIR /app/bert-service
RUN pip install --no-cache-dir -i https://pypi.tuna.tsinghua.edu.cn/simple -r requirements.txt

# 第三阶段：运行阶段
FROM eclipse-temurin:17.0.18_8-jre
WORKDIR /app

# 安装Python环境
RUN apt-get update && \
    apt-get install -y python3 python3-pip && \
    rm -rf /var/lib/apt/lists/*

# 复制Java构建结果
COPY --from=builder /app/risk-warning-bert/target/*.jar app.jar

# 复制Python代码和依赖
COPY --from=python-builder /app/bert-service ./bert-service
COPY --from=python-builder /app/classify-service ./classify-service

# 复制启动脚本
COPY risk-warning-bert/start.sh ./start.sh
RUN chmod +x start.sh

# 创建用户
RUN useradd -m appuser
USER appuser

# 暴露端口
EXPOSE 8080

# 启动服务
ENTRYPOINT ["./start.sh"]
