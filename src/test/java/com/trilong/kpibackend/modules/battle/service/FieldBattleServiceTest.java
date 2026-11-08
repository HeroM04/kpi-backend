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

        service.rejectBattle(50L, 1L, "Ảnh không thấy khách");

        assertThat(b.getStatus()).isEqualTo("REJECTED");
        verify(kpi).updateKpiPoints(eq(7L), eq("meeting"), eq(-10), eq(nop), startsWith("Admin từ chối thực chiến"));
    }

    @Test
    @DisplayName("Từ chối báo cáo duyệt lúc nhóm đã đầy (vào 0đ) → không trừ gì, không ăn điểm báo cáo khác")
    void tuChoiKhiDaDayNhom() {
        baoCao("MEETING", "APPROVED");
        when(kpi.diemThucDaCong(anyLong(), anyString(), anyString(), any(), anyInt())).thenReturn(0);

        service.rejectBattle(50L, 1L, "Ảnh không thấy khách");

        verify(kpi, never()).updateKpiPoints(any(), any(), anyInt(), any(), any());
    }

    @Test
    @DisplayName("Từ chối báo cáo chưa duyệt → không đụng điểm")
    void tuChoiChoDuyet() {
        baoCao("MEETING", "PENDING");
        service.rejectBattle(50L, 1L, "Ảnh không thấy khách");
        verify(kpi, never()).updateKpiPoints(any(), any(), anyInt(), any(), any());
    }

    // ── Gửi báo cáo: chặn bấm Gửi lặp ──────────────────────────────────────

    private com.trilong.kpibackend.modules.battle.dto.SubmitFieldBattleDTO guiBaoCao(String loai, String noiDung) {
        var dto = new com.trilong.kpibackend.modules.battle.dto.SubmitFieldBattleDTO();
        dto.setBattleType(loai);
        dto.setContent(noiDung);
        dto.setPhotoUrl("https://anh/" + System.nanoTime() + ".jpg");   // mỗi lần bấm tải ảnh riêng
        return dto;
    }

    private FieldBattle daCo(String loai, String noiDung) {
        FieldBattle b = FieldBattle.builder().id(60L).user(nhanSu).content(noiDung).battleType(loai).status("PENDING").build();
        b.setSubmittedAt(ZonedDateTime.now());
        return b;
    }

    @Test
    @DisplayName("Gửi lần đầu → khóa người gửi rồi lưu báo cáo mới")
    void guiLanDau() {
        when(userRepository.khoaTheoId(7L)).thenReturn(Optional.of(nhanSu));
        when(fieldBattleRepository.findByUserIdAndSubmittedAtAfter(eq(7L), any())).thenReturn(java.util.List.of());

        FieldBattle b = service.submitBattle(7L, guiBaoCao("MEETING", "hẹn gặp kh trao đổi dự án starlake"));

        assertThat(b.getStatus()).isEqualTo("PENDING");
        verify(userRepository).khoaTheoId(7L);
        verify(fieldBattleRepository).save(any());
    }

    @Test
    @DisplayName("Bấm Gửi lặp: cùng loại, cùng nội dung (khác hoa thường / dấu cách) trong 2 phút → trả lại bản đã có, không lưu thêm")
    void guiLap() {
        FieldBattle cu = daCo("MEETING", "hẹn gặp kh trao đổi dự án starlake");
        when(userRepository.khoaTheoId(7L)).thenReturn(Optional.of(nhanSu));
        when(fieldBattleRepository.findByUserIdAndSubmittedAtAfter(eq(7L), any())).thenReturn(java.util.List.of(cu));

        FieldBattle b = service.submitBattle(7L, guiBaoCao("MEETING", "  Hẹn gặp KH   trao đổi dự án starlake "));

        assertThat(b).isSameAs(cu);
        verify(fieldBattleRepository, never()).save(any());
    }

    @Test
    @DisplayName("Khác loại hoặc khác nội dung → vẫn là báo cáo mới")
    void khacLoaiKhacNoiDung() {
        when(userRepository.khoaTheoId(7L)).thenReturn(Optional.of(nhanSu));
        when(fieldBattleRepository.findByUserIdAndSubmittedAtAfter(eq(7L), any()))
                .thenReturn(java.util.List.of(daCo("MEETING", "tiếp khách")));

        service.submitBattle(7L, guiBaoCao("SUPPORT", "tiếp khách"));
        service.submitBattle(7L, guiBaoCao("MEETING", "tiếp khách dự án Vista"));

        verify(fieldBattleRepository, times(2)).save(any());
    }

    // ── Sửa báo cáo qua modal "Sửa" trên web (PUT /field-battle/{id}) ─────────

    private com.trilong.kpibackend.modules.battle.dto.UpdateFieldBattleDTO sua(String trangThai, String lyDo) {
        var dto = new com.trilong.kpibackend.modules.battle.dto.UpdateFieldBattleDTO();
        dto.setStatus(trangThai);
        dto.setRejectReason(lyDo);
        return dto;
    }

    @Test
    @DisplayName("Sửa từ Chờ duyệt sang Đã duyệt → +10đ, ghi người duyệt như bấm Duyệt")
    void suaSangDuyet() {
        FieldBattle b = baoCao("MEETING", "PENDING");

        service.updateBattle(50L, sua("APPROVED", null), 1L);

        assertThat(b.getStatus()).isEqualTo("APPROVED");
        assertThat(b.getApprovedBy()).isEqualTo(admin);
        assertThat(b.getApprovedAt()).isNotNull();
        verify(kpi).updateKpiPoints(eq(7L), eq("meeting"), eq(10), eq(nop), startsWith("Admin duyệt thực chiến"));
    }

    @Test
    @DisplayName("Sửa từ Đã duyệt về Chờ duyệt → gỡ đúng số đã thực cộng, xóa người duyệt")
    void suaGoDuyet() {
        FieldBattle b = baoCao("MEETING", "APPROVED");
        b.setApprovedBy(admin);
        when(kpi.diemThucDaCong(7L, "meeting", "Admin duyệt thực chiến", nop, 10)).thenReturn(6);

        service.updateBattle(50L, sua("PENDING", null), 1L);

        assertThat(b.getStatus()).isEqualTo("PENDING");
        assertThat(b.getApprovedBy()).isNull();
        assertThat(b.getApprovedAt()).isNull();
        verify(kpi).updateKpiPoints(eq(7L), eq("meeting"), eq(-6), eq(nop), startsWith("Admin gỡ duyệt thực chiến"));
    }

    @Test
    @DisplayName("Sửa sang Từ chối mà thiếu lý do → báo lỗi, báo cáo và điểm giữ nguyên")
    void suaSangTuChoiThieuLyDo() {
        FieldBattle b = baoCao("MEETING", "APPROVED");
        when(kpi.diemThucDaCong(anyLong(), anyString(), anyString(), any(), anyInt())).thenReturn(10);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.updateBattle(50L, sua("REJECTED", "  "), 1L))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("lý do");

        assertThat(b.getStatus()).isEqualTo("APPROVED");
        verify(kpi, never()).updateKpiPoints(any(), any(), anyInt(), any(), any());
        verify(fieldBattleRepository, never()).save(any());
    }

    @Test
    @DisplayName("Sửa từ Đã duyệt sang Từ chối có lý do → thu hồi điểm, lưu lý do đã cắt khoảng trắng")
    void suaSangTuChoi() {
        FieldBattle b = baoCao("MEETING", "APPROVED");
        when(kpi.diemThucDaCong(7L, "meeting", "Admin duyệt thực chiến", nop, 10)).thenReturn(10);

        service.updateBattle(50L, sua("REJECTED", "  Ảnh không thấy khách  "), 1L);

        assertThat(b.getStatus()).isEqualTo("REJECTED");
        assertThat(b.getRejectReason()).isEqualTo("Ảnh không thấy khách");
        assertThat(b.getApprovedBy()).isEqualTo(admin);
        verify(kpi).updateKpiPoints(eq(7L), eq("meeting"), eq(-10), eq(nop), anyString());
    }

    @Test
    @DisplayName("Sửa báo cáo bị từ chối sang Đã duyệt → +điểm và gỡ lý do từ chối")
    void suaTuChoiSangDuyet() {
        FieldBattle b = baoCao("SUPPORT", "REJECTED");
        b.setRejectReason("Ảnh mờ");

        service.updateBattle(50L, sua("APPROVED", "Ảnh mờ"), 1L);

        assertThat(b.getRejectReason()).isNull();
        verify(kpi).updateKpiPoints(eq(7L), eq("meeting"), eq(5), eq(nop), anyString());
    }

    @Test
    @DisplayName("Giữ Từ chối, chỉ sửa lại lý do → lưu lý do mới, không đụng điểm")
    void suaLyDo() {
        FieldBattle b = baoCao("MEETING", "REJECTED");
        b.setRejectReason("Ảnh mờ");

        service.updateBattle(50L, sua("REJECTED", "Không thấy khách trong ảnh"), 1L);

        assertThat(b.getRejectReason()).isEqualTo("Không thấy khách trong ảnh");
        verify(kpi, never()).updateKpiPoints(any(), any(), anyInt(), any(), any());
    }

    @Test
    @DisplayName("Sửa nội dung, không đổi trạng thái → ghi nội dung mới, không đụng điểm; nội dung trống thì giữ cũ")
    void suaNoiDung() {
        FieldBattle b = baoCao("MEETING", "APPROVED");
        var dto = sua(null, null);
        dto.setProject("Văn phòng Vista");
        dto.setCustomerName("Chị Lan");
        dto.setContent("   ");

        service.updateBattle(50L, dto, 1L);

        assertThat(b.getStatus()).isEqualTo("APPROVED");
        assertThat(b.getProject()).isEqualTo("Văn phòng Vista");
        assertThat(b.getCustomerName()).isEqualTo("Chị Lan");
        assertThat(b.getContent()).isEqualTo("gặp khách");
        verify(kpi, never()).updateKpiPoints(any(), any(), anyInt(), any(), any());
    }

    @Test
    @DisplayName("Đang duyệt mà đổi gặp khách → hỗ trợ: gỡ điểm loại cũ rồi cộng 5đ loại mới")
    void suaDoiLoai() {
        FieldBattle b = baoCao("MEETING", "APPROVED");
        when(kpi.diemThucDaCong(7L, "meeting", "Admin duyệt thực chiến", nop, 10)).thenReturn(10);
        var dto = sua("APPROVED", null);
        dto.setBattleType("SUPPORT");

        service.updateBattle(50L, dto, 1L);

        assertThat(b.getBattleType()).isEqualTo("SUPPORT");
        verify(kpi).updateKpiPoints(eq(7L), eq("meeting"), eq(-10), eq(nop), startsWith("Admin đổi loại thực chiến"));
        verify(kpi).updateKpiPoints(eq(7L), eq("meeting"), eq(5), eq(nop), startsWith("Admin duyệt thực chiến"));
    }

    @Test
    @DisplayName("Trạng thái lạ → báo lỗi, không lưu")
    void suaTrangThaiLa() {
        baoCao("MEETING", "PENDING");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.updateBattle(50L, sua("DONE", null), 1L))
                .isInstanceOf(IllegalArgumentException.class);
        verify(fieldBattleRepository, never()).save(any());
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
