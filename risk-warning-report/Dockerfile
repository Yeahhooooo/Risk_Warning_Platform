# 第一阶段：构建阶段
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
COPY risk-warning-report/pom.xml ./risk-warning-report/pom.xml
COPY risk-warning-report/src ./risk-warning-report/src

# 构建common模块
WORKDIR /app/risk-warning-common
RUN mvn clean install -DskipTests -U

# 构建当前模块
WORKDIR /app/risk-warning-report
RUN mvn dependency:go-offline -B -U
# 先执行package，再执行spring-boot:repackage确保生成可执行jar
RUN mvn clean package spring-boot:repackage -DskipTests -U

# 第二阶段：运行阶段
FROM eclipse-temurin:17.0.18_8-jre
WORKDIR /app
COPY --from=builder /app/risk-warning-report/target/*.jar app.jar
RUN useradd -m appuser
USER appuser
ENTRYPOINT ["java", "-jar", "app.jar"]
