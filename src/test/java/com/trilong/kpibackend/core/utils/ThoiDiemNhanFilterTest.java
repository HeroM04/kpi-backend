package com.trilong.kpibackend.core.utils;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class ThoiDiemNhanFilterTest {

    @Test
    @DisplayName("Ghi mốc nhận trước khi chuyển yêu cầu đi tiếp")
    void ghiMocTruocKhiXuLy() throws Exception {
        long truoc = System.currentTimeMillis();
        AtomicLong mocTrongXuLy = new AtomicLong();

        new ThoiDiemNhanFilter().doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                (rq, rs) -> mocTrongXuLy.set(ThoiDiemNhanFilter.cua((HttpServletRequest) rq)));

        assertTrue(mocTrongXuLy.get() >= truoc);
        assertTrue(mocTrongXuLy.get() <= System.currentTimeMillis());
    }

    @Test
    @DisplayName("Mốc không trôi theo thời gian xử lý")
    void mocKhongTroi() {
        MockHttpServletRequest rq = new MockHttpServletRequest();
        rq.setAttribute(ThoiDiemNhanFilter.THUOC_TINH, 1_000L);
        assertEquals(1_000L, ThoiDiemNhanFilter.cua(rq));
    }

    @Test
    @DisplayName("Không có mốc (gọi ngoài luồng HTTP) thì lấy giờ hiện tại")
    void khongCoMoc() {
        long truoc = System.currentTimeMillis();
        assertTrue(ThoiDiemNhanFilter.cua(null) >= truoc);
        assertTrue(ThoiDiemNhanFilter.cua(new MockHttpServletRequest()) >= truoc);
    }

    @Test
    @DisplayName("Chạy trước bộ lọc bảo mật — không thì mốc đã tính cả phần chờ DB lúc xác thực")
    void chayDauTien() {
        assertEquals(Ordered.HIGHEST_PRECEDENCE, ThoiDiemNhanFilter.class.getAnnotation(Order.class).value());
    }
}
