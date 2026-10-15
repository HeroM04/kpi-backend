package com.trilong.kpibackend.modules.attendance.service;

import com.trilong.kpibackend.modules.attendance.entity.LeaveRequest;
import com.trilong.kpibackend.modules.attendance.repository.LeaveRequestRepository;
import com.trilong.kpibackend.modules.kpi.entity.KpiLedgerEntry;
import com.trilong.kpibackend.modules.kpi.repository.KpiLedgerEntryRepository;
import com.trilong.kpibackend.modules.kpi.service.KpiCalculationService;
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
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Chuẩn hóa thứ Bảy 10/10/2026: hoàn điểm đi muộn đã trừ, thu lại điểm đúng giờ
 * đã cộng, hoàn điểm vắng đã trừ — đúng số thực vào sổ, chạy lại không trùng.
 * Bảng công (trạng thái vắng) giữ nguyên.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HoanDiemThuBayServiceTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalDate THU_BAY = LocalDate.of(2026, 10, 10);

    @Mock KpiLedgerEntryRepository ledger;
    @Mock KpiCalculationService kpi;
    @Mock LeaveRequestRepository leaveRepo;
    @InjectMocks HoanDiemThuBayService service;

    private final List<KpiLedgerEntry> dong = new ArrayList<>();

    private KpiLedgerEntry them(long userId, int thuc, String lyDo, int ngay, int gio) {
        KpiLedgerEntry e = new KpiLedgerEntry();
        e.setUserId(userId);
        e.setCategory("attendance");
        e.setPoints(thuc);
        e.setEffectivePoints(thuc);
        e.setReason(lyDo);
        e.setOccurredAt(ZonedDateTime.of(2026, 10, ngay, gio, 0, 0, 0, VN));
        dong.add(e);
        return e;
    }

    @BeforeEach
    void chuanBi() {
        when(kpi.getWeekString(any())).thenReturn("2026-W41");
        when(ledger.findByCategoryAndWeekOrderByIdAsc("attendance", "2026-W41")).thenReturn(dong);
    }

    @Test
    @DisplayName("Hoàn đúng số đã thực trừ đi muộn ngày 10/10; ngày khác không đụng tới")
    void hoanDungNguoiDungSo() {
        them(7L, -5, "Đi muộn ca sáng — chấm công lúc 09:02 (hạn 08:45)", 10, 9);
        them(8L, -2, "Đi muộn ca sáng — chấm công lúc 09:10 (hạn 08:45)", 10, 9);   // nhóm còn 2đ nên chỉ trừ được 2
        them(9L, 0, "Đi muộn ca sáng — chấm công lúc 09:20 (hạn 08:45)", 10, 9);    // nhóm 0đ, không trừ được gì
        them(11L, -5, "Đi muộn ca sáng — chấm công lúc 09:05 (hạn 08:45)", 9, 9);   // thứ Sáu, không hoàn

        var kq = service.chuanHoa(THU_BAY);

        assertThat(kq).containsExactly(
                new HoanDiemThuBayService.KetQua(7L, 5, 0, 0),
                new HoanDiemThuBayService.KetQua(8L, 2, 0, 0));
        verify(kpi).updateKpiPoints(eq(7L), eq("attendance"), eq(5), any(), startsWith("Hoàn điểm đi muộn thứ Bảy 10/10/2026"));
        verify(kpi).updateKpiPoints(eq(8L), eq("attendance"), eq(2), any(), anyString());
        verify(kpi, never()).updateKpiPoints(eq(9L), anyString(), anyInt(), any(), anyString());
        verify(kpi, never()).updateKpiPoints(eq(11L), anyString(), anyInt(), any(), anyString());
    }

    @Test
    @DisplayName("Thu lại đúng số đã thực cộng đúng giờ ngày 10/10 (cả ca chiều, cả bản Admin duyệt ngoại tuyến); ngày khác không đụng tới")
    void thuLaiDungGio() {
        them(10L, 5, "Đi làm đúng giờ — chấm công lúc 08:30", 10, 8);
        them(12L, 3, "Đi làm đúng giờ — chấm công lúc 08:40", 10, 8);                // nhóm chạm trần, chỉ vào được 3
        them(13L, 0, "Đi làm đúng giờ — chấm công lúc 08:41", 10, 8);                // đã đủ trần, không vào gì
        them(14L, 5, "Vào ca chiều đúng giờ — chấm công lúc 13:30", 10, 13);
        them(15L, 5, "Admin duyệt chấm công ngoại tuyến — Đi làm đúng giờ — chấm công lúc 08:20", 10, 8);
        them(16L, 5, "Đi làm đúng giờ — chấm công lúc 08:30", 9, 8);                 // thứ Sáu, giữ nguyên
        them(17L, 5, "Tăng ca — chấm công ra lúc 20:15", 10, 20);                     // không phải điểm đúng giờ

        var kq = service.chuanHoa(THU_BAY);

        assertThat(kq).containsExactly(
                new HoanDiemThuBayService.KetQua(10L, 0, 5, 0),
                new HoanDiemThuBayService.KetQua(12L, 0, 3, 0),
                new HoanDiemThuBayService.KetQua(14L, 0, 5, 0),
                new HoanDiemThuBayService.KetQua(15L, 0, 5, 0));
        verify(kpi).updateKpiPoints(eq(10L), eq("attendance"), eq(-5), any(), startsWith("Thu lại điểm đúng giờ thứ Bảy 10/10/2026"));
        verify(kpi).updateKpiPoints(eq(12L), eq("attendance"), eq(-3), any(), anyString());
        verify(kpi).updateKpiPoints(eq(14L), eq("attendance"), eq(-5), any(), anyString());
        verify(kpi).updateKpiPoints(eq(15L), eq("attendance"), eq(-5), any(), anyString());
        verify(kpi, never()).updateKpiPoints(eq(13L), anyString(), anyInt(), any(), anyString());
        verify(kpi, never()).updateKpiPoints(eq(16L), anyString(), anyInt(), any(), anyString());
        verify(kpi, never()).updateKpiPoints(eq(17L), anyString(), anyInt(), any(), anyString());
    }

    @Test
    @DisplayName("Ghi đúng mốc giờ chấm công để vào đúng tuần, đúng tháng KPI")
    void ghiDungMoc() {
        KpiLedgerEntry e = them(10L, 5, "Đi làm đúng giờ — chấm công lúc 08:30", 10, 8);
        service.chuanHoa(THU_BAY);
        verify(kpi).updateKpiPoints(eq(10L), eq("attendance"), eq(-5), eq(e.getOccurredAt()), anyString());
    }

    @Test
    @DisplayName("Chạy lại lần hai (đã có dòng hoàn / dòng thu lại) → không trùng")
    void khongHoanTrung() {
        them(7L, -5, "Đi muộn ca sáng — chấm công lúc 09:02 (hạn 08:45)", 10, 9);
        them(7L, 5, "Hoàn điểm đi muộn thứ Bảy 10/10/2026 — thứ Bảy không tính điểm chuyên cần", 10, 9);
        them(10L, 5, "Đi làm đúng giờ — chấm công lúc 08:30", 10, 8);
        them(10L, -5, "Thu lại điểm đúng giờ thứ Bảy 10/10/2026 — thứ Bảy không tính điểm chuyên cần", 10, 8);

        assertThat(service.chuanHoa(THU_BAY)).isEmpty();
        verify(kpi, never()).updateKpiPoints(any(), any(), anyInt(), any(), any());
    }

    @Test
    @DisplayName("Đã hoàn đi muộn từ bản trước (commit 74c298b) → lần này chỉ thu lại đúng giờ")
    void daHoanTuBanTruoc() {
        them(7L, -5, "Đi muộn ca sáng — chấm công lúc 09:02 (hạn 08:45)", 10, 9);
        them(7L, 5, "Hoàn điểm đi muộn thứ Bảy 10/10/2026 — thứ Bảy không tính điểm chuyên cần", 10, 9);
        them(10L, 5, "Đi làm đúng giờ — chấm công lúc 08:30", 10, 8);

        assertThat(service.chuanHoa(THU_BAY)).containsExactly(new HoanDiemThuBayService.KetQua(10L, 0, 5, 0));
        verify(kpi, never()).updateKpiPoints(eq(7L), anyString(), anyInt(), any(), anyString());
    }

    @Test
    @DisplayName("Admin đã thu hồi duyệt (đã trả lại / đã gỡ 5đ) → không hoàn, không thu thêm")
    void daThuHoi() {
        them(7L, -5, "Admin duyệt chấm công ngoại tuyến — Đi muộn ca sáng — chấm công lúc 09:02 (hạn 08:45)", 10, 9);
        them(7L, 5, "Admin thu hồi duyệt chấm công lúc 09:02", 10, 9);
        them(10L, 5, "Admin duyệt chấm công ngoại tuyến — Đi làm đúng giờ — chấm công lúc 08:20", 10, 8);
        them(10L, -5, "Admin thu hồi duyệt chấm công lúc 08:20", 10, 8);

        assertThat(service.chuanHoa(THU_BAY)).isEmpty();
        verify(kpi, never()).updateKpiPoints(any(), any(), anyInt(), any(), any());
    }

    @Test
    @DisplayName("Hoàn đúng số đã thực trừ vắng không phép / nghỉ có phép ngày 10/10; ngày thường giữ nguyên")
    void hoanVang() {
        them(20L, -15, "Vắng không phép ngày 10/10 — không chấm công và không có đơn xin nghỉ", 10, 12);
        them(21L, -6, "Vắng không phép ngày 10/10 — không chấm công và không có đơn xin nghỉ", 10, 12);   // nhóm còn 6đ
        them(22L, -10, "Nghỉ có phép ngày 10/10 — Admin đã duyệt đơn", 10, 12);
        them(23L, -10, "Nghỉ có phép ngày 10/10 — Admin đã duyệt đơn", 10, 12);
        them(23L, 10, "Hoàn lại điểm đơn nghỉ ngày 10/10 — Admin đổi sang từ chối", 10, 12);
        them(23L, -15, "Vắng không phép ngày 10/10 — đơn xin vắng bị từ chối, hôm đó không chấm công", 10, 12);
        them(24L, -15, "Vắng không phép ngày 09/10 — không chấm công và không có đơn xin nghỉ", 9, 12);   // thứ Sáu

        var kq = service.chuanHoa(THU_BAY);

        assertThat(kq).containsExactly(
                new HoanDiemThuBayService.KetQua(20L, 0, 0, 15),
                new HoanDiemThuBayService.KetQua(21L, 0, 0, 6),
                new HoanDiemThuBayService.KetQua(22L, 0, 0, 10),
                new HoanDiemThuBayService.KetQua(23L, 0, 0, 15));
        verify(kpi).updateKpiPoints(eq(20L), eq("attendance"), eq(15), eq(ZonedDateTime.of(2026, 10, 10, 12, 0, 0, 0, VN)),
                startsWith("Hoàn điểm vắng thứ Bảy 10/10/2026"));
        verify(kpi).updateKpiPoints(eq(21L), eq("attendance"), eq(6), any(), anyString());
        verify(kpi).updateKpiPoints(eq(22L), eq("attendance"), eq(10), any(), anyString());
        verify(kpi).updateKpiPoints(eq(23L), eq("attendance"), eq(15), any(), anyString());
        verify(kpi, never()).updateKpiPoints(eq(24L), anyString(), anyInt(), any(), anyString());
    }

    @Test
    @DisplayName("Chạy lại sau khi đã hoàn vắng → không hoàn trùng")
    void khongHoanVangTrung() {
        them(20L, -15, "Vắng không phép ngày 10/10 — không chấm công và không có đơn xin nghỉ", 10, 12);
        them(20L, 15, "Hoàn điểm vắng thứ Bảy 10/10/2026 — thứ Bảy nghỉ không bị trừ điểm", 10, 12);

        assertThat(service.chuanHoa(THU_BAY)).isEmpty();
        verify(kpi, never()).updateKpiPoints(any(), any(), anyInt(), any(), any());
    }

    @Test
    @DisplayName("Bảng công giữ nguyên trạng thái vắng, chỉ bỏ cờ đã trừ điểm")
    void giuBangCong() {
        LeaveRequest mayGhi = don(1L, "UNEXCUSED", true, "Hệ thống tự ghi nhận: không chấm công và không có đơn xin vắng được duyệt", null);
        LeaveRequest tuChoi = don(2L, "UNEXCUSED", true, "về quê", 99L);
        LeaveRequest choDuyet = don(3L, "UNEXCUSED", true, "ốm", null);
        LeaveRequest coPhep = don(4L, "APPROVED", true, "đám cưới", 99L);
        when(leaveRepo.findByLeaveDate(THU_BAY)).thenReturn(List.of(mayGhi, tuChoi, choDuyet, coPhep));

        service.chuanHoa(THU_BAY);

        assertThat(List.of(mayGhi, tuChoi, choDuyet)).allMatch(r -> "UNEXCUSED".equals(r.getStatus()));
        assertThat(coPhep.getStatus()).isEqualTo("APPROVED");
        assertThat(List.of(mayGhi, tuChoi, choDuyet, coPhep)).allMatch(r -> !r.getKpiApplied());
        verify(leaveRepo, never()).delete(any(LeaveRequest.class));
    }

    private LeaveRequest don(long userId, String trangThai, boolean daTru, String lyDo, Long nguoiDuyet) {
        LeaveRequest r = new LeaveRequest();
        r.setUserId(userId);
        r.setLeaveDate(THU_BAY);
        r.setStatus(trangThai);
        r.setKpiApplied(daTru);
        r.setReason(lyDo);
        r.setReviewedBy(nguoiDuyet);
        return r;
    }

    @Test
    @DisplayName("Ngày không phải thứ Bảy → từ chối")
    void khongPhaiThuBay() {
        assertThatThrownBy(() -> service.chuanHoa(LocalDate.of(2026, 10, 9)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("không phải thứ Bảy");
    }
}
