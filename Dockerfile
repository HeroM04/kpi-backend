# Bước 1: Build environment (Môi trường build)
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

# Copy toàn bộ source code (bao gồm pom.xml và thư mục src)
COPY pom.xml .
COPY src ./src

# Chạy Maven để build file .jar, bỏ qua quá trình chạy test
RUN mvn clean package -DskipTests

# Bước 2: Run environment (Môi trường chạy)
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Mở cổng 8088 cho ứng dụng Spring Boot
EXPOSE 8088

# Copy file .jar đã được build từ bước 1 sang bước 2
# Đổi tên file thành app.jar cho ngắn gọn và dễ quản lý
COPY --from=build /app/target/*.jar app.jar

# Thiết lập lệnh khởi chạy ứng dụng
ENTRYPOINT ["java", "-jar", "app.jar"]
