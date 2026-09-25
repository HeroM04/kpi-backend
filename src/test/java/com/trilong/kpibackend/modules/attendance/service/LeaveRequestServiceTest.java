package com.trilong.kpibackend.modules.attendance.service;

import com.trilong.kpibackend.modules.attendance.entity.CheckinLog;
import com.trilong.kpibackend.modules.attendance.entity.LeaveRequest;
import com.trilong.kpibackend.modules.attendance.repository.CheckinLogRepository;
import com.trilong.kpibackend.modules.attendance.repository.LeaveRequestRepository;
import com.trilong.kpibackend.modules.kpi.service.KpiCalculationService;
import com.trilong.kpibackend.modules.user.entity.User;
import com.trilong.kpibackend.modules.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Đơn xin vắng và chốt vắng không phép cuối ngày.
 *
 * <p>Luật: nghỉ có đơn được duyệt −10đ, vắng không đơn −15đ, Chủ nhật không
 * tính. Mọi tình huống duyệt / thu hồi duyệt / gửi lại phải cho ra đúng một
 * trong ba kết quả đó, không trừ trùng, không bỏ sót.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LeaveRequestServiceTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    @Mock LeaveRequestRepository leaveRepo;
    @Mock CheckinLogRepository checkinRepo;
    @Mock UserRepository userRepository;
    @Mock KpiCalculationService kpi;
    @InjectMocks LeaveRequestService service;

    @BeforeEach
    void chuanBi() {
        when(leaveRepo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private LeaveRequest don(long id, LocalDate ngay, String trangThai, boolean daTru) {
        LeaveRequest r = new LeaveRequest();
        r.setId(id);
        r.setUserId(7L);
        r.setLeaveDate(ngay);
        r.setStatus(trangThai);
        r.setKpiApplied(daTru);
        when(leaveRepo.findById(id)).thenReturn(Optional.of(r));
        return r;
    }

    private LocalDate homQuaKhongPhaiCN() {
        LocalDate d = LocalDate.now(VN).minusDays(1);
        return d.getDayOfWeek() == java.time.DayOfWeek.SUNDAY ? d.minusDays(1) : d;
    }

    // ── Duyệt / từ chối ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Duyệt và từ chối đơn")
    class DuyetTuChoi {

        @Test
        @DisplayName("Duyệt đơn → −10đ một lần; duyệt lại không trừ thêm")
        void duyet() {
            LeaveRequest r = don(1, LocalDate.now(VN).plusDays(2), "PENDING", false);
            service.approve(1L, 99L, null);
            service.approve(1L, 99L, null);

            assertThat(r.getStatus()).isEqualTo("APPROVED");
            verify(kpi, times(1)).updateKpiPoints(eq(7L), eq("attendance"), eq(-10), any(), anyString());
        }

        @Test
        @DisplayName("Từ chối đơn của ngày CHƯA tới → chỉ đổi trạng thái, để tác vụ cuối ngày tự xét")
        void tuChoiNgayChuaToi() {
            LeaveRequest r = don(2, LocalDate.now(VN).plusDays(3), "PENDING", false);
            service.reject(2L, 99L, "thiếu lý do");

            assertThat(r.getStatus()).isEqualTo("REJECTED");
            verify(kpi, never()).updateKpiPoints(any(), any(), anyInt(), any(), any());
        }

        @Test
        @DisplayName("Thu hồi duyệt đơn của ngày ĐÃ QUA mà hôm đó không chấm công → hoàn 10đ rồi thành vắng không phép −15đ")
        void thuHoiDuyetNgayDaQua() {
            LocalDate homQua = homQuaKhongPhaiCN();
            LeaveRequest r = don(3, homQua, "APPROVED", true);
            when(checkinRepo.findByUserIdAndCheckinTimeBetween(eq(7L), any(), any())).thenReturn(List.of());

            service.reject(3L, 99L, "không đúng quy trình");

            assertThat(r.getStatus()).isEqualTo("UNEXCUSED");
            assertThat(r.getKpiApplied()).isTrue();
            verify(kpi).updateKpiPoints(eq(7L), eq("attendance"), eq(10), any(), startsWith("Hoàn lại điểm đơn nghỉ"));
            verify(kpi).updateKpiPoints(eq(7L), eq("attendance"), eq(-15), any(), startsWith("Vắng không phép"));
        }

        @Test
        @DisplayName("Thu hồi duyệt ngày đã qua nhưng hôm đó CÓ chấm công → chỉ hoàn 10đ, không phạt vắng")
        void thuHoiNhungCoDiLam() {
            LeaveRequest r = don(4, homQuaKhongPhaiCN(), "APPROVED", true);
            CheckinLog c = new CheckinLog();
            c.setStatus("APPROVED");
            when(checkinRepo.findByUserIdAndCheckinTimeBetween(eq(7L), any(), any())).thenReturn(List.of(c));

            service.reject(4L, 99L, null);

            assertThat(r.getStatus()).isEqualTo("REJECTED");
            verify(kpi, never()).updateKpiPoints(any(), any(), eq(-15), any(), any());
        }

        @Test
        @DisplayName("Không từ chối được bản ghi vắng không phép do máy ghi — tránh hoàn nhầm 10đ cho khoản trừ 15đ")
        void khongTuChoiVangKhongPhep() {
            don(5, homQuaKhongPhaiCN(), "UNEXCUSED", true);
            assertThatThrownBy(() -> service.reject(5L, 99L, null)).isInstanceOf(IllegalArgumentException.class);
            verify(kpi, never()).updateKpiPoints(any(), any(), anyInt(), any(), any());
        }

        @Test
        @DisplayName("Chỉ người gửi mới hủy được đơn, và chỉ khi còn chờ duyệt")
        void huyDon() {
            don(6, LocalDate.now(VN).plusDays(1), "PENDING", false);
            assertThatThrownBy(() -> service.cancel(8L, 6L)).hasMessageContaining("không có quyền");

            don(7, LocalDate.now(VN).plusDays(1), "APPROVED", true);
            assertThatThrownBy(() -> service.cancel(7L, 7L)).hasMessageContaining("chờ duyệt");
        }
    }

    // ── Chốt vắng cuối ngày ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Chốt vắng không phép cuối ngày")
    class ChotNgay {

        private final LocalDate thuTu = LocalDate.of(2026, 9, 23);

        private User nguoi(long id, String vaiTro, String trangThai) {
            User u = User.builder().id(id).fullName("NS " + id).role(vaiTro).status(trangThai).build();
            u.setCreatedAt(ZonedDateTime.of(2026, 7, 1, 8, 0, 0, 0, VN));
            return u;
        }

        @Test
        @DisplayName("Chủ nhật → không chốt ai")
        void chuNhat() {
            assertThat(service.closeDay(LocalDate.of(2026, 9, 27))).isZero();
            verifyNoInteractions(kpi);
        }

        @Test
        @DisplayName("Chỉ phạt người vắng thật: bỏ qua Admin, người nghỉ việc, người đã chấm công, người có đơn được duyệt, người chưa vào công ty")
        void chiPhatNguoiVangThat() {
            User vang = nguoi(1, "SALE", "ACTIVE");
            User vanPhongVang = nguoi(2, "VAN_PHONG", "ACTIVE");   // Back-Office vẫn bị ghi vắng để tính công
            User admin = nguoi(3, "ADMIN", "ACTIVE");
            User nghiViec = nguoi(4, "SALE", "INACTIVE");
            User diLam = nguoi(5, "SALE", "ACTIVE");
            User coDon = nguoi(6, "SALE", "ACTIVE");
            User moiVao = nguoi(7, "SALE", "ACTIVE");
            moiVao.setCreatedAt(ZonedDateTime.of(2026, 9, 24, 8, 0, 0, 0, VN));
            User chamCongBiTuChoi = nguoi(8, "SALE", "ACTIVE");

            when(userRepository.findAll()).thenReturn(List.of(vang, vanPhongVang, admin, nghiViec, diLam, coDon, moiVao, chamCongBiTuChoi));

            CheckinLog hopLe = new CheckinLog(); hopLe.setUserId(5L); hopLe.setStatus("APPROVED");
            CheckinLog biTuChoi = new CheckinLog(); biTuChoi.setUserId(8L); biTuChoi.setStatus("REJECTED");
            when(checkinRepo.findByCheckinTimeBetween(any(), any())).thenReturn(List.of(hopLe, biTuChoi));

            LeaveRequest donDuyet = new LeaveRequest(); donDuyet.setUserId(6L); donDuyet.setStatus("APPROVED");
            when(leaveRepo.findByLeaveDate(thuTu)).thenReturn(List.of(donDuyet));
            when(leaveRepo.findByUserIdAndLeaveDate(anyLong(), any())).thenReturn(Optional.empty());

            int soNguoi = service.closeDay(thuTu);

            assertThat(soNguoi).isEqualTo(3); // người 1, 2, 8
            ArgumentCaptor<LeaveRequest> luu = ArgumentCaptor.forClass(LeaveRequest.class);
            verify(leaveRepo, times(3)).save(luu.capture());
            assertThat(luu.getAllValues()).extracting(LeaveRequest::getUserId).containsExactlyInAnyOrder(1L, 2L, 8L);
            assertThat(luu.getAllValues()).allMatch(r -> "UNEXCUSED".equals(r.getStatus()) && r.getKpiApplied());
            verify(kpi, times(3)).updateKpiPoints(anyLong(), eq("attendance"), eq(-15), any(), startsWith("Vắng không phép ngày 23/09"));
        }

        @Test
        @DisplayName("Chạy lại cùng ngày không phạt trùng")
        void chayLaiKhongTrung() {
            when(userRepository.findAll()).thenReturn(List.of(nguoi(1, "SALE", "ACTIVE")));
            when(checkinRepo.findByCheckinTimeBetween(any(), any())).thenReturn(List.of());
            LeaveRequest daChot = new LeaveRequest(); daChot.setUserId(1L); daChot.setStatus("UNEXCUSED");
            when(leaveRepo.findByLeaveDate(thuTu)).thenReturn(List.of(daChot));

            assertThat(service.closeDay(thuTu)).isZero();
            verifyNoInteractions(kpi);
        }
    }
}
