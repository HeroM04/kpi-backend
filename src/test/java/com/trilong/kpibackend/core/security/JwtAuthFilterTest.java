package com.trilong.kpibackend.core.security;

import com.trilong.kpibackend.modules.auth.service.PhienDangNhapService;
import com.trilong.kpibackend.modules.user.entity.Department;
import com.trilong.kpibackend.modules.user.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Cửa kiểm tra MỌI yêu cầu API. Dùng token thật (ký bằng khóa thật), chỉ giả
 * bản chụp DB — để chứng minh "đăng xuất mọi thiết bị" chặn được cả token còn
 * hạn đang nằm trên máy người khác, và quyền đổi trên web có hiệu lực ngay.
 */
class JwtAuthFilterTest {

    private final JwtUtils jwt = new JwtUtils();
    private final HoSoNongService hoSoNong = mock(HoSoNongService.class);
    private final PhienDangNhapService phien = mock(PhienDangNhapService.class);
    private final JwtAuthFilter filter = new JwtAuthFilter();

    private final User nguoi = User.builder().id(7L).phoneNumber("0912345678").fullName("Sale A")
            .role("SALE").department(Department.builder().id(8L).build()).build();

    @BeforeEach
    void chuanBi() {
        ReflectionTestUtils.setField(jwt, "jwtSecret", "chi-dung-cho-kiem-thu-khong-phai-khoa-that-32ky-tu");
        ReflectionTestUtils.setField(jwt, "jwtExpirationMs", 3_600_000L);
        ReflectionTestUtils.setField(filter, "jwtUtils", jwt);
        ReflectionTestUtils.setField(filter, "hoSoNong", hoSoNong);
        ReflectionTestUtils.setField(filter, "phienDangNhap", phien);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void donDep() {
        SecurityContextHolder.clearContext();
    }

    private HoSoNongService.BanChup banChup(String vaiTro, Long phong, String trangThai,
                                            ZonedDateTime phienHopLeTu, Set<Long> phienConHieuLuc) {
        return new HoSoNongService.BanChup(vaiTro, phong, trangThai, "Sale A", null,
                phienHopLeTu, phienConHieuLuc, System.currentTimeMillis() + 60_000);
    }

    private Authentication goi(String token) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/kpi-scores/my");
        req.addHeader("Authorization", "Bearer " + token);
        filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());
        return SecurityContextHolder.getContext().getAuthentication();
    }

    @Test
    @DisplayName("Token hợp lệ → vào được, vai trò và phòng ban lấy theo DB (không theo token)")
    void theoDb() throws Exception {
        String token = jwt.generateToken(nguoi, 501L);
        when(hoSoNong.lay(7L)).thenReturn(Optional.of(banChup("TRUONG_PHONG", 28L, "ACTIVE", null, Set.of(501L))));

        Authentication a = goi(token);

        assertThat(a).isNotNull();
        UserPrincipal p = (UserPrincipal) a.getPrincipal();
        assertThat(p.getRole()).isEqualTo("TRUONG_PHONG");   // token nói SALE, DB nói TRUONG_PHONG
        assertThat(p.getDepartmentId()).isEqualTo(28L);      // token nói 8, DB nói 28
        assertThat(p.getSessionId()).isEqualTo(501L);
        assertThat(a.getAuthorities()).extracting(Object::toString).contains("ROLE_TRUONG_PHONG");
        verify(phien).ghiNhanHoatDong(501L);
    }

    @Test
    @DisplayName("Đã bấm 'Đăng xuất mọi thiết bị' → token phát trước đó bị chặn dù còn hạn")
    void chanSauKhiDangXuatTatCa() throws Exception {
        String token = jwt.generateToken(nguoi, 501L);
        Thread.sleep(1100); // iat tính theo giây
        when(hoSoNong.lay(7L)).thenReturn(Optional.of(banChup("SALE", 8L, "ACTIVE", ZonedDateTime.now(), Set.of(501L))));

        assertThat(goi(token)).isNull();
    }

    @Test
    @DisplayName("Riêng máy này bị gỡ → token của máy này bị chặn")
    void chanMayBiGo() throws Exception {
        String token = jwt.generateToken(nguoi, 501L);
        when(hoSoNong.lay(7L)).thenReturn(Optional.of(banChup("SALE", 8L, "ACTIVE", null, Set.of(777L))));
        assertThat(goi(token)).isNull();
    }

    @Test
    @DisplayName("Tài khoản bị khóa → chặn ngay, không đợi token hết hạn")
    void chanTaiKhoanKhoa() throws Exception {
        String token = jwt.generateToken(nguoi, 501L);
        when(hoSoNong.lay(7L)).thenReturn(Optional.of(banChup("SALE", 8L, "INACTIVE", null, Set.of(501L))));
        assertThat(goi(token)).isNull();
    }

    @Test
    @DisplayName("Token đời cũ không có sid vẫn dùng được (không đá cả công ty ra khi lên bản mới)")
    void tokenCuKhongCoSid() throws Exception {
        String token = jwt.generateToken(nguoi);   // không gắn phiên
        when(hoSoNong.lay(7L)).thenReturn(Optional.of(banChup("SALE", 8L, "ACTIVE", null, Set.of(501L))));
        assertThat(goi(token)).isNotNull();
    }

    @Test
    @DisplayName("DB không trả lời → dùng tạm dữ liệu trong token, không chặn người dùng")
    void dbKhongTraLoi() throws Exception {
        String token = jwt.generateToken(nguoi, 501L);
        when(hoSoNong.lay(anyLong())).thenReturn(Optional.empty());

        UserPrincipal p = (UserPrincipal) goi(token).getPrincipal();
        assertThat(p.getRole()).isEqualTo("SALE");
    }

    @Test
    @DisplayName("Token giả / sửa chữ ký → không vào được")
    void tokenGia() throws Exception {
        String token = jwt.generateToken(nguoi, 501L);
        String sua = token.substring(0, token.length() - 3) + (token.endsWith("aaa") ? "bbb" : "aaa");
        assertThat(goi(sua)).isNull();
        assertThat(goi("khong-phai-token")).isNull();
    }
}
