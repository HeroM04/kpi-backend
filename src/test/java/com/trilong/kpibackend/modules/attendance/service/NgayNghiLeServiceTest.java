package com.trilong.kpibackend.modules.attendance.service;

import com.trilong.kpibackend.modules.attendance.entity.LeaveRequest;
import com.trilong.kpibackend.modules.attendance.entity.NgayNghiLe;
import com.trilong.kpibackend.modules.attendance.repository.LeaveRequestRepository;
import com.trilong.kpibackend.modules.attendance.repository.NgayNghiLeRepository;
import com.trilong.kpibackend.modules.kpi.service.KpiCalculationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 24/11/2026 nghỉ toàn quốc mà máy chốt 144 người vắng không phép. Thêm ngày đó
 * làm ngày nghỉ lễ phải hoàn ĐÚNG số đã trừ và bỏ chữ "vắng không phép".
 */
class NgayNghiLeServiceTest {

    private static final LocalDate NGAY = LocalDate.of(2026, 11, 24);

    private NgayNghiLeRepository leRepo;
    private LeaveRequestRepository donRepo;
    private KpiCalculationService kpi;
    private NgayNghiLeService service;

    @BeforeEach
    void setUp() {
        leRepo = mock(NgayNghiLeRepository.class);
        donRepo = mock(LeaveRequestRepository.class);
        kpi = mock(KpiCalculationService.class);
        service = new NgayNghiLeService(leRepo, donRepo, kpi);
        when(leRepo.findByNgay(any())).thenReturn(Optional.empty());
        when(leRepo.save(any(NgayNghiLe.class))).thenAnswer(i -> i.getArgument(0));
    }

    private LeaveRequest don(long userId, String trangThai, boolean daTru, String lyDo) {
        LeaveRequest r = new LeaveRequest();
        r.setUserId(userId);
        r.setLeaveDate(NGAY);
        r.setStatus(trangThai);
        r.setKpiApplied(daTru);
        r.setReason(lyDo);
        return r;
    }

    private static final String MAY_GHI = LeaveRequestService.LY_DO_MAY_GHI + ": không chấm công và không có đơn xin vắng được duyệt";

    @Test
    @DisplayName("Vắng không phép do máy ghi → hoàn đúng số đã thực trừ (kể cả 0đ vẫn ghi dòng), xóa bản ghi")
    void hoanVaXoaVangMayGhi() {
        LeaveRequest a = don(1, "UNEXCUSED", true, MAY_GHI);   // trừ thực 15
        LeaveRequest b = don(2, "UNEXCUSED", true, MAY_GHI);   // nhóm đang 0đ → trừ thực 0
        when(donRepo.findByLeaveDate(NGAY)).thenReturn(List.of(a, b));
        when(kpi.diemThucDaTru(eq(1L), eq("attendance"), startsWith("Vắng không phép ngày 24/11"), any(), anyInt())).thenReturn(15);
        when(kpi.diemThucDaTru(eq(2L), eq("attendance"), startsWith("Vắng không phép ngày 24/11"), any(), anyInt())).thenReturn(0);

        var kq = service.them(NGAY, "Ngày nghỉ toàn quốc");

        assertEquals(2, kq.soNguoiHoan());
        assertEquals(15, kq.tongDiemHoan());
        verify(kpi).updateKpiPoints(eq(1L), eq("attendance"), eq(15), argThat(m -> m.withZoneSameInstant(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate().equals(NGAY)),
                contains("Hoàn điểm vắng ngày 24/11"));
        verify(kpi).updateKpiPoints(eq(2L), eq("attendance"), eq(0), any(), contains("Ngày nghỉ toàn quốc"));
        verify(donRepo).delete(a);
        verify(donRepo).delete(b);
    }

    @Test
    @DisplayName("Vắng không phép vì đơn bị từ chối → hoàn điểm, đơn về Từ chối (không xóa đơn của nhân sự)")
    void donBiTuChoi() {
        LeaveRequest r = don(3, "UNEXCUSED", true, "Việc gia đình");
        when(donRepo.findByLeaveDate(NGAY)).thenReturn(List.of(r));
        when(kpi.diemThucDaTru(anyLong(), anyString(), anyString(), any(), anyInt())).thenReturn(15);

        service.them(NGAY, "Ngày nghỉ toàn quốc");

        assertEquals("REJECTED", r.getStatus());
        assertFalse(r.getKpiApplied());
        verify(donRepo, never()).delete(any(LeaveRequest.class));
        verify(donRepo).save(r);
    }

    @Test
    @DisplayName("Nghỉ có phép đã bị trừ −10 (xin trong ngày) → hoàn số đã trừ, đơn vẫn có phép")
    void nghiCoPhepDaTru() {
        LeaveRequest r = don(4, "APPROVED", true, "Ốm");
        LeaveRequest xinTruoc = don(5, "APPROVED", false, "Về quê");   // xin trước, không bị trừ
        when(donRepo.findByLeaveDate(NGAY)).thenReturn(List.of(r, xinTruoc));
        when(kpi.diemThucDaTru(eq(4L), eq("attendance"), startsWith("Nghỉ có phép ngày 24/11"), any(), anyInt())).thenReturn(4);

        var kq = service.them(NGAY, "Ngày nghỉ toàn quốc");

        assertEquals(1, kq.soNguoiHoan());
        assertEquals(4, kq.tongDiemHoan());
        assertEquals("APPROVED", r.getStatus());
        assertFalse(r.getKpiApplied());
        verify(kpi, never()).updateKpiPoints(eq(5L), anyString(), anyInt(), any(), anyString());
    }

    @Test
    @DisplayName("Ngày lễ sắp tới → chỉ lưu ngày, không đụng điểm ai")
    void ngayLeSapToi() {
        LocalDate tet = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(30);
        var kq = service.them(tet, "Tết Dương lịch");
        assertEquals(0, kq.soNguoiHoan());
        verify(donRepo, never()).findByLeaveDate(any());
        verify(leRepo).save(argThat(le -> le.getNgay().equals(tet) && le.getTen().equals("Tết Dương lịch")));
    }

    @Test
    @DisplayName("Chạy lại (đã hoàn hết) → không hoàn trùng, không thêm trùng ngày lễ")
    void chayLai() {
        NgayNghiLe daCo = new NgayNghiLe(NGAY, "Ngày nghỉ toàn quốc");
        when(leRepo.findByNgay(NGAY)).thenReturn(Optional.of(daCo));
        when(donRepo.findByLeaveDate(NGAY)).thenReturn(List.of());

        var kq = service.them(NGAY, null);

        assertEquals(0, kq.soNguoiHoan());
        verify(leRepo, never()).save(any());
        verify(kpi, never()).updateKpiPoints(anyLong(), anyString(), anyInt(), any(), anyString());
    }

    @Test
    @DisplayName("Không chọn ngày → báo lỗi")
    void thieuNgay() {
        assertThrows(IllegalArgumentException.class, () -> service.them(null, "x"));
    }
}
