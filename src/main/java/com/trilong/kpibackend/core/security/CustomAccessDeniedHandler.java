package com.trilong.kpibackend.core.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trilong.kpibackend.core.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * CustomAccessDeniedHandler — xử lý khi user ĐÃ đăng nhập nhưng KHÔNG CÓ QUYỀN.
 *
 * Ví dụ: Nhân viên SALE cố truy cập endpoint /api/v1/admin/ của ADMIN.
 * Trả về JSON 403 thay vì trang HTML mặc định của Spring Security:
 * { "status": "FORBIDDEN", "message": "Bạn không có quyền..." }
 */
@Component
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        // Lấy role hiện tại của user để thông báo cụ thể hơn
        String uri = request.getRequestURI();
        ErrorResponse error = new ErrorResponse(
                "FORBIDDEN",
                "Bạn không có quyền truy cập tài nguyên này. Endpoint: " + uri
        );

        response.getWriter().write(objectMapper.writeValueAsString(error));
    }
}
