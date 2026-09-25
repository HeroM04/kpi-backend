package com.trilong.kpibackend.modules.auth.service;

import com.trilong.kpibackend.core.security.HoSoNongService;
import com.trilong.kpibackend.core.security.JwtUtils;
import com.trilong.kpibackend.core.security.UserPrincipal;
import com.trilong.kpibackend.modules.auth.dto.ChangePasswordRequestDTO;
import com.trilong.kpibackend.modules.auth.dto.LoginRequestDTO;
import com.trilong.kpibackend.modules.auth.dto.RefreshTokenRequestDTO;
import com.trilong.kpibackend.modules.auth.entity.RefreshToken;
import com.trilong.kpibackend.modules.auth.repository.RefreshTokenRepository;
import com.trilong.kpibackend.modules.user.entity.User;
import com.trilong.kpibackend.modules.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.ZonedDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Đăng nhập, làm mới phiên, đổi mật khẩu. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Spy BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    @Mock JwtUtils jwtUtils;
    @Mock HoSoNongService hoSoNong;
    @Mock PhienDangNhapService phienDangNhapService;
    @Mock HttpServletRequest http;
    @InjectMocks AuthService service;

    private User nguoi;

    @BeforeEach
    void chuanBi() {
        ReflectionTestUtils.setField(service, "accessTokenExpMs", 3_600_000L);
        ReflectionTestUtils.setField(service, "refreshTokenExpMs", 604_800_000L);
        nguoi = User.builder().id(7L).fullName("Sale A").phoneNumber("0912345678")
                .passwordHash(passwordEncoder.encode("dung-mat-khau")).role("SALE").status("ACTIVE").build();
        when(userRepository.findByPhoneNumber("0912345678")).thenReturn(Optional.of(nguoi));
        when(userRepository.findById(7L)).thenReturn(Optional.of(nguoi));
        when(refreshTokenRepository.save(any())).thenAnswer(i -> {
            RefreshToken rt = i.getArgument(0);
            if (rt.getId() == null) rt.setId(501L);
            return rt;
        });
        when(jwtUtils.generateToken(any(User.class), any())).thenReturn("jwt");
        when(http.getHeader("User-Agent")).thenReturn("Mozilla/5.0 (Windows NT 10.0) Chrome/140.0");
        when(http.getRemoteAddr()).thenReturn("113.161.44.7");
    }

    private LoginRequestDTO dangNhap(String sdt, String mk) {
        LoginRequestDTO d = new LoginRequestDTO();
        d.setPhoneNumber(sdt); d.setPassword(mk);
        return d;
    }

    @Test
    @DisplayName("Đúng mật khẩu → cấp token GẮN VỚI PHIÊN vừa ghi (claim sid)")
    void dangNhapDung() {
        var kq = service.login(dangNhap("0912345678", "dung-mat-khau"), http);

        assertThat(kq.getAccessToken()).isEqualTo("jwt");
        verify(jwtUtils).generateToken(nguoi, 501L);
        ArgumentCaptor<RefreshToken> phien = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(phien.capture());
        assertThat(phien.getValue().getIpAddress()).isEqualTo("113.161.44.7");
        assertThat(phien.getValue().getLastSeenAt()).isNotNull();
    }

    @Test
    @DisplayName("Sai mật khẩu / sai SĐT / tài khoản bị khóa → từ chối, không cấp phiên")
    void dangNhapSai() {
        assertThatThrownBy(() -> service.login(dangNhap("0912345678", "sai"), http)).hasMessageContaining("không chính xác");
        assertThatThrownBy(() -> service.login(dangNhap("0900000000", "x"), http)).hasMessageContaining("Không tìm thấy");
        nguoi.setStatus("INACTIVE");
        assertThatThrownBy(() -> service.login(dangNhap("0912345678", "dung-mat-khau"), http)).hasMessageContaining("khóa");
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("Làm mới phiên: thu hồi phiên cũ, cấp phiên mới gắn token mới")
    void lamMoi() {
        RefreshToken cu = RefreshToken.builder().id(400L).user(nguoi).token("cu")
                .expiresAt(ZonedDateTime.now().plusDays(3)).build();
        when(refreshTokenRepository.findByToken("cu")).thenReturn(Optional.of(cu));
        RefreshTokenRequestDTO d = new RefreshTokenRequestDTO();
        d.setRefreshToken("cu");

        service.refreshToken(d);

        assertThat(cu.isRevoked()).isTrue();
        verify(jwtUtils).generateToken(nguoi, 501L);
    }

    @Test
    @DisplayName("Tài khoản đã bị khóa thì không làm mới được phiên — dù refresh token còn hạn")
    void khongLamMoiKhiBiKhoa() {
        nguoi.setStatus("INACTIVE");
        RefreshToken cu = RefreshToken.builder().id(400L).user(nguoi).token("cu")
                .expiresAt(ZonedDateTime.now().plusDays(3)).build();
        when(refreshTokenRepository.findByToken("cu")).thenReturn(Optional.of(cu));
        RefreshTokenRequestDTO d = new RefreshTokenRequestDTO();
        d.setRefreshToken("cu");

        assertThatThrownBy(() -> service.refreshToken(d)).hasMessageContaining("khóa");
        verify(jwtUtils, never()).generateToken(any(User.class), any());
    }

    @Test
    @DisplayName("Đổi mật khẩu thành công → đăng xuất MỌI thiết bị (kể cả access token còn hạn)")
    void doiMatKhau() {
        ChangePasswordRequestDTO d = new ChangePasswordRequestDTO();
        d.setOldPassword("dung-mat-khau"); d.setNewPassword("moi-123456"); d.setConfirmPassword("moi-123456");

        service.changePassword(UserPrincipal.builder().userId(7L).build(), d);

        assertThat(passwordEncoder.matches("moi-123456", nguoi.getPasswordHash())).isTrue();
        verify(phienDangNhapService).dangXuatMoiThietBi(7L);
    }

    @Test
    @DisplayName("Đổi mật khẩu: sai mật khẩu cũ hoặc xác nhận không khớp → từ chối, giữ nguyên phiên")
    void doiMatKhauSai() {
        ChangePasswordRequestDTO d = new ChangePasswordRequestDTO();
        d.setOldPassword("sai"); d.setNewPassword("moi-123456"); d.setConfirmPassword("moi-123456");
        assertThatThrownBy(() -> service.changePassword(UserPrincipal.builder().userId(7L).build(), d))
                .hasMessageContaining("cũ không đúng");

        d.setOldPassword("dung-mat-khau"); d.setConfirmPassword("khac");
        assertThatThrownBy(() -> service.changePassword(UserPrincipal.builder().userId(7L).build(), d))
                .hasMessageContaining("không khớp");
        verify(phienDangNhapService, never()).dangXuatMoiThietBi(any());
    }
}
