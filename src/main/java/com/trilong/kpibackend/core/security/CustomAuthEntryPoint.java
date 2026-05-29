package com.trilong.kpibackend.core.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trilong.kpibackend.core.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * CustomAuthEntryPoint — xử lý khi request KHÔNG có token hoặc token hết hạn.
 *
 * Mặc định Spring Security redirect về trang login HTML (401 với redirect).
 * Class này override để trả về JSON chuẩn thay vì HTML:
 * { "status": "UNAUTHORIZED", "message": "..." }
 *
 * Áp dụng cho:
 * - Request không có Authorization header
 * - Token đã hết hạn (expired)
 * - Token bị tampered (chữ ký không hợp lệ)
 */
@Component
public class CustomAuthEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ErrorResponse error = new ErrorResponse(
                "UNAUTHORIZED",
                "Bạn chưa đăng nhập hoặc phiên làm việc đã hết hạn. Vui lòng đăng nhập lại."
        );

        response.getWriter().write(objectMapper.writeValueAsString(error));
    }
}
