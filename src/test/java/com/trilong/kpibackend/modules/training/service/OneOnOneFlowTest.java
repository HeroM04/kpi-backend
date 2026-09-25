package com.trilong.kpibackend.modules.training.service;

import com.trilong.kpibackend.modules.kpi.entity.KpiLedgerEntry;
import com.trilong.kpibackend.modules.kpi.repository.KpiLedgerEntryRepository;
import com.trilong.kpibackend.modules.kpi.service.KpiCalculationService;
import com.trilong.kpibackend.modules.notification.service.PushNotificationService;
import com.trilong.kpibackend.modules.training.entity.OneOnOneTraining;
import com.trilong.kpibackend.modules.training.repository.OneOnOneTrainingRepository;
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

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Đào tạo 1-1: nộp → chờ duyệt → duyệt / từ chối / xóa, và chuyển báo cáo tự duyệt cũ về chờ duyệt. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OneOnOneFlowTest {

    @Mock OneOnOneTrainingRepository repo;
    @Mock KpiCalculationService kpi;
    @Mock KpiLedgerEntryRepository ledger;
    @Mock PushNotificationService push;
    @Mock SimpMessagingTemplate ws;
    @InjectMocks OneOnOneTrainingService service;

    private final ZonedDateTime nop = ZonedDateTime.of(2026, 9, 23, 9, 0, 22, 0, ZoneId.of("Asia/Ho_Chi_Minh"));

    private OneOnOneTraining baoCao(String trangThai, Long nguoiDuyet) {
        OneOnOneTraining t = OneOnOneTraining.builder().id(5L).userId(7L).content("cách học dự án")
                .status(trangThai).reviewedBy(nguoiDuyet).submittedAt(nop).build();
        when(repo.findById(5L)).thenReturn(Optional.of(t));
        return t;
    }

    private KpiLedgerEntry dong(String dienGiai, int thucNhan) {
        KpiLedgerEntry e = new KpiLedgerEntry();
        e.setId(1L); e.setOccurredAt(nop); e.setEffectivePoints(thucNhan); e.setReason(dienGiai);
        return e;
    }

    @BeforeEach
    void chuanBi() {
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("Nộp → CHỜ DUYỆT, chưa cộng điểm, báo web quản trị")
    void nop() {
        var kq = service.submitOneOnOneTraining(7L, "tele sales", null);
        assertThat(kq.getStatus()).isEqualTo("PENDING");
        verifyNoInteractions(kpi);
        verify(ws).convertAndSend(eq("/topic/admin/requests"), any(Object.class));
    }

    @Test
    @DisplayName("Duyệt → +5đ Thực chiến vào tuần NỘP; duyệt lại không cộng thêm")
    void duyet() {
        baoCao("PENDING", null);
        service.duyet(5L, 1L);
        service.duyet(5L, 1L);
        verify(kpi, times(1)).updateKpiPoints(eq(7L), eq("meeting"), eq(5), eq(nop), startsWith("Admin duyệt đào tạo 1-1"));
    }

    @Test
    @DisplayName("Từ chối báo cáo chờ duyệt → không đụng điểm, nhân sự nhận thông báo")
    void tuChoiChoDuyet() {
        baoCao("PENDING", null);
        service.tuChoi(5L, 1L);
        verify(kpi, never()).updateKpiPoints(any(), any(), anyInt(), any(), any());
        verify(push).guiToiNhanSu(eq(7L), contains("bị từ chối"), anyString(), any());
    }

    @Test
    @DisplayName("Từ chối báo cáo đã duyệt lúc nhóm đã đầy (vào 0đ) → không trừ gì")
    void tuChoiDaDuyetKhiDay() {
        baoCao("APPROVED", 1L);
        when(ledger.findByUserIdAndCategoryAndReasonStartingWithOrderByIdAsc(7L, "meeting", "Admin duyệt đào tạo 1-1"))
                .thenReturn(List.of(dong("Admin duyệt đào tạo 1-1 — cách học dự án", 0)));
        service.tuChoi(5L, 1L);
        verify(kpi, never()).updateKpiPoints(any(), any(), anyInt(), any(), any());
    }

    @Test
    @DisplayName("Xóa báo cáo đã duyệt → thu hồi đúng số đang giữ rồi xóa")
    void xoa() {
        OneOnOneTraining t = baoCao("APPROVED", 1L);
        when(ledger.findByUserIdAndCategoryAndReasonStartingWithOrderByIdAsc(7L, "meeting", "Admin duyệt đào tạo 1-1"))
                .thenReturn(List.of(dong("Admin duyệt đào tạo 1-1 — cách học dự án", 5)));

        assertThat(service.xoa(5L)).isEqualTo(5);
        verify(kpi).updateKpiPoints(eq(7L), eq("meeting"), eq(-5), eq(nop), startsWith("Admin xóa báo cáo đào tạo 1-1"));
        verify(repo).delete(t);
    }

    @Test
    @DisplayName("Chuyển báo cáo TỰ DUYỆT đời cũ về chờ duyệt, hoàn đúng số đã vào")
    void chuyenTuDuyetCu() {
        OneOnOneTraining cu = OneOnOneTraining.builder().id(5L).userId(7L).content("cách học dự án")
                .status("APPROVED").submittedAt(nop).build();
        when(repo.findByStatusAndReviewedByIsNullOrderBySubmittedAtAsc("APPROVED")).thenReturn(List.of(cu));
        when(ledger.findByUserIdAndCategoryAndReason(7L, "meeting", "Báo cáo đào tạo 1-1"))
                .thenReturn(List.of(dong("Báo cáo đào tạo 1-1", 5)));

        var kq = service.chuyenBaoCaoTuDuyetVeChoDuyet();

        assertThat(cu.getStatus()).isEqualTo("PENDING");
        assertThat(kq.soBaoCao()).isEqualTo(1);
        assertThat(kq.tongDiemHoan()).isEqualTo(5);
        verify(kpi).updateKpiPoints(eq(7L), eq("meeting"), eq(-5), eq(nop), contains("chuyển về chờ Admin duyệt"));
    }
}
