# Stage 1: Build the Maven project
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app

# Copy the pom.xml and source code
COPY pom.xml .
COPY src ./src

# Build the jar file skipping tests to speed up the process
RUN mvn clean package -DskipTests

# Stage 2: Create the minimal runtime image
FROM eclipse-temurin:17-jre

WORKDIR /app

# Khai báo volume cho thư mục upload nếu dùng storage local
VOLUME /app/uploads

# Expose port
EXPOSE 8088

# Môi trường chạy mặc định là prod
ENV SPRING_PROFILES_ACTIVE=prod

# Copy jar từ stage 1 sang stage 2
COPY --from=builder /app/target/*.jar app.jar

# Chạy ứng dụng
ENTRYPOINT ["java", "-jar", "app.jar"]
