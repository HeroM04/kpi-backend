package com.trilong.kpibackend.modules.auth.service;

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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Quên mật khẩu trên app: nhập số điện thoại là mật khẩu về 123456 (trừ Admin). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QuenMatKhauServiceTest {

    @Mock UserRepository userRepository;
    final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    QuenMatKhauService service;
    User sale;

    @BeforeEach
    void chuanBi() {
        service = new QuenMatKhauService(userRepository, encoder);
        sale = User.builder().id(7L).fullName("Sale A").phoneNumber("0912345678")
                .passwordHash(encoder.encode("mat-khau-cu")).role("SALE").status("ACTIVE").build();
        when(userRepository.findByPhoneNumber(anyString())).thenReturn(Optional.empty());
        when(userRepository.findByPhoneNumber("0912345678")).thenReturn(Optional.of(sale));
    }

    @Test
    @DisplayName("Nhập đúng số điện thoại → mật khẩu về 123456")
    void datLai() {
        service.datLaiMatKhau(" 0912345678 ");

        assertThat(encoder.matches("123456", sale.getPasswordHash())).isTrue();
        verify(userRepository).save(sale);
    }

    @Test
    @DisplayName("Số không có tài khoản / để trống → báo lỗi, không đổi gì")
    void khongCoTaiKhoan() {
        assertThatThrownBy(() -> service.datLaiMatKhau("0987654321")).hasMessageContaining("Không tìm thấy tài khoản");
        assertThatThrownBy(() -> service.datLaiMatKhau("  ")).hasMessageContaining("Nhập số điện thoại");
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Tài khoản đã khóa thì không đặt lại")
    void biKhoa() {
        sale.setStatus("INACTIVE");
        assertThatThrownBy(() -> service.datLaiMatKhau("0912345678")).hasMessageContaining("bị khóa");
        assertThat(encoder.matches("mat-khau-cu", sale.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("Tài khoản Admin không tự đặt lại được (Web Admin có hoa hồng, doanh thu)")
    void admin() {
        sale.setRole("ADMIN");
        assertThatThrownBy(() -> service.datLaiMatKhau("0912345678")).hasMessageContaining("Admin");
        assertThat(encoder.matches("mat-khau-cu", sale.getPasswordHash())).isTrue();
        verify(userRepository, never()).save(any());
    }
}
