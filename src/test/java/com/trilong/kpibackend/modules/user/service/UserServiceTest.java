package com.trilong.kpibackend.modules.user.service;

import com.trilong.kpibackend.core.security.HoSoNongService;
import com.trilong.kpibackend.modules.attendance.repository.CheckinLogRepository;
import com.trilong.kpibackend.modules.auth.service.PhienDangNhapService;
import com.trilong.kpibackend.modules.kpi.repository.KpiScoreRepository;
import com.trilong.kpibackend.modules.kpi.service.KpiCalculationService;
import com.trilong.kpibackend.modules.notification.service.PushNotificationService;
import com.trilong.kpibackend.modules.user.dto.CreateUserDTO;
import com.trilong.kpibackend.modules.user.dto.UpdateUserDTO;
import com.trilong.kpibackend.modules.user.entity.Department;
import com.trilong.kpibackend.modules.user.entity.User;
import com.trilong.kpibackend.modules.user.repository.DepartmentRepository;
import com.trilong.kpibackend.modules.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tạo / sửa / khóa / xóa nhân sự.
 *
 * <p>Quy ước sửa từng phần: trường để trống (null) là KHÔNG SỬA, muốn gỡ thì
 * truyền 0 (phòng ban, người giới thiệu). Trước đây gỡ khỏi phòng gửi null nên
 * máy chủ bỏ qua, web báo "đã gỡ" mà người vẫn nằm nguyên trong phòng.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceTest {

    @Mock UserRepository userRepository;
    @Mock DepartmentRepository departmentRepository;
    @Spy BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    @Mock KpiScoreRepository kpiScoreRepository;
    @Mock KpiCalculationService kpiCalculationService;
    @Mock CheckinLogRepository checkinLogRepository;
    @Mock HoSoNongService hoSoNong;
    @Mock PushNotificationService pushNotificationService;
    @Mock SimpMessagingTemplate messagingTemplate;
    @Mock PhienDangNhapService phienDangNhapService;
    @InjectMocks UserService service;

    private final Department kd08 = Department.builder().id(8L).name("Phòng Kinh Doanh 8").build();
    private final Department kd28 = Department.builder().id(28L).name("Phòng Kinh Doanh 28").build();
    private User nguoi;

    @BeforeEach
    void chuanBi() {
        nguoi = User.builder().id(7L).fullName("Nguyễn Văn A").phoneNumber("0912345678").passwordHash("x")
                .role("TRUONG_PHONG").status("ACTIVE").department(kd08).referrerId(3L).build();
        when(userRepository.findById(7L)).thenReturn(Optional.of(nguoi));
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(departmentRepository.findById(28L)).thenReturn(Optional.of(kd28));
        when(kpiCalculationService.extractMonth(any(java.time.ZonedDateTime.class))).thenReturn("2026-09");
    }

    @Nested
    @DisplayName("Tạo nhân sự")
    class Tao {

        private CreateUserDTO dto(String sdt, String vaiTro) {
            CreateUserDTO d = new CreateUserDTO();
            d.setFullName("Người mới"); d.setPhoneNumber(sdt); d.setPassword("123456"); d.setRole(vaiTro);
            return d;
        }

        @Test
        @DisplayName("SĐT đã có người dùng → từ chối")
        void trungSdt() {
            when(userRepository.existsByPhoneNumber("0911111111")).thenReturn(true);
            assertThatThrownBy(() -> service.createUser(dto("0911111111", "SALE")))
                    .hasMessageContaining("đã được đăng ký");
        }

        @Test
        @DisplayName("SĐT dán kèm dấu cách được cắt — không sinh tài khoản không đăng nhập được")
        void catDauCach() {
            var kq = service.createUser(dto("  0922222222 ", "SALE"));
            assertThat(kq.getPhoneNumber()).isEqualTo("0922222222");
            verify(userRepository).existsByPhoneNumber("0922222222");
        }

        @Test
        @DisplayName("Mật khẩu được băm, không lưu nguyên văn")
        void bamMatKhau() {
            service.createUser(dto("0933333333", "SALE"));
            verify(userRepository).save(argThat(u -> !"123456".equals(u.getPasswordHash())
                    && passwordEncoder.matches("123456", u.getPasswordHash())));
        }

        @Test
        @DisplayName("Sale/Trưởng phòng có sẵn bảng điểm tháng; Văn phòng thì không")
        void bangDiem() {
            service.createUser(dto("0944444444", "SALE"));
            verify(kpiScoreRepository, times(1)).save(any());
            service.createUser(dto("0955555555", "VAN_PHONG"));
            verify(kpiScoreRepository, times(1)).save(any());
        }
    }

    @Nested
    @DisplayName("Sửa nhân sự (sửa từng phần)")
    class Sua {

        @Test
        @DisplayName("Trường để trống (null) → giữ nguyên")
        void nullLaKhongSua() {
            service.updateUser(7L, new UpdateUserDTO());
            assertThat(nguoi.getDepartment()).isEqualTo(kd08);
            assertThat(nguoi.getReferrerId()).isEqualTo(3L);
            assertThat(nguoi.getRole()).isEqualTo("TRUONG_PHONG");
        }

        @Test
        @DisplayName("departmentId = 0 → gỡ khỏi phòng; = id → chuyển phòng")
        void phongBan() {
            UpdateUserDTO d = new UpdateUserDTO();
            d.setDepartmentId(28L);
            service.updateUser(7L, d);
            assertThat(nguoi.getDepartment()).isEqualTo(kd28);

            d.setDepartmentId(0L);
            service.updateUser(7L, d);
            assertThat(nguoi.getDepartment()).isNull();
        }

        @Test
        @DisplayName("referrerId = 0 → gỡ người giới thiệu; tự giới thiệu chính mình → từ chối")
        void nguoiGioiThieu() {
            UpdateUserDTO d = new UpdateUserDTO();
            d.setReferrerId(0L);
            service.updateUser(7L, d);
            assertThat(nguoi.getReferrerId()).isNull();

            d.setReferrerId(7L);
            assertThatThrownBy(() -> service.updateUser(7L, d)).hasMessageContaining("chính mình");
        }

        @Test
        @DisplayName("Đổi SĐT sang số đã có người dùng → từ chối; giữ nguyên số của mình → không kiểm")
        void doiSdt() {
            when(userRepository.existsByPhoneNumber("0999999999")).thenReturn(true);
            UpdateUserDTO d = new UpdateUserDTO();
            d.setPhoneNumber("0999999999");
            assertThatThrownBy(() -> service.updateUser(7L, d)).hasMessageContaining("đã thuộc về nhân sự khác");

            d.setPhoneNumber("0912345678");
            service.updateUser(7L, d);
            verify(userRepository, never()).existsByPhoneNumber("0912345678");
        }

        @Test
        @DisplayName("Sửa xong → quên bản chụp quyền và báo app tải lại hồ sơ ngay")
        void baoAppTaiLai() {
            UpdateUserDTO d = new UpdateUserDTO();
            d.setDepartmentId(28L);
            service.updateUser(7L, d);

            verify(hoSoNong).quen(7L);
            verify(messagingTemplate).convertAndSend(eq("/topic/ho-so/7"), any(Object.class));
        }
    }

    @Nested
    @DisplayName("Khóa, đặt lại mật khẩu, xóa")
    class KhoaXoa {

        @Test
        @DisplayName("Khóa (xóa mềm) → INACTIVE, giữ dữ liệu, quyền bị thu ngay ở yêu cầu kế tiếp")
        void khoa() {
            service.deleteUser(7L);
            assertThat(nguoi.getStatus()).isEqualTo("INACTIVE");
            verify(hoSoNong).quen(7L);
        }

        @Test
        @DisplayName("Admin đặt lại mật khẩu → đăng xuất mọi thiết bị của người đó")
        void datLaiMatKhau() {
            service.resetPassword(7L, "matkhaumoi");
            assertThat(passwordEncoder.matches("matkhaumoi", nguoi.getPasswordHash())).isTrue();
            verify(phienDangNhapService).dangXuatMoiThietBi(7L);
        }

        @Test
        @DisplayName("Xóa vĩnh viễn tài khoản đang hoạt động → từ chối (phải khóa trước)")
        void khongXoaNguoiDangLam() {
            assertThatThrownBy(() -> service.purgeUser(7L)).hasMessageContaining("INACTIVE");
        }
    }
}
