package com.trilong.kpibackend.core.utils;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Ghi lại lúc máy chủ vừa nhận một yêu cầu, trước mọi bước khác.
 *
 * <p>Dùng để kiểm tra mã QR điểm danh theo lúc học viên GỬI tới, không phải
 * lúc xử lý tới nơi. Đầu buổi đào tạo cả lớp quét cùng lúc, còn bước xác thực
 * phải đọc hồ sơ từ DB mà máy chủ chỉ có 5 kết nối — yêu cầu xếp hàng chờ có
 * khi tới 15 giây, tới lượt kiểm tra thì mã đã đổi, học viên quét đúng mã
 * đang chiếu vẫn bị báo "Mã QR đã hết hạn" (buổi 25/09/2026).
 *
 * <p>Chạy trước bộ lọc bảo mật (thứ tự cao nhất) để mốc này không tính phần
 * chờ DB.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ThoiDiemNhanFilter extends OncePerRequestFilter {

    static final String THUOC_TINH = ThoiDiemNhanFilter.class.getName();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        request.setAttribute(THUOC_TINH, System.currentTimeMillis());
        chain.doFilter(request, response);
    }

    /** Lúc máy chủ nhận yêu cầu này; không có mốc thì lấy giờ hiện tại. */
    public static long cua(HttpServletRequest request) {
        Object v = request == null ? null : request.getAttribute(THUOC_TINH);
        return v instanceof Long l ? l : System.currentTimeMillis();
    }
}
