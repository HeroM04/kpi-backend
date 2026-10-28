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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Admin thêm chấm công tay: tạo đúng lượt, duyệt qua đường chuẩn, gỡ vắng không phép. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChamCongTayServiceTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalDate THU_HAI = LocalDate.of(2026, 10, 26);

    @Mock CheckinLogRepository checkinRepo;
    @Mock LeaveRequestRepository leaveRepo;
    @Mock UserRepository userRepository;
    @Mock KpiCalculationService kpi;
    @Mock CheckinService checkinService;
    @InjectMocks ChamCongTayService service;

    private final List<CheckinLog> daLuu = new ArrayList<>();
    private final AtomicLong id = new AtomicLong(100);

    @BeforeEach
    void chuanBi() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(User.builder().id(7L).fullName("Sale A").build()));
        when(checkinRepo.findByUserIdAndCheckinTimeBetween(eq(7L), any(), any())).thenReturn(List.of());
        when(checkinRepo.save(any())).thenAnswer(i -> {
            CheckinLog c = i.getArgument(0);
            c.setId(id.incrementAndGet());
            daLuu.add(c);
            return c;
        });
        when(checkinService.updateStatus(anyLong(), eq("APPROVED"), isNull())).thenAnswer(i -> {
            Long ma = i.getArgument(0);
            CheckinLog c = daLuu.stream().filter(x -> x.getId().equals(ma)).findFirst().orElseThrow();
            c.setStatus("APPROVED");
            return c;
        });
        when(leaveRepo.findByUserIdAndLeaveDate(anyLong(), any())).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("Giờ vào + giờ ra → hai lượt MANUAL đúng ngày giờ VN, đều duyệt qua đường Admin duyệt")
    void themVaoVaRa() {
        var kq = service.themTay(7L, THU_HAI, LocalTime.of(8, 30), LocalTime.of(17, 45), "Quên chấm công");

        assertThat(kq).hasSize(2);
        assertThat(daLuu).extracting(CheckinLog::getActionType).containsExactly("CHECK_IN", "CHECK_OUT");
        assertThat(daLuu).allMatch(c -> ChamCongTayService.LOAI.equals(c.getCheckinType()) && c.getUserId() == 7L
                && c.getNote().equals("Admin thêm tay — Quên chấm công"));
        assertThat(daLuu.get(0).getCheckinTime()).isEqualTo(ZonedDateTime.of(2026, 10, 26, 8, 30, 0, 0, VN));
        assertThat(daLuu.get(1).getCheckinTime()).isEqualTo(ZonedDateTime.of(2026, 10, 26, 17, 45, 0, 0, VN));
        verify(checkinService, times(2)).updateStatus(anyLong(), eq("APPROVED"), isNull());
        assertThat(kq).allMatch(c -> "APPROVED".equals(c.getStatus()));
    }

    @Test
    @DisplayName("Chỉ giờ ra → một lượt CHECK_OUT")
    void chiGioRa() {
        service.themTay(7L, THU_HAI, null, LocalTime.of(18, 0), "Điện thoại hết pin lúc vào");
        assertThat(daLuu).extracting(CheckinLog::getActionType).containsExactly("CHECK_OUT");
    }

    @Test
    @DisplayName("Dữ liệu sai → báo lỗi rõ, không lưu gì")
    void duLieuSai() {
        assertThatThrownBy(() -> service.themTay(7L, THU_HAI, null, null, "x")).hasMessageContaining("ít nhất");
        assertThatThrownBy(() -> service.themTay(7L, THU_HAI, LocalTime.of(17, 0), LocalTime.of(8, 0), "x"))
                .hasMessageContaining("Giờ ra phải sau giờ vào");
        assertThatThrownBy(() -> service.themTay(7L, THU_HAI, LocalTime.of(8, 0), null, "  ")).hasMessageContaining("lý do");
        assertThatThrownBy(() -> service.themTay(7L, LocalDate.now(VN).plusDays(1), LocalTime.of(8, 0), null, "x"))
                .hasMessageContaining("chưa tới");
        assertThatThrownBy(() -> service.themTay(99L, THU_HAI, LocalTime.of(8, 0), null, "x"))
                .hasMessageContaining("Không tìm thấy nhân sự");
        verify(checkinRepo, never()).save(any());
    }

    @Test
    @DisplayName("Đã có lượt vào cùng phút (chưa bị từ chối) → báo trùng, không thêm")
    void trungPhut() {
        CheckinLog co = new CheckinLog();
        co.setActionType("CHECK_IN");
        co.setStatus("APPROVED");
        co.setCheckinTime(ZonedDateTime.of(2026, 10, 26, 8, 30, 27, 0, VN));
        when(checkinRepo.findByUserIdAndCheckinTimeBetween(eq(7L), any(), any())).thenReturn(List.of(co));

        assertThatThrownBy(() -> service.themTay(7L, THU_HAI, LocalTime.of(8, 30), null, "x"))
                .hasMessageContaining("đã có rồi");
        verify(checkinRepo, never()).save(any());
    }

    @Test
    @DisplayName("Ngày đã bị máy chốt vắng không phép → hoàn đúng số đã thực trừ, xóa bản ghi vắng")
    void goVangMayGhi() {
        LeaveRequest vang = new LeaveRequest();
        vang.setUserId(7L);
        vang.setLeaveDate(THU_HAI);
        vang.setStatus("UNEXCUSED");
        vang.setKpiApplied(true);
        vang.setReason("Hệ thống tự ghi nhận: không chấm công và không có đơn xin vắng được duyệt");
        when(leaveRepo.findByUserIdAndLeaveDate(7L, THU_HAI)).thenReturn(Optional.of(vang));
        ZonedDateTime trua = ZonedDateTime.of(2026, 10, 26, 12, 0, 0, 0, VN);
        when(kpi.diemThucDaTru(7L, "attendance", "Vắng không phép ngày 26/10", trua, 15)).thenReturn(6);

        service.themTay(7L, THU_HAI, LocalTime.of(8, 40), null, "Quên chấm công");

        verify(kpi).updateKpiPoints(eq(7L), eq("attendance"), eq(6), eq(trua), startsWith("Hoàn điểm vắng không phép ngày 26/10"));
        verify(leaveRepo).delete(vang);
    }

    @Test
    @DisplayName("Vắng không phép vốn là đơn bị từ chối → trả về REJECTED, không xóa đơn của người ta")
    void goVangTuDon() {
        LeaveRequest vang = new LeaveRequest();
        vang.setUserId(7L);
        vang.setLeaveDate(THU_HAI);
        vang.setStatus("UNEXCUSED");
        vang.setKpiApplied(true);
        vang.setReason("về quê");
        when(leaveRepo.findByUserIdAndLeaveDate(7L, THU_HAI)).thenReturn(Optional.of(vang));
        when(kpi.diemThucDaTru(anyLong(), anyString(), anyString(), any(), anyInt())).thenReturn(15);
        when(leaveRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        service.themTay(7L, THU_HAI, LocalTime.of(8, 40), null, "Có đi làm, chưa chấm");

        ArgumentCaptor<LeaveRequest> luu = ArgumentCaptor.forClass(LeaveRequest.class);
        verify(leaveRepo).save(luu.capture());
        assertThat(luu.getValue().getStatus()).isEqualTo("REJECTED");
        assertThat(luu.getValue().getKpiApplied()).isFalse();
        verify(leaveRepo, never()).delete(any(LeaveRequest.class));
        verify(kpi).updateKpiPoints(eq(7L), eq("attendance"), eq(15), any(), anyString());
    }
}
