# 网关服务Dockerfile
FROM openjdk:8-jdk-slim

WORKDIR /app

COPY target/risk-warning-gateway-*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
