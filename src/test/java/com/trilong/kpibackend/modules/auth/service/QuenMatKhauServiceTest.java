package com.trilong.kpibackend.modules.auth.service;

import com.trilong.kpibackend.modules.auth.entity.KhoaKhoiPhuc;
import com.trilong.kpibackend.modules.auth.repository.KhoaKhoiPhucRepository;
import com.trilong.kpibackend.modules.user.entity.User;
import com.trilong.kpibackend.modules.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Quên mật khẩu trên app: chỉ chiếc điện thoại từng đăng nhập đúng tài khoản
 * mới tự đặt lại được về 123456 — biết số điện thoại thôi thì không.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QuenMatKhauServiceTest {

    @Mock KhoaKhoiPhucRepository khoaRepository;
    @Mock UserRepository userRepository;
    @Mock PhienDangNhapService phienDangNhapService;
    final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    QuenMatKhauService service;

    User sale;
    final List<KhoaKhoiPhuc> daLuu = new ArrayList<>();

    @BeforeEach
    void chuanBi() {
        service = new QuenMatKhauService(khoaRepository, userRepository, encoder, phienDangNhapService);
        sale = User.builder().id(7L).fullName("Sale A").phoneNumber("0912345678")
                .passwordHash(encoder.encode("mat-khau-cu")).role("SALE").status("ACTIVE").build();
        when(userRepository.findById(7L)).thenReturn(Optional.of(sale));
        when(khoaRepository.save(any())).thenAnswer(i -> {
            KhoaKhoiPhuc k = i.getArgument(0);
            if (k.getId() == null) { k.setId(100L + daLuu.size()); daLuu.add(0, k); }
            return k;
        });
        when(khoaRepository.cuaNhanSu(7L)).thenAnswer(i -> new ArrayList<>(daLuu));
        when(khoaRepository.timTheoMaBam(anyString())).thenAnswer(i -> daLuu.stream()
                .filter(k -> k.getMaBam().equals(i.getArgument(0))).findFirst());
    }

    @Test
    @DisplayName("Cấp khóa: trả khóa ngẫu nhiên cho máy, máy chủ chỉ lưu mã băm")
    void capKhoa() {
        String a = service.capKhoa(sale, "Dart/3.5 (dart:io)");
        String b = service.capKhoa(sale, "Dart/3.5 (dart:io)");

        assertThat(a).hasSizeGreaterThanOrEqualTo(40).isNotEqualTo(b);
        assertThat(daLuu).extracting(KhoaKhoiPhuc::getMaBam)
                .containsExactly(QuenMatKhauService.bam(b), QuenMatKhauService.bam(a))
                .doesNotContain(a, b);
    }

    @Test
    @DisplayName("Cấp quá số khóa giữ tối đa thì xóa các khóa cũ nhất")
    void giuToiDa() {
        for (int i = 0; i < QuenMatKhauService.GIU_TOI_DA + 2; i++) service.capKhoa(sale, "Dart");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<KhoaKhoiPhuc>> xoa = ArgumentCaptor.forClass(List.class);
        verify(khoaRepository, atLeastOnce()).deleteAll(xoa.capture());
        // Lần cấp cuối: danh sách có GIU_TOI_DA + 2 khóa → xóa 2 khóa cũ nhất
        assertThat(xoa.getValue()).extracting(KhoaKhoiPhuc::getId).containsExactly(101L, 100L);
    }

    @Test
    @DisplayName("Đúng số điện thoại + khóa của máy → mật khẩu về 123456, đá mọi máy khác, giữ khóa máy này")
    void datLaiDung() {
        String khoa = service.capKhoa(sale, "Dart");

        service.datLaiMatKhau(" 0912345678 ", khoa);

        assertThat(encoder.matches("123456", sale.getPasswordHash())).isTrue();
        verify(userRepository).save(sale);
        verify(phienDangNhapService).dangXuatMoiThietBi(7L, 100L);
        assertThat(daLuu.get(0).getLanDungCuoi()).isNotNull();
    }

    @Test
    @DisplayName("Biết số điện thoại nhưng không có khóa của máy chính chủ → không đổi gì")
    void khongCoKhoa() {
        service.capKhoa(sale, "Dart");

        assertThatThrownBy(() -> service.datLaiMatKhau("0912345678", "khoa-doan-bua"))
                .hasMessageContaining("chưa từng đăng nhập");
        assertThatThrownBy(() -> service.datLaiMatKhau("0912345678", null))
                .hasMessageContaining("chưa từng đăng nhập");
        assertThat(encoder.matches("mat-khau-cu", sale.getPasswordHash())).isTrue();
        verify(phienDangNhapService, never()).dangXuatMoiThietBi(anyLong(), any());
    }

    @Test
    @DisplayName("Khóa của tài khoản A không đặt lại được tài khoản B — cùng một câu trả lời chung")
    void khoaCuaNguoiKhac() {
        String khoa = service.capKhoa(sale, "Dart");

        assertThatThrownBy(() -> service.datLaiMatKhau("0987654321", khoa))
                .hasMessage(QuenMatKhauService.LOI_CHUNG);
        assertThat(encoder.matches("mat-khau-cu", sale.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("Tài khoản đã bị khóa thì không tự đặt lại được")
    void taiKhoanBiKhoa() {
        String khoa = service.capKhoa(sale, "Dart");
        sale.setStatus("INACTIVE");

        assertThatThrownBy(() -> service.datLaiMatKhau("0912345678", khoa)).hasMessageContaining("bị khóa");
        assertThat(encoder.matches("mat-khau-cu", sale.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("App đang đăng nhập hỏi lại khóa: còn hiệu lực thì giữ nguyên, mất / của người khác thì cấp mới")
    void giuHoacCapMoi() {
        String khoa = service.capKhoa(sale, "Dart");
        assertThat(service.giuHoacCapMoi(7L, khoa, "Dart")).isEqualTo(khoa);
        assertThat(daLuu).hasSize(1);

        String moi = service.giuHoacCapMoi(7L, null, "Dart");
        assertThat(moi).isNotEqualTo(khoa);
        assertThat(daLuu).hasSize(2);

        // Khóa của người khác (userId 9) gửi lên dưới tài khoản 7 → không nhận, cấp khóa riêng cho 7
        daLuu.get(0).setUser(User.builder().id(9L).build());
        String khoaCua9 = moi;
        assertThat(service.giuHoacCapMoi(7L, khoaCua9, "Dart")).isNotEqualTo(khoaCua9);
    }
}
