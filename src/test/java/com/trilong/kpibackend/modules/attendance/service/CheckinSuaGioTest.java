package com.trilong.kpibackend.modules.attendance.service;

import com.trilong.kpibackend.core.service.FaceRecognitionService;
import com.trilong.kpibackend.modules.attendance.entity.CheckinLog;
import com.trilong.kpibackend.modules.attendance.repository.CheckinLogRepository;
import com.trilong.kpibackend.modules.kpi.entity.KpiLedgerEntry;
import com.trilong.kpibackend.modules.kpi.repository.KpiLedgerEntryRepository;
import com.trilong.kpibackend.modules.kpi.service.KpiCalculationService;
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
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Admin sửa giờ một lượt chấm công: tính lại điểm đúng giờ / đi muộn theo giờ mới, gỡ đúng số đã thực có. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CheckinSuaGioTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalDate THU_HAI = LocalDate.of(2026, 10, 12);
    private static final Long NGUOI = 7L;

    @Mock CheckinLogRepository checkinRepo;
    @Mock UserRepository userRepository;
    @Mock KpiCalculationService kpi;
    @Mock KpiLedgerEntryRepository ledger;
    @Mock SimpMessagingTemplate messagingTemplate;
    @Mock FaceRecognitionService faceRecognitionService;
    @Mock TangCaService tangCaService;
    @InjectMocks CheckinService service;

    private final List<CheckinLog> trongNgay = new ArrayList<>();
    private final List<KpiLedgerEntry> soCai = new ArrayList<>();

    private CheckinLog luot(long ma, String loai, String trangThai, ZonedDateTime luc) {
        CheckinLog c = new CheckinLog();
        c.setId(ma);
        c.setUserId(NGUOI);
        c.setActionType(loai);
        c.setStatus(trangThai);
        c.setCheckinTime(luc);
        trongNgay.add(c);
        when(checkinRepo.findById(ma)).thenReturn(Optional.of(c));
        return c;
    }

    private void khoan(int thuc, String lyDo, ZonedDateTime luc) {
        KpiLedgerEntry e = new KpiLedgerEntry();
        e.setUserId(NGUOI);
        e.setCategory("attendance");
        e.setPoints(thuc < 0 ? -5 : 5);
        e.setEffectivePoints(thuc);
        e.setReason(lyDo);
        e.setOccurredAt(luc);
        soCai.add(e);
    }

    private ZonedDateTime luc(LocalDate ngay, int gio, int phut, int giay) {
        return ngay.atTime(gio, phut, giay).atZone(VN);
    }

    @BeforeEach
    void chuanBi() {
        when(userRepository.findById(NGUOI)).thenReturn(Optional.of(User.builder().id(NGUOI).fullName("Vũ Tuyết Nhi").build()));
        when(checkinRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(checkinRepo.findByUserIdAndCheckinTimeBetween(eq(NGUOI), any(), any())).thenAnswer(i -> List.copyOf(trongNgay));
        when(kpi.getWeekString(any())).thenReturn("2026-W42");
        when(ledger.findByUserIdAndCategoryAndWeekOrderByIdAsc(NGUOI, "attendance", "2026-W42")).thenAnswer(i -> List.copyOf(soCai));
    }

    @Test
    @DisplayName("App chậm, 08:46 mới nhận → sửa thành 08:45: gỡ −5 đi muộn, cộng +5 đúng giờ, ghi chú giờ cũ + lý do")
    void diMuonThanhDungGio() {
        ZonedDateTime cu = luc(THU_HAI, 8, 46, 20);
        CheckinLog c = luot(5, "CHECK_IN", "APPROVED", cu);
        khoan(-5, "Đi muộn ca sáng — chấm công lúc 08:46 (hạn 08:45)", cu);

        var kq = service.suaGio(5L, LocalTime.of(8, 45), "App chậm, tới lúc 08:45");

        verify(kpi).updateKpiPoints(NGUOI, "attendance", 5, cu,
                "Admin sửa giờ chấm công: gỡ khoản lúc 08:46 (giờ đúng 08:45) — App chậm, tới lúc 08:45");
        verify(kpi).updateKpiPoints(NGUOI, "attendance", 5, luc(THU_HAI, 8, 45, 0),
                "Admin sửa giờ chấm công — Đi làm đúng giờ — chấm công lúc 08:45");
        assertThat(kq.diemGo()).isEqualTo(-5);
        assertThat(kq.diemCong()).isEqualTo(5);
        assertThat(c.getCheckinTime()).isEqualTo(luc(THU_HAI, 8, 45, 0));
        assertThat(c.getNote()).isEqualTo("Admin sửa giờ 08:46 → 08:45: App chậm, tới lúc 08:45");
        assertThat(kq.moTa()).isEqualTo("Đã sửa giờ vào 08:46 → 08:45. Gỡ khoản cũ -5đ. Tính lại: +5đ.");
    }

    @Test
    @DisplayName("Khoản −5 lúc đó chỉ trừ thực được 2đ (tuần đang 0đ) → chỉ hoàn 2đ, không hoàn quá")
    void hoanDungSoThucTru() {
        ZonedDateTime cu = luc(THU_HAI, 8, 50, 0);
        luot(5, "CHECK_IN", "APPROVED", cu);
        khoan(-2, "Đi muộn ca sáng — chấm công lúc 08:50 (hạn 08:45)", cu);

        service.suaGio(5L, LocalTime.of(8, 40), "Chấm hộ bị chậm");

        verify(kpi).updateKpiPoints(eq(NGUOI), eq("attendance"), eq(2), eq(cu), anyString());
        verify(kpi).updateKpiPoints(eq(NGUOI), eq("attendance"), eq(5), eq(luc(THU_HAI, 8, 40, 0)), anyString());
    }

    @Test
    @DisplayName("Đã từng thu hồi rồi duyệt lại: cộng cả các khoản cùng mốc giờ để ra số đang có")
    void congCaKhoanThuHoi() {
        ZonedDateTime cu = luc(THU_HAI, 9, 10, 5);
        luot(5, "CHECK_IN", "APPROVED", cu);
        khoan(-5, "Admin duyệt chấm công ngoại tuyến — Đi muộn ca sáng — chấm công lúc 09:10 (hạn 08:45)", cu);
        khoan(5, "Admin thu hồi duyệt chấm công lúc 09:10", cu);
        khoan(-5, "Admin duyệt chấm công ngoại tuyến — Đi muộn ca sáng — chấm công lúc 09:10 (hạn 08:45)", cu);
        khoan(10, "Tăng ca — về lúc 09:10", cu.plusHours(11));   // khác mốc, không tính

        var kq = service.suaGio(5L, LocalTime.of(8, 30), "Gặp khách sớm, quên chấm");

        assertThat(kq.diemGo()).isEqualTo(-5);
        assertThat(kq.diemCong()).isEqualTo(5);
    }

    @Test
    @DisplayName("Không phải lượt vào đầu tiên trong ngày → không có điểm để gỡ, giờ mới cũng không cộng")
    void khongPhaiLuotDau() {
        luot(4, "CHECK_IN", "APPROVED", luc(THU_HAI, 8, 30, 0));
        luot(5, "CHECK_IN", "APPROVED", luc(THU_HAI, 13, 50, 0));

        var kq = service.suaGio(5L, LocalTime.of(13, 40), "Sai giờ");

        verify(kpi, never()).updateKpiPoints(anyLong(), anyString(), anyInt(), any(), anyString());
        assertThat(kq.diemGo()).isZero();
        assertThat(kq.diemCong()).isZero();
    }

    @Test
    @DisplayName("Lượt chờ duyệt chỉ đổi giờ; thứ Bảy không tính điểm; giờ tương lai và thiếu lý do bị chặn")
    void cacTruongHopKhac() {
        luot(5, "CHECK_IN", "PENDING", luc(THU_HAI, 8, 50, 0));
        var kq = service.suaGio(5L, LocalTime.of(8, 40), "Sai giờ");
        assertThat(kq.moTa()).contains("chưa duyệt");

        LocalDate thuBay = THU_HAI.minusDays(2);
        luot(6, "CHECK_IN", "APPROVED", luc(thuBay, 8, 50, 0));
        service.suaGio(6L, LocalTime.of(8, 40), "Sai giờ");
        verify(kpi, never()).updateKpiPoints(anyLong(), anyString(), anyInt(), any(), anyString());

        assertThatThrownBy(() -> service.suaGio(5L, LocalTime.of(8, 40), " ")).hasMessageContaining("lý do");
        luot(8, "CHECK_IN", "APPROVED", ZonedDateTime.now(VN).withHour(0).withMinute(1));
        assertThatThrownBy(() -> service.suaGio(8L, LocalTime.of(23, 59), "Sai giờ")).hasMessageContaining("chưa tới");
    }

    @Test
    @DisplayName("Lượt ra 19:50 sửa thành 20:10 → được tính tăng ca; 20:10 sửa về 19:50 → thu lại nếu hết lượt về")
    void suaGioRaTangCa() {
        luot(9, "CHECK_OUT", "APPROVED", luc(THU_HAI, 19, 50, 0));
        when(tangCaService.conLuotVeTangCa(NGUOI, THU_HAI, 9L)).thenReturn(false);
        service.suaGio(9L, LocalTime.of(20, 10), "Quên chấm ra");
        verify(kpi).updateKpiPoints(NGUOI, "meeting", 5, luc(THU_HAI, 20, 10, 0), "Tăng ca — về lúc 20:10");

        service.suaGio(9L, LocalTime.of(19, 50), "Chấm nhầm");
        verify(tangCaService).thuLaiNeuHetLuot(NGUOI, THU_HAI);
    }
}
