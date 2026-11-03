package com.trilong.kpibackend.modules.user.service;

import com.trilong.kpibackend.modules.kpi.repository.KpiAutoGrantRepository;
import com.trilong.kpibackend.modules.user.dto.ApproveReferralDTO;
import com.trilong.kpibackend.modules.user.dto.ReferralSubmissionDTO;
import com.trilong.kpibackend.modules.user.dto.UserDTO;
import com.trilong.kpibackend.modules.user.entity.ReferralSubmission;
import com.trilong.kpibackend.modules.user.entity.User;
import com.trilong.kpibackend.modules.user.repository.ReferralSubmissionRepository;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Gieo hạt: số điện thoại đã có tài khoản (nhân sự mở trước) vẫn gửi đơn được,
 * duyệt thì gắn người giới thiệu vào tài khoản đó thay vì tạo mới.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReferralSubmissionServiceTest {

    private static final String SDT_MOI = "0968175189";

    @Mock ReferralSubmissionRepository referralRepo;
    @Mock UserRepository userRepository;
    @Mock UserService userService;
    @Mock KpiAutoGrantRepository kpiAutoGrantRepository;
    @InjectMocks ReferralSubmissionService service;

    private final User nguoiGioiThieu = User.builder().id(7L).fullName("Sale A").phoneNumber("0900000007").status("ACTIVE").build();

    @BeforeEach
    void chuanBi() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(nguoiGioiThieu));
        when(referralRepo.findByCandidatePhoneAndStatus(any(), any())).thenReturn(Optional.empty());
        when(referralRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(userService.convertToDTO(any())).thenAnswer(i -> {
            User u = i.getArgument(0);
            UserDTO d = new UserDTO();
            d.setId(u.getId());
            d.setFullName(u.getFullName());
            return d;
        });
    }

    private ReferralSubmissionDTO don() {
        ReferralSubmissionDTO d = new ReferralSubmissionDTO();
        d.setCandidateName("Nguyễn Trung Cương");
        d.setCandidatePhone(SDT_MOI);
        d.setNote("đồng nghiệp cũ");
        return d;
    }

    private User taiKhoanCoSan(Long referrerId) {
        return User.builder().id(50L).fullName("Nguyễn Trung Cương").phoneNumber(SDT_MOI).status("ACTIVE")
                .joinedDate(LocalDate.of(2026, 10, 28)).referrerId(referrerId).build();
    }

    private ReferralSubmission donChoDuyet() {
        ReferralSubmission r = new ReferralSubmission();
        r.setId(1L);
        r.setReferrerId(7L);
        r.setCandidateName("Nguyễn Trung Cương");
        r.setCandidatePhone(SDT_MOI);
        r.setStatus("PENDING");
        return r;
    }

    @Test
    @DisplayName("Số đã có tài khoản, chưa ai giới thiệu → vẫn nhận đơn")
    void nhanDonKhiDaCoTaiKhoan() {
        when(userRepository.findByPhoneNumber(SDT_MOI)).thenReturn(Optional.of(taiKhoanCoSan(null)));

        ReferralSubmission r = service.submit(7L, don());

        assertThat(r.getStatus()).isEqualTo("PENDING");
        assertThat(r.getCandidatePhone()).isEqualTo(SDT_MOI);
        verify(referralRepo).save(any());
    }

    @Test
    @DisplayName("Tài khoản đã ghi nhận chính mình là người giới thiệu → báo không cần gửi nữa")
    void daGhiNhanChinhMinh() {
        when(userRepository.findByPhoneNumber(SDT_MOI)).thenReturn(Optional.of(taiKhoanCoSan(7L)));

        assertThatThrownBy(() -> service.submit(7L, don()))
                .hasMessageContaining("Bạn đã được ghi nhận là người giới thiệu");
        verify(referralRepo, never()).save(any());
    }

    @Test
    @DisplayName("Tài khoản đã có người khác giới thiệu → chặn")
    void daCoNguoiKhacGioiThieu() {
        when(userRepository.findByPhoneNumber(SDT_MOI)).thenReturn(Optional.of(taiKhoanCoSan(99L)));

        assertThatThrownBy(() -> service.submit(7L, don()))
                .hasMessageContaining("đã được ghi nhận người giới thiệu khác");
        verify(referralRepo, never()).save(any());
    }

    @Test
    @DisplayName("Tự giới thiệu chính mình vẫn bị chặn")
    void tuGioiThieu() {
        ReferralSubmissionDTO d = don();
        d.setCandidatePhone(nguoiGioiThieu.getPhoneNumber());

        assertThatThrownBy(() -> service.submit(7L, d)).hasMessageContaining("chính mình");
    }

    @Test
    @DisplayName("Duyệt đơn có tài khoản sẵn → gắn người giới thiệu, giữ ngày vào làm, không tạo tài khoản mới")
    void duyetGanVaoTaiKhoanSanCo() {
        User acc = taiKhoanCoSan(null);
        ReferralSubmission r = donChoDuyet();
        when(referralRepo.findById(1L)).thenReturn(Optional.of(r));
        when(userRepository.findByPhoneNumber(SDT_MOI)).thenReturn(Optional.of(acc));

        ApproveReferralDTO dto = new ApproveReferralDTO(); // không phòng ban, không mật khẩu
        dto.setNote("Nhân sự đã mở tài khoản trước");
        UserDTO kq = service.approve(1L, 1L, dto);

        assertThat(kq.getId()).isEqualTo(50L);
        assertThat(acc.getReferrerId()).isEqualTo(7L);
        assertThat(acc.getJoinedDate()).isEqualTo(LocalDate.of(2026, 10, 28));
        assertThat(r.getStatus()).isEqualTo("APPROVED");
        assertThat(r.getCreatedUserId()).isEqualTo(50L);
        assertThat(r.getJoinedDate()).isEqualTo(LocalDate.of(2026, 10, 28));
        verify(userRepository).save(acc);
        verify(userService, never()).createUser(any());
    }

    @Test
    @DisplayName("Duyệt mà tài khoản đã có người giới thiệu khác → chặn, không đổi gì")
    void duyetKhiDaCoNguoiKhac() {
        User acc = taiKhoanCoSan(99L);
        when(referralRepo.findById(1L)).thenReturn(Optional.of(donChoDuyet()));
        when(userRepository.findByPhoneNumber(SDT_MOI)).thenReturn(Optional.of(acc));
        when(userRepository.findById(99L)).thenReturn(Optional.of(User.builder().id(99L).fullName("Sale B").build()));

        assertThatThrownBy(() -> service.approve(1L, 1L, new ApproveReferralDTO()))
                .hasMessageContaining("Sale B");
        assertThat(acc.getReferrerId()).isEqualTo(99L);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Chưa có tài khoản → vẫn tạo mới như cũ, bắt buộc phòng ban")
    void duyetTaoMoiNhuCu() {
        when(referralRepo.findById(1L)).thenReturn(Optional.of(donChoDuyet()));
        when(userRepository.findByPhoneNumber(SDT_MOI)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approve(1L, 1L, new ApproveReferralDTO()))
                .hasMessageContaining("phòng ban");

        UserDTO taoRa = new UserDTO();
        taoRa.setId(60L);
        taoRa.setFullName("Nguyễn Trung Cương");
        when(userService.createUser(any())).thenReturn(taoRa);
        ApproveReferralDTO dto = new ApproveReferralDTO();
        dto.setDepartmentId(3L);
        dto.setPassword("matkhau1");

        assertThat(service.approve(1L, 1L, dto).getId()).isEqualTo(60L);
        verify(userService).createUser(any());
    }

    @Test
    @DisplayName("Danh sách cho Admin: đơn chờ có tài khoản sẵn thì kèm tên + ngày vào làm của tài khoản đó")
    void dtoKemTaiKhoanSanCo() {
        when(userRepository.findByPhoneNumber(SDT_MOI)).thenReturn(Optional.of(taiKhoanCoSan(null)));
        when(kpiAutoGrantRepository.findByUserIdOrderByGrantedAtDesc(any())).thenReturn(List.of());

        var d = service.toDTO(donChoDuyet());

        assertThat(d.getExistingAccountId()).isEqualTo(50L);
        assertThat(d.getExistingAccountJoinedDate()).isEqualTo(LocalDate.of(2026, 10, 28));
    }
}
