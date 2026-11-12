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
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Admin "Chỉ tính công": người làm KPI chấm công ở cửa lúc 08:45, lên tới nơi
 * đã muộn — giữ ngày công, không cho điểm đúng giờ (0 điểm hoặc tính đi muộn).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CheckinChiTinhCongTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalDate THU_HAI = LocalDate.of(2026, 10, 12);
    private static final Long NGUOI = 7L;
    private static final ZonedDateTime LUC_0845 = THU_HAI.atTime(8, 45, 10).atZone(VN);

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
    private User nguoi;

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

    private void khoan(int diem, String lyDo, ZonedDateTime luc) {
        KpiLedgerEntry e = new KpiLedgerEntry();
        e.setUserId(NGUOI);
        e.setCategory("attendance");
        e.setPoints(diem);
        e.setEffectivePoints(diem);
        e.setReason(lyDo);
        e.setOccurredAt(luc);
        soCai.add(e);
    }

    @BeforeEach
    void chuanBi() {
        nguoi = User.builder().id(NGUOI).fullName("Vũ Tuyết Nhi").role("SALE").build();
        when(userRepository.findById(NGUOI)).thenAnswer(i -> Optional.of(nguoi));
        when(checkinRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(checkinRepo.findByUserIdAndCheckinTimeBetween(eq(NGUOI), any(), any())).thenAnswer(i -> List.copyOf(trongNgay));
        when(kpi.getWeekString(any())).thenReturn("2026-W42");
        when(ledger.findByUserIdAndCategoryAndWeekOrderByIdAsc(NGUOI, "attendance", "2026-W42")).thenAnswer(i -> List.copyOf(soCai));
        // Ghi khoản vào sổ như thật để lần bấm sau thấy số đang có
        when(kpi.updateKpiPoints(anyLong(), eq("attendance"), anyInt(), any(), anyString())).thenAnswer(i -> {
            khoan(i.getArgument(2), i.getArgument(4), i.getArgument(3));
            return null;
        });
    }

    @Test
    @DisplayName("Đã duyệt tự động +5 lúc 08:45 → chỉ tính công 0 điểm: gỡ 5đ, lưu chế độ + lý do")
    void khongDiem() {
        CheckinLog c = luot(5, "CHECK_IN", "APPROVED", LUC_0845);
        khoan(5, "Đi làm đúng giờ — chấm công lúc 08:45", LUC_0845);

        var kq = service.chiTinhCong(5L, "KHONG_DIEM", "Chấm ở cửa, lên tới nơi đã muộn");

        verify(kpi).updateKpiPoints(NGUOI, "attendance", -5, LUC_0845,
                "Admin chỉ tính công lượt vào lúc 08:45 — 0 điểm: Chấm ở cửa, lên tới nơi đã muộn");
        assertThat(c.getStatus()).isEqualTo("APPROVED");
        assertThat(c.getChiTinhCong()).isEqualTo("KHONG_DIEM");
        assertThat(c.getChiTinhCongLyDo()).isEqualTo("Chấm ở cửa, lên tới nơi đã muộn");
        assertThat(c.getCheckinTime()).isEqualTo(LUC_0845);
        assertThat(kq.diemTruoc()).isEqualTo(5);
        assertThat(kq.diemSau()).isZero();
        assertThat(kq.moTa()).isEqualTo("Đã chỉ tính công lượt vào 08:45 (0 điểm). Điểm chuyên cần +5đ → 0đ.");
    }

    @Test
    @DisplayName("Tính đi muộn: +5 thành −5 (một khoản −10); đổi sang 0 điểm rồi bỏ → trả lại +5")
    void diMuonRoiDoiRoiBo() {
        CheckinLog c = luot(5, "CHECK_IN", "APPROVED", LUC_0845);
        khoan(5, "Đi làm đúng giờ — chấm công lúc 08:45", LUC_0845);

        assertThat(service.chiTinhCong(5L, "DI_MUON", "Chấm ở cửa").diemSau()).isEqualTo(-5);
        verify(kpi).updateKpiPoints(NGUOI, "attendance", -10, LUC_0845,
                "Admin chỉ tính công lượt vào lúc 08:45 — tính là đi muộn: Chấm ở cửa");

        service.chiTinhCong(5L, "KHONG_DIEM", "Chấm ở cửa");
        verify(kpi).updateKpiPoints(eq(NGUOI), eq("attendance"), eq(5), eq(LUC_0845), anyString());

        var bo = service.chiTinhCong(5L, "BO", null);
        verify(kpi).updateKpiPoints(NGUOI, "attendance", 5, LUC_0845,
                "Admin bỏ chỉ tính công lượt vào lúc 08:45 — tính lại theo giờ chấm");
        assertThat(bo.diemSau()).isEqualTo(5);
        assertThat(c.getChiTinhCong()).isNull();
        assertThat(c.getChiTinhCongLyDo()).isNull();
        assertThat(soCai.stream().mapToInt(KpiLedgerEntry::getPoints).sum()).isEqualTo(5);
    }

    @Test
    @DisplayName("Lượt chờ duyệt → \"Duyệt chỉ tính công\" tính đi muộn: duyệt luôn, −5 kèm lý do")
    void choDuyetThiDuyetLuon() {
        CheckinLog c = luot(5, "CHECK_IN", "PENDING", LUC_0845);

        var kq = service.chiTinhCong(5L, "DI_MUON", "Ngoài vùng, tới nơi đã muộn");

        assertThat(c.getStatus()).isEqualTo("APPROVED");
        verify(kpi).updateKpiPoints(NGUOI, "attendance", -5, LUC_0845,
                "Admin duyệt chấm công ngoại tuyến — Chỉ tính công, tính là đi muộn — chấm công lúc 08:45 (Ngoài vùng, tới nơi đã muộn)");
        assertThat(kq.diemSau()).isEqualTo(-5);
    }

    @Test
    @DisplayName("Lượt chờ duyệt → \"Duyệt chỉ tính công\" 0 điểm: duyệt, không ghi khoản điểm nào")
    void choDuyetKhongDiem() {
        CheckinLog c = luot(5, "CHECK_IN", "PENDING", LUC_0845);

        service.chiTinhCong(5L, "KHONG_DIEM", "Tới nơi đã muộn");

        assertThat(c.getStatus()).isEqualTo("APPROVED");
        verify(kpi, never()).updateKpiPoints(anyLong(), eq("attendance"), anyInt(), any(), anyString());
    }

    @Test
    @DisplayName("Thu hồi lượt đã chỉ tính công 0 điểm → không trừ thêm 5đ (trước đây tính lại theo giờ chấm)")
    void thuHoiSauChiTinhCong() {
        CheckinLog c = luot(5, "CHECK_IN", "APPROVED", LUC_0845);
        c.setChiTinhCong("KHONG_DIEM");

        service.updateStatus(5L, "REJECTED", "Chấm hộ");

        verify(kpi, never()).updateKpiPoints(anyLong(), eq("attendance"), anyInt(), any(), anyString());
    }

    @Test
    @DisplayName("Thu hồi lượt vào thứ hai trong ngày (lúc duyệt được 0đ) → không gỡ gì")
    void thuHoiLuotVaoThuHai() {
        luot(4, "CHECK_IN", "APPROVED", THU_HAI.atTime(8, 30).atZone(VN));
        luot(5, "CHECK_IN", "APPROVED", THU_HAI.atTime(13, 30).atZone(VN));

        service.updateStatus(5L, "REJECTED", "Chấm trùng");

        verify(kpi, never()).updateKpiPoints(anyLong(), eq("attendance"), anyInt(), any(), anyString());
    }

    @Test
    @DisplayName("Không áp được: thiếu lý do, lượt ra, đã từ chối, nhân sự văn phòng, thứ Bảy, không phải lượt vào đầu ngày")
    void khongApDuoc() {
        luot(5, "CHECK_IN", "APPROVED", LUC_0845);
        assertThatThrownBy(() -> service.chiTinhCong(5L, "KHONG_DIEM", "  "))
                .hasMessageContaining("Nhập lý do");
        assertThatThrownBy(() -> service.chiTinhCong(5L, "LAM_LAI", "x"))
                .hasMessageContaining("0 điểm hoặc tính đi muộn");

        luot(6, "CHECK_OUT", "APPROVED", THU_HAI.atTime(17, 40).atZone(VN));
        assertThatThrownBy(() -> service.chiTinhCong(6L, "KHONG_DIEM", "x")).hasMessageContaining("lượt chấm công VÀO");

        luot(7, "CHECK_IN", "REJECTED", THU_HAI.atTime(8, 40).atZone(VN));
        assertThatThrownBy(() -> service.chiTinhCong(7L, "KHONG_DIEM", "x")).hasMessageContaining("bị từ chối");

        luot(8, "CHECK_IN", "APPROVED", THU_HAI.atTime(13, 40).atZone(VN));
        assertThatThrownBy(() -> service.chiTinhCong(8L, "KHONG_DIEM", "x")).hasMessageContaining("lượt vào đầu tiên");

        luot(9, "CHECK_IN", "APPROVED", LocalDate.of(2026, 10, 17).atTime(8, 45).atZone(VN));
        assertThatThrownBy(() -> service.chiTinhCong(9L, "KHONG_DIEM", "x")).hasMessageContaining("Thứ Bảy");

        nguoi = User.builder().id(NGUOI).fullName("Kế toán").role("VAN_PHONG").build();
        assertThatThrownBy(() -> service.chiTinhCong(5L, "KHONG_DIEM", "x")).hasMessageContaining("không làm KPI");

        verify(kpi, never()).updateKpiPoints(anyLong(), anyString(), anyInt(), any(), anyString());
    }

    @Test
    @DisplayName("Admin sửa giờ lượt đã chỉ tính công đi muộn → tính lại vẫn −5, không thành +5")
    void suaGioGiuCheDo() {
        CheckinLog c = luot(5, "CHECK_IN", "APPROVED", LUC_0845);
        c.setChiTinhCong("DI_MUON");
        c.setChiTinhCongLyDo("Chấm ở cửa");
        khoan(5, "Đi làm đúng giờ — chấm công lúc 08:45", LUC_0845);
        khoan(-10, "Admin chỉ tính công lượt vào lúc 08:45 — tính là đi muộn: Chấm ở cửa", LUC_0845);

        service.suaGio(5L, java.time.LocalTime.of(8, 40), "Sửa nhầm giờ");

        verify(kpi).updateKpiPoints(eq(NGUOI), eq("attendance"), eq(5), eq(LUC_0845), anyString());
        verify(kpi).updateKpiPoints(NGUOI, "attendance", -5, THU_HAI.atTime(8, 40).atZone(VN),
                "Admin sửa giờ chấm công — Chỉ tính công, tính là đi muộn — chấm công lúc 08:40 (Chấm ở cửa)");
    }
}
