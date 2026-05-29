package com.trilong.kpibackend.core.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger UI Configuration.
 * Truy cập Swagger UI tại: http://localhost:8080/swagger-ui/index.html
 *
 * Cách test API với JWT:
 * 1. Gọi POST /api/v1/auth/login để lấy accessToken
 * 2. Click nút "Authorize" (🔓) ở góc phải Swagger UI
 * 3. Nhập: Bearer eyJhbGci...
 * 4. Click "Authorize" → tất cả endpoint protected sẽ tự động gửi token
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "KPI System API — Trí Long",
                version = "2.0.0",
                description = """
                        **Hệ thống chấm KPI nhân viên kinh doanh BĐS Trí Long**
                        
                        ## Luồng đăng nhập:
                        1. `POST /api/v1/auth/login` → nhận `accessToken` + `refreshToken`
                        2. Gửi `Authorization: Bearer {accessToken}` trong header mỗi request
                        3. Khi access token hết hạn (1h): dùng `POST /api/v1/auth/refresh`
                        
                        ## 4 Role trong hệ thống:
                        - **SALE**: Nhân viên kinh doanh
                        - **TRUONG_PHONG**: Trưởng phòng kinh doanh
                        - **VAN_PHONG**: Nhân viên văn phòng / Kế toán
                        - **ADMIN**: Quản trị hệ thống / HR
                        """,
                contact = @Contact(
                        name = "Dev Team Trí Long",
                        email = "dev@trilong.vn"
                ),
                license = @License(name = "Internal use only")
        ),
        servers = {
                @Server(url = "http://localhost:8080", description = "Local Development"),
                @Server(url = "https://api.trilong.vn", description = "Production")
        },
        security = @SecurityRequirement(name = "Bearer Authentication")
)
@SecurityScheme(
        name = "Bearer Authentication",
        type = SecuritySchemeType.HTTP,
        bearerFormat = "JWT",
        scheme = "bearer",
        description = "Nhập JWT access token nhận được từ POST /api/v1/auth/login"
)
public class OpenApiConfig {
}
