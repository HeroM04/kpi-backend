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
# Bộ nhớ cho container 512 MB (Render Free). Cả JVM phải nằm gọn trong 512 MB,
# không chỉ heap: đo bằng Native Memory Tracking (08/11/2026), ứng dụng này
# ngoài heap đã chiếm ~200 MB (metaspace ~110, symbol ~47, code, luồng...).
# Trước đây heap 60% RAM (~307 MB) + ~200 MB ngoài heap = sát 512 MB: ngày
# 08/11 nén một ảnh 11 MB làm heap phình lên và Render tắt container ("Ran out
# of memory"). Heap thật chỉ dùng ~75 MB lúc rảnh, ~130 MB lúc nén ảnh lớn.
#   -Xmx200m                   heap tối đa 200 MB → tổng đo được ~405 MB
#   -XX:TieredStopAtLevel=1    chỉ dùng trình biên dịch C1: mã máy nhỏ hơn, khởi
#                              động nhanh hơn trên 0,1 CPU, bớt bộ nhớ biên dịch
#   -XX:ReservedCodeCacheSize  trần vùng mã máy (mặc định 240 MB, lớn dần theo giờ)
#   -XX:MaxDirectMemorySize    trần bộ đệm ngoài heap (mặc định = heap)
#   -XX:+PrintCommandLineFlags in cờ JVM vào log Render lúc khởi động để kiểm tra
# Serial GC gọn nhất ở 1 CPU; -Xss512k giảm bộ nhớ mỗi luồng Tomcat.
ENV JAVA_OPTS="-Xmx200m -Xss512k -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:ReservedCodeCacheSize=48m -XX:MaxDirectMemorySize=32m -XX:+PrintCommandLineFlags"
# glibc mặc định mở nhiều vùng cấp phát (8 × số CPU) cho các luồng → bộ nhớ
# tiến trình phình mà JVM không thấy. 2 vùng là đủ cho container 1 CPU.
ENV MALLOC_ARENA_MAX=2

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
