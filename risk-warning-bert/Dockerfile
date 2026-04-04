# 第一阶段：构建Java部分（保持原样，因为 maven:3.9.14-eclipse-temurin-17 基于 Debian）
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
RUN mvn clean package spring-boot:repackage -DskipTests -U

# 第二阶段：构建Python部分 - 改用 Ubuntu 基础镜像
FROM ubuntu:22.04 AS python-builder

# 设置环境变量
ENV DEBIAN_FRONTEND=noninteractive
ENV PYTHON_VERSION=3.10

WORKDIR /app

# 配置 Ubuntu 阿里云源并安装 Python 3.10
RUN sed -i 's/archive.ubuntu.com/mirrors.aliyun.com/g' /etc/apt/sources.list && \
    sed -i 's/security.ubuntu.com/mirrors.aliyun.com/g' /etc/apt/sources.list && \
    apt-get update && \
    apt-get install -y software-properties-common && \
    add-apt-repository -y ppa:deadsnakes/ppa && \
    apt-get update && \
    apt-get install -y \
        python${PYTHON_VERSION} \
        python${PYTHON_VERSION}-dev \
        python${PYTHON_VERSION}-distutils \
        python3-pip \
        gcc \
        g++ \
        && \
    # 创建 Python 3.10 符号链接
    ln -sf /usr/bin/python${PYTHON_VERSION} /usr/bin/python3 && \
    ln -sf /usr/bin/python${PYTHON_VERSION} /usr/bin/python && \
    # 更新 pip
    curl -sS https://bootstrap.pypa.io/get-pip.py | python${PYTHON_VERSION} && \
    # 清理缓存
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# 复制Python代码和依赖文件
COPY risk-warning-bert/bert-service ./bert-service
COPY risk-warning-bert/classify-service ./classify-service

# 使用国内pip源安装依赖
WORKDIR /app/bert-service
RUN pip install --no-cache-dir -i https://pypi.tuna.tsinghua.edu.cn/simple -r requirements.txt

# 第三阶段：运行阶段 - 改用 Ubuntu 基础镜像
FROM ubuntu:22.04

# 设置环境变量
ENV DEBIAN_FRONTEND=noninteractive
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="$JAVA_HOME/bin:$PATH"

WORKDIR /app

# 配置 Ubuntu 阿里云源并安装运行时环境
RUN sed -i 's/archive.ubuntu.com/mirrors.aliyun.com/g' /etc/apt/sources.list && \
    sed -i 's/security.ubuntu.com/mirrors.aliyun.com/g' /etc/apt/sources.list && \
    apt-get update && \
    apt-get install -y \
        openjdk-17-jre-headless \
        python3.10 \
        python3-pip \
        curl \
        && \
    # 安装特定版本的 JDK 17
    mkdir -p /opt/java && \
    curl -L https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.8%2B7/OpenJDK17U-jre_x64_linux_hotspot_17.0.8_7.tar.gz -o /tmp/openjdk.tar.gz && \
    tar -xzf /tmp/openjdk.tar.gz -C /opt/java && \
    mv /opt/java/jdk-17.0.8+7-jre /opt/java/openjdk && \
    # 清理缓存
    apt-get clean && \
    rm -rf /var/lib/apt/lists/* /tmp/openjdk.tar.gz

# 复制Java构建结果
COPY --from=builder /app/risk-warning-bert/target/*.jar app.jar

# 复制Python代码和依赖
COPY --from=python-builder /app/bert-service ./bert-service
COPY --from=python-builder /app/classify-service ./classify-service
# 复制 Python 可执行文件
COPY --from=python-builder /usr/bin/python3.10 /usr/bin/python3.10
COPY --from=python-builder /usr/bin/python3 /usr/bin/python3
COPY --from=python-builder /usr/bin/python /usr/bin/python

# 复制启动脚本
COPY risk-warning-bert/start.sh ./start.sh
RUN chmod +x start.sh

# 创建用户
RUN useradd -m appuser && \
    chown -R appuser:appuser /app
USER appuser

# 暴露端口
EXPOSE 8080

# 启动服务
ENTRYPOINT ["./start.sh"]