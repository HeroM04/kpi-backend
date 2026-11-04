package com.trilong.kpibackend.modules.attendance.service;

import com.trilong.kpibackend.modules.attendance.entity.CheckinLog;
import com.trilong.kpibackend.modules.attendance.repository.CheckinLogRepository;
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
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Tăng ca mỗi ngày một lần: nhận ra ngày tính thừa, thu lại đúng số, không thu trùng. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TangCaServiceTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalDate THU_HAI = LocalDate.of(2026, 10, 12);
    private static final String TUAN = "2026-W42";
    private static final Long NGUOI = 7L;

    @Mock CheckinLogRepository checkinRepo;
    @Mock KpiLedgerEntryRepository ledgerRepo;
    @Mock KpiCalculationService kpi;
    @InjectMocks TangCaService service;

    private final List<KpiLedgerEntry> soCai = new ArrayList<>();
    private final List<CheckinLog> luot = new ArrayList<>();
    private final AtomicLong id = new AtomicLong();
    /** Số điểm thực vào của khoản thu lại kế tiếp — mặc định đúng như yêu cầu. */
    private Integer thucTruKeTiep = null;

    private static ZonedDateTime luc(int gio, int phut) {
        return THU_HAI.atTime(gio, phut).atZone(VN);
    }

    private void cong(int thuc, ZonedDateTime moc) {
        ghiSo(5, thuc, "Tăng ca — về lúc " + String.format("%02d:%02d", moc.getHour(), moc.getMinute()), moc);
    }

    private void ghiSo(int diem, int thuc, String lyDo, ZonedDateTime moc) {
        KpiLedgerEntry e = new KpiLedgerEntry();
        e.setId(id.incrementAndGet());
        e.setUserId(NGUOI);
        e.setCategory("meeting");
        e.setPoints(diem);
        e.setEffectivePoints(thuc);
        e.setReason(lyDo);
        e.setWeek(TUAN);
        e.setOccurredAt(moc);
        soCai.add(e);
    }

    private void ve(String trangThai, ZonedDateTime moc) {
        CheckinLog c = new CheckinLog();
        c.setId(id.incrementAndGet());
        c.setUserId(NGUOI);
        c.setActionType("CHECK_OUT");
        c.setStatus(trangThai);
        c.setCheckinTime(moc);
        luot.add(c);
    }

    @BeforeEach
    void chuanBi() {
        when(kpi.getWeekString(any())).thenReturn(TUAN);
        when(ledgerRepo.findByCategoryAndWeekOrderByIdAsc("meeting", TUAN)).thenAnswer(i -> List.copyOf(soCai));
        when(ledgerRepo.findByUserIdAndCategoryAndWeekOrderByIdAsc(eq(NGUOI), eq("meeting"), eq(TUAN)))
                .thenAnswer(i -> List.copyOf(soCai));
        when(checkinRepo.findByUserIdAndCheckinTimeBetween(eq(NGUOI), any(), any())).thenAnswer(i -> List.copyOf(luot));
        // Ghi khoản thu lại vào sổ như thật, để lần chạy sau thấy phần đã thu
        when(kpi.updateKpiPoints(anyLong(), anyString(), anyInt(), any(), anyString())).thenAnswer(i -> {
            int diem = i.getArgument(2);
            ghiSo(diem, thucTruKeTiep != null ? thucTruKeTiep : diem, i.getArgument(4), i.getArgument(3));
            return null;
        });
    }

    @Test
    @DisplayName("Còn lượt về: chỉ lượt ĐÃ DUYỆT, từ 20:00, khác lượt đang xét mới tính")
    void conLuotVe() {
        ve("PENDING", luc(20, 30));
        ve("APPROVED", luc(19, 50));
        assertThat(service.conLuotVeTangCa(NGUOI, THU_HAI, null)).isFalse();

        ve("APPROVED", luc(20, 20));
        Long luotDuyet = luot.get(luot.size() - 1).getId();
        assertThat(service.conLuotVeTangCa(NGUOI, THU_HAI, null)).isTrue();
        assertThat(service.conLuotVeTangCa(NGUOI, THU_HAI, luotDuyet)).isFalse();
    }

    @Test
    @DisplayName("Về 2 lần lúc 20:20 bị cộng 2 lần: xem trước không ghi gì, thu lại 5đ, chạy lại không thu nữa")
    void haiLanCungNgay() {
        ve("APPROVED", luc(20, 20));
        ve("APPROVED", luc(20, 20));
        cong(5, luc(20, 20));
        cong(5, luc(20, 20));

        var xem = service.chuanHoa(THU_HAI, THU_HAI, false);
        assertThat(xem).singleElement().satisfies(t -> {
            assertThat(t.dangCo()).isEqualTo(10);
            assertThat(t.dungRa()).isEqualTo(5);
            assertThat(t.thua()).isEqualTo(5);
            assertThat(t.lyDo()).isEqualTo(TangCaService.VI_TRUNG);
        });
        verify(kpi, never()).updateKpiPoints(anyLong(), anyString(), anyInt(), any(), anyString());

        service.chuanHoa(THU_HAI, THU_HAI, true);
        verify(kpi).updateKpiPoints(NGUOI, "meeting", -5, luc(20, 20),
                "Thu lại điểm tăng ca ngày 12/10/2026 — mỗi ngày chỉ tính tăng ca một lần");

        assertThat(service.chuanHoa(THU_HAI, THU_HAI, true)).isEmpty();
        verify(kpi, times(1)).updateKpiPoints(anyLong(), anyString(), anyInt(), any(), anyString());
    }

    @Test
    @DisplayName("Lần cộng thứ hai chỉ vào 3đ vì nhóm chạm trần → chỉ thu 3đ")
    void lanThuHaiChamTran() {
        ve("APPROVED", luc(20, 5));
        ve("APPROVED", luc(21, 10));
        cong(5, luc(20, 5));
        cong(3, luc(21, 10));

        assertThat(service.chuanHoa(THU_HAI, THU_HAI, true)).singleElement()
                .satisfies(t -> assertThat(t.thua()).isEqualTo(3));
        verify(kpi).updateKpiPoints(eq(NGUOI), eq("meeting"), eq(-3), any(), anyString());
    }

    @Test
    @DisplayName("Một lần tăng ca, lượt về còn đó → không có gì để sửa")
    void binhThuong() {
        ve("APPROVED", luc(20, 26));
        cong(5, luc(20, 26));

        assertThat(service.chuanHoa(THU_HAI, THU_HAI, true)).isEmpty();
        verify(kpi, never()).updateKpiPoints(anyLong(), anyString(), anyInt(), any(), anyString());
    }

    @Test
    @DisplayName("Lượt về đã bị thu hồi mà điểm tăng ca còn (lỗi cũ) → thu lại hết")
    void luotVeDaThuHoi() {
        ve("REJECTED", luc(20, 40));
        cong(5, luc(20, 40));

        assertThat(service.chuanHoa(THU_HAI, THU_HAI, true)).singleElement().satisfies(t -> {
            assertThat(t.dungRa()).isZero();
            assertThat(t.lyDo()).isEqualTo(TangCaService.VI_THU_HOI);
        });
        verify(kpi).updateKpiPoints(NGUOI, "meeting", -5, luc(20, 40),
                "Thu lại điểm tăng ca ngày 12/10/2026 — lượt về đã bị thu hồi");
    }

    @Test
    @DisplayName("Admin thu hồi lượt về: còn lượt khác thì giữ, hết lượt thì thu lại")
    void thuHoiLuotVe() {
        ve("APPROVED", luc(20, 20));
        cong(5, luc(20, 10));   // điểm do lượt 20:10 (vừa bị thu hồi) mang lại

        assertThat(service.thuLaiNeuHetLuot(NGUOI, THU_HAI)).isZero();
        verify(kpi, never()).updateKpiPoints(anyLong(), anyString(), anyInt(), any(), anyString());

        luot.get(0).setStatus("REJECTED");
        assertThat(service.thuLaiNeuHetLuot(NGUOI, THU_HAI)).isEqualTo(5);
        verify(kpi).updateKpiPoints(NGUOI, "meeting", -5, luc(20, 10),
                "Thu lại điểm tăng ca ngày 12/10/2026 — lượt về đã bị thu hồi");
    }

    @Test
    @DisplayName("Khoản thu chỉ trừ thực được 2đ vì điểm tuần chạm 0 → lần sau không thu thêm")
    void thuChamSan() {
        ve("REJECTED", luc(20, 40));
        cong(5, luc(20, 40));
        thucTruKeTiep = -2;

        assertThat(service.thuLaiNeuHetLuot(NGUOI, THU_HAI)).isEqualTo(5);
        assertThat(service.thuLaiNeuHetLuot(NGUOI, THU_HAI)).isZero();
        assertThat(service.chuanHoa(THU_HAI, THU_HAI, true)).isEmpty();
        verify(kpi, times(1)).updateKpiPoints(anyLong(), anyString(), anyInt(), any(), anyString());
    }
}
