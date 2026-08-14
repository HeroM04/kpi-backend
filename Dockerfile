# syntax=docker/dockerfile:1

# ============================================================
# KPI Backend — Spring Boot 4 (Java 17)
# Multi-stage: build bằng Maven → chạy trên JRE gọn nhẹ
# ============================================================

# ---- Stage 1: Build ----
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app

# Cache dependencies: layer này chỉ chạy lại khi pom.xml thay đổi
COPY pom.xml .
RUN mvn -B -q dependency:go-offline || true

# Build jar (bỏ test để build nhanh, không cần DB lúc build)
COPY src ./src
RUN mvn -B -q -DskipTests clean package

# ---- Stage 2: Runtime ----
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copy jar từ stage build
COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8088

# Mặc định chạy profile prod (đọc DATABASE_URL, AWS_* từ biến môi trường)
ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
