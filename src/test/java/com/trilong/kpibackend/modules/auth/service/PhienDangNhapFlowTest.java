package com.trilong.kpibackend.modules.auth.service;

import com.trilong.kpibackend.core.security.HoSoNongService;
import com.trilong.kpibackend.modules.auth.entity.RefreshToken;
import com.trilong.kpibackend.modules.auth.repository.RefreshTokenRepository;
import com.trilong.kpibackend.modules.user.entity.User;
import com.trilong.kpibackend.modules.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Xem máy nào đang đăng nhập và đá ra khi cần — tính năng xử lý việc tài khoản
 * admin bị dùng trên máy lạ.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PhienDangNhapFlowTest {

    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock UserRepository userRepository;
    @Mock HoSoNongService hoSoNong;
    @Mock PhienDangNhapService.GhiHoatDongNen ghiNen;
    PhienDangNhapService service;

    private final User admin = User.builder().id(1L).fullName("Admin").build();

    @BeforeEach
    void chuanBi() {
        service = new PhienDangNhapService(refreshTokenRepository, userRepository, hoSoNong, ghiNen);
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("Đăng xuất mọi thiết bị: thu hồi hết phiên, đặt mốc chặn token cũ, quên bản chụp quyền")
    void dangXuatTatCa() {
        when(refreshTokenRepository.thuHoiTatCa(1L)).thenReturn(3);
        ZonedDateTime truoc = ZonedDateTime.now();

        int so = service.dangXuatMoiThietBi(1L);

        assertThat(so).isEqualTo(3);
        assertThat(admin.getSessionsValidFrom()).isAfter(truoc);   // token phát trước mốc này bị chặn
        verify(hoSoNong).quen(1L);
    }

    @Test
    @DisplayName("Gỡ một máy: chỉ thu hồi phiên đó và quên bản chụp để máy đó bị chặn ngay")
    void goMotMay() {
        when(refreshTokenRepository.thuHoiMotPhien(501L, 1L)).thenReturn(1);
        assertThat(service.thuHoi(1L, 501L)).isTrue();
        verify(hoSoNong).quen(1L);

        // Không gỡ được phiên của người khác
        when(refreshTokenRepository.thuHoiMotPhien(502L, 1L)).thenReturn(0);
        assertThat(service.thuHoi(1L, 502L)).isFalse();
    }

    @Test
    @DisplayName("Danh sách phiên: đánh dấu máy đang xem, đọc tên thiết bị dễ hiểu")
    void danhSach() {
        RefreshToken a = RefreshToken.builder().id(501L).deviceInfo("Mozilla/5.0 (Windows NT 10.0) Chrome/140.0").build();
        RefreshToken b = RefreshToken.builder().id(502L).deviceInfo("Dart/3.5 (dart:io) Android").build();
        when(refreshTokenRepository.timPhienConHieuLuc(eq(1L), any())).thenReturn(List.of(a, b));

        var ds = service.danhSach(1L, 502L);

        assertThat(ds).extracting(PhienDangNhapService.Phien::thietBi)
                .containsExactly("Chrome trên Windows", "App Trí Long (Android)");
        assertThat(ds).extracting(PhienDangNhapService.Phien::phienHienTai).containsExactly(false, true);
    }

    @Test
    @DisplayName("Ghi 'còn hoạt động' tối đa một lần mỗi phút mỗi phiên — không đè DB mỗi cú bấm")
    void nhipGhi() {
        for (int i = 0; i < 50; i++) service.ghiNhanHoatDong(501L);
        service.ghiNhanHoatDong(502L);
        service.ghiNhanHoatDong(null);

        verify(ghiNen, times(1)).ghi(501L);
        verify(ghiNen, times(1)).ghi(502L);
        verifyNoMoreInteractions(ghiNen);
    }
}
