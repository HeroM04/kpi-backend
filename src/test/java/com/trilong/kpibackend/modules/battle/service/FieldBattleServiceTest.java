package com.trilong.kpibackend.modules.battle.service;

import com.trilong.kpibackend.modules.battle.entity.FieldBattle;
import com.trilong.kpibackend.modules.battle.repository.FieldBattleRepository;
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

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Duyệt / từ chối / xóa báo cáo thực chiến — mỗi thao tác của Admin đổi điểm
 * của nhân sự, nên từng nhánh phải cộng hoặc gỡ đúng số.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FieldBattleServiceTest {

    @Mock FieldBattleRepository fieldBattleRepository;
    @Mock UserRepository userRepository;
    @Mock KpiCalculationService kpi;
    @InjectMocks FieldBattleService service;

    private final User nhanSu = User.builder().id(7L).fullName("Sale A").build();
    private final User admin = User.builder().id(1L).fullName("Admin").build();
    private final ZonedDateTime nop = ZonedDateTime.of(2026, 9, 23, 10, 0, 0, 0, ZoneId.of("Asia/Ho_Chi_Minh"));

    private FieldBattle baoCao(String loai, String trangThai) {
        FieldBattle b = FieldBattle.builder().id(50L).user(nhanSu).customerName("Anh Tuấn").project("Vista")
                .content("gặp khách").battleType(loai).status(trangThai).build();
        b.setSubmittedAt(nop);
        when(fieldBattleRepository.findById(50L)).thenReturn(Optional.of(b));
        return b;
    }

    @BeforeEach
    void chuanBi() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(fieldBattleRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("Duyệt gặp khách → +10đ Thực chiến, neo vào lúc NỘP (không phải lúc duyệt)")
    void duyetGapKhach() {
        FieldBattle b = baoCao("MEETING", "PENDING");
        service.approveBattle(50L, 1L);

        assertThat(b.getStatus()).isEqualTo("APPROVED");
        assertThat(b.getApprovedBy()).isEqualTo(admin);
        verify(kpi).updateKpiPoints(eq(7L), eq("meeting"), eq(10), eq(nop), startsWith("Admin duyệt thực chiến"));
    }

    @Test
    @DisplayName("Duyệt hỗ trợ khách của người khác → +5đ")
    void duyetHoTro() {
        baoCao("SUPPORT", "PENDING");
        service.approveBattle(50L, 1L);
        verify(kpi).updateKpiPoints(eq(7L), eq("meeting"), eq(5), any(), anyString());
    }

    @Test
    @DisplayName("Duyệt lại báo cáo đã duyệt → không cộng lần hai")
    void duyetHaiLan() {
        baoCao("MEETING", "APPROVED");
        service.approveBattle(50L, 1L);
        verify(kpi, never()).updateKpiPoints(any(), any(), anyInt(), any(), any());
    }

    @Test
    @DisplayName("Từ chối báo cáo đã duyệt → gỡ đúng số đã thực cộng")
    void tuChoiDaDuyet() {
        FieldBattle b = baoCao("MEETING", "APPROVED");
        when(kpi.diemThucDaCong(7L, "meeting", "Admin duyệt thực chiến", nop, 10)).thenReturn(10);

        service.rejectBattle(50L, 1L);

        assertThat(b.getStatus()).isEqualTo("REJECTED");
        verify(kpi).updateKpiPoints(eq(7L), eq("meeting"), eq(-10), eq(nop), startsWith("Admin từ chối thực chiến"));
    }

    @Test
    @DisplayName("Từ chối báo cáo duyệt lúc nhóm đã đầy (vào 0đ) → không trừ gì, không ăn điểm báo cáo khác")
    void tuChoiKhiDaDayNhom() {
        baoCao("MEETING", "APPROVED");
        when(kpi.diemThucDaCong(anyLong(), anyString(), anyString(), any(), anyInt())).thenReturn(0);

        service.rejectBattle(50L, 1L);

        verify(kpi, never()).updateKpiPoints(any(), any(), anyInt(), any(), any());
    }

    @Test
    @DisplayName("Từ chối báo cáo chưa duyệt → không đụng điểm")
    void tuChoiChoDuyet() {
        baoCao("MEETING", "PENDING");
        service.rejectBattle(50L, 1L);
        verify(kpi, never()).updateKpiPoints(any(), any(), anyInt(), any(), any());
    }

    @Test
    @DisplayName("Xóa báo cáo đã duyệt vào được một phần (5/10đ) → gỡ đúng 5đ rồi xóa")
    void xoaDaDuyet() {
        FieldBattle b = baoCao("MEETING", "APPROVED");
        when(kpi.diemThucDaCong(anyLong(), anyString(), anyString(), any(), anyInt())).thenReturn(5);

        service.deleteBattle(50L);

        verify(kpi).updateKpiPoints(eq(7L), eq("meeting"), eq(-5), eq(nop), startsWith("Admin xóa báo cáo thực chiến"));
        verify(fieldBattleRepository).delete(b);
    }
}
