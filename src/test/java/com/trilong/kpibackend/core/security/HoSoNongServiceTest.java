package com.trilong.kpibackend.core.security;

import com.trilong.kpibackend.modules.auth.repository.RefreshTokenRepository;
import com.trilong.kpibackend.modules.user.entity.Department;
import com.trilong.kpibackend.modules.user.entity.User;
import com.trilong.kpibackend.modules.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bản chụp quyền (vai trò, phòng ban, trạng thái) đọc từ DB thay vì tin token.
 * Mọi yêu cầu API đều đi qua đây — sai là hoặc quyền cũ còn hiệu lực cả tiếng,
 * hoặc DB bị hỏi ở mỗi cú bấm.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HoSoNongServiceTest {

    @Mock UserRepository userRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @InjectMocks HoSoNongService service;

    private User nguoi;

    @BeforeEach
    void chuanBi() {
        nguoi = User.builder().id(7L).role("SALE").status("ACTIVE")
                .department(Department.builder().id(8L).build()).build();
        when(userRepository.findById(7L)).thenReturn(Optional.of(nguoi));
        when(refreshTokenRepository.timIdPhienConHieuLuc(eq(7L), any())).thenReturn(List.of(501L));
    }

    @Test
    @DisplayName("Đọc DB một lần, các yêu cầu sau trong 60 giây dùng bản chụp")
    void dungBanChup() {
        service.lay(7L);
        service.lay(7L);
        service.lay(7L);
        verify(userRepository, times(1)).findById(7L);
    }

    @Test
    @DisplayName("Admin sửa → quên → yêu cầu kế tiếp đã thấy vai trò/phòng mới")
    void quenLaThayMoi() {
        assertThat(service.lay(7L).orElseThrow().departmentId()).isEqualTo(8L);

        nguoi.setRole("TRUONG_PHONG");
        nguoi.setDepartment(Department.builder().id(28L).build());
        service.quen(7L);

        var b = service.lay(7L).orElseThrow();
        assertThat(b.role()).isEqualTo("TRUONG_PHONG");
        assertThat(b.departmentId()).isEqualTo(28L);
    }

    @Test
    @DisplayName("Tài khoản bị khóa → bản chụp báo không hoạt động")
    void biKhoa() {
        nguoi.setStatus("INACTIVE");
        assertThat(service.lay(7L).orElseThrow().dangHoatDong()).isFalse();
    }

    @Test
    @DisplayName("DB lỗi → dùng tạm bản chụp cũ, không chặn người dùng vì DB chậm")
    void dbLoi() {
        service.lay(7L);
        // Buộc hết hạn bằng cách quên rồi cho DB lỗi: không có bản cũ → rỗng (rơi về token)
        service.quen(7L);
        when(userRepository.findById(7L)).thenThrow(new RuntimeException("mất kết nối"));
        assertThat(service.lay(7L)).isEmpty();
    }

    @Test
    @DisplayName("Người không tồn tại → rỗng")
    void khongCo() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        assertThat(service.lay(99L)).isEmpty();
        assertThat(service.lay(null)).isEmpty();
    }
}
