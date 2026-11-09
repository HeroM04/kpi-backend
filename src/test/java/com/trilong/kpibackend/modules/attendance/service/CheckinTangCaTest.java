package com.trilong.kpibackend.modules.attendance.service;

import com.trilong.kpibackend.core.service.FaceRecognitionService;
import com.trilong.kpibackend.modules.attendance.dto.CheckinRequestDTO;
import com.trilong.kpibackend.modules.attendance.entity.CheckinLog;
import com.trilong.kpibackend.modules.attendance.repository.CheckinLogRepository;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Chấm công ra: tăng ca mỗi ngày một lần, thu hồi lượt về thì thu lại, bấm gửi
 * lặp không tạo lượt trùng; Admin duyệt tất cả lượt chờ theo bộ lọc.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CheckinTangCaTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalDate THU_HAI = LocalDate.of(2026, 10, 12);
    private static final Long NGUOI = 7L;

    @Mock CheckinLogRepository checkinRepo;
    @Mock UserRepository userRepository;
    @Mock KpiCalculationService kpi;
    @Mock SimpMessagingTemplate messagingTemplate;
    @Mock FaceRecognitionService faceRecognitionService;
    @Mock TangCaService tangCaService;
    @InjectMocks CheckinService service;

    private CheckinLog luot(long ma, String loai, String trangThai, ZonedDateTime luc) {
        CheckinLog c = new CheckinLog();
        c.setId(ma);
        c.setUserId(NGUOI);
        c.setActionType(loai);
        c.setStatus(trangThai);
        c.setCheckinTime(luc);
        return c;
    }

    @BeforeEach
    void chuanBi() {
        User u = User.builder().id(NGUOI).fullName("Trần Thị Hương").build();
        when(userRepository.findById(NGUOI)).thenReturn(Optional.of(u));
        when(userRepository.khoaTheoId(NGUOI)).thenReturn(Optional.of(u));
        when(checkinRepo.save(any())).thenAnswer(i -> {
            CheckinLog c = i.getArgument(0);
            if (c.getId() == null) c.setId(99L);
            return c;
        });
    }

    @Test
    @DisplayName("Duyệt lượt về 20:20 khi ngày đó đã có lượt về được tính → không cộng tăng ca lần nữa")
    void duyetLuotVeThuHai() {
        CheckinLog c = luot(5, "CHECK_OUT", "PENDING", THU_HAI.atTime(20, 20).atZone(VN));
        when(checkinRepo.findById(5L)).thenReturn(Optional.of(c));
        when(tangCaService.conLuotVeTangCa(NGUOI, THU_HAI, 5L)).thenReturn(true);

        service.updateStatus(5L, "APPROVED", null);

        verify(kpi, never()).updateKpiPoints(eq(NGUOI), eq("meeting"), anyInt(), any(), anyString());
    }

    @Test
    @DisplayName("Duyệt lượt về 20:20 là lượt đầu tiên đủ giờ → +5 tăng ca")
    void duyetLuotVeDauTien() {
        ZonedDateTime luc = THU_HAI.atTime(20, 20).atZone(VN);
        when(checkinRepo.findById(5L)).thenReturn(Optional.of(luot(5, "CHECK_OUT", "PENDING", luc)));
        when(tangCaService.conLuotVeTangCa(NGUOI, THU_HAI, 5L)).thenReturn(false);

        service.updateStatus(5L, "APPROVED", null);

        verify(kpi).updateKpiPoints(NGUOI, "meeting", 5, luc, "Tăng ca — về lúc 20:20");
    }

    @Test
    @DisplayName("Lượt về đã cộng bị xóa khỏi bảng chấm công, nhật ký ngày đó còn +5 → lượt về sau không cộng nữa")
    void nhatKyDaCoTangCa() {
        when(checkinRepo.findById(5L)).thenReturn(Optional.of(
                luot(5, "CHECK_OUT", "PENDING", THU_HAI.atTime(20, 40).atZone(VN))));
        when(tangCaService.conLuotVeTangCa(NGUOI, THU_HAI, 5L)).thenReturn(false);
        when(tangCaService.daCongTrongNgay(NGUOI, THU_HAI)).thenReturn(true);

        service.updateStatus(5L, "APPROVED", null);

        verify(kpi, never()).updateKpiPoints(eq(NGUOI), eq("meeting"), anyInt(), any(), anyString());
    }

    @Test
    @DisplayName("Thu hồi lượt về đủ giờ tăng ca → nhờ TangCaService thu lại nếu ngày đó hết lượt về")
    void thuHoiLuotVe() {
        when(checkinRepo.findById(5L)).thenReturn(Optional.of(
                luot(5, "CHECK_OUT", "APPROVED", THU_HAI.atTime(20, 20).atZone(VN))));
        when(checkinRepo.findById(6L)).thenReturn(Optional.of(
                luot(6, "CHECK_OUT", "APPROVED", THU_HAI.atTime(17, 40).atZone(VN))));

        service.updateStatus(5L, "REJECTED", "Chấm công ra trùng");
        service.updateStatus(6L, "REJECTED", "Về sớm");

        verify(tangCaService).thuLaiNeuHetLuot(NGUOI, THU_HAI);
        verify(tangCaService, times(1)).thuLaiNeuHetLuot(anyLong(), any());
    }

    @Test
    @DisplayName("Bấm Xác nhận chấm công ra lần nữa trong 2 phút → trả lại lượt cũ, không lưu lượt mới")
    void guiLap() {
        CheckinLog cu = luot(40, "CHECK_OUT", "APPROVED", ZonedDateTime.now(VN).minusSeconds(30));
        when(checkinRepo.findByUserIdAndCheckinTimeBetween(eq(NGUOI), any(), any())).thenReturn(List.of(cu));
        CheckinRequestDTO req = new CheckinRequestDTO();
        req.setActionType("CHECK_OUT");
        req.setLatitude(20.999042);
        req.setLongitude(105.806702);
        req.setPhotoUrl("https://anh/1.jpg");

        assertThat(service.submitCheckin(NGUOI, req)).isSameAs(cu);
        verify(checkinRepo, never()).save(any());
        verifyNoInteractions(faceRecognitionService, kpi);
    }

    @Test
    @DisplayName("Lượt trước đã bị từ chối, hoặc khác loại → gửi lại vẫn được lưu")
    void khongPhaiGuiLap() {
        when(checkinRepo.findByUserIdAndCheckinTimeBetween(eq(NGUOI), any(), any())).thenReturn(List.of(
                luot(41, "CHECK_OUT", "REJECTED", ZonedDateTime.now(VN).minusSeconds(30)),
                luot(42, "CHECK_IN", "APPROVED", ZonedDateTime.now(VN).minusSeconds(60))));
        CheckinRequestDTO req = new CheckinRequestDTO();
        req.setActionType("CHECK_OUT");
        req.setLatitude(20.999042);
        req.setLongitude(105.806702);
        req.setPhotoUrl("https://anh/2.jpg");

        CheckinLog moi = service.submitCheckin(NGUOI, req);

        assertThat(moi.getId()).isEqualTo(99L);
        assertThat(moi.getActionType()).isEqualTo("CHECK_OUT");
        verify(checkinRepo).save(any());
    }

    @Test
    @DisplayName("Duyệt tất cả: chỉ lượt CHỜ khớp phòng ban + khoảng ngày, duyệt từ cũ tới mới")
    void duyetTatCa() {
        com.trilong.kpibackend.modules.user.entity.Department kd8 = new com.trilong.kpibackend.modules.user.entity.Department();
        kd8.setId(8L);
        User huong = User.builder().id(NGUOI).fullName("Trần Thị Hương").department(kd8).build();
        User khac = User.builder().id(9L).fullName("Lê Văn Minh").build();
        when(userRepository.findAll()).thenReturn(List.of(huong, khac));

        CheckinLog chieu = luot(3, "CHECK_IN", "PENDING", THU_HAI.atTime(13, 10).atZone(VN));
        CheckinLog sang = luot(2, "CHECK_IN", "PENDING", THU_HAI.atTime(8, 30).atZone(VN));
        CheckinLog phongKhac = luot(4, "CHECK_IN", "PENDING", THU_HAI.atTime(8, 40).atZone(VN));
        phongKhac.setUserId(9L);
        CheckinLog ngayKhac = luot(1, "CHECK_IN", "PENDING", THU_HAI.minusDays(3).atTime(8, 30).atZone(VN));
        // Kho trả mới → cũ như truy vấn thật
        when(checkinRepo.findByStatusOrderByCheckinTimeDesc("PENDING")).thenReturn(List.of(chieu, phongKhac, sang, ngayKhac));
        for (CheckinLog c : List.of(chieu, sang, phongKhac, ngayKhac)) {
            when(checkinRepo.findById(c.getId())).thenReturn(Optional.of(c));
        }

        int so = service.duyetTatCaDangCho(8L, null, THU_HAI.toString(), THU_HAI.toString());

        assertThat(so).isEqualTo(2);
        var thuTu = inOrder(checkinRepo);
        thuTu.verify(checkinRepo).findById(2L);
        thuTu.verify(checkinRepo).findById(3L);
        verify(checkinRepo, never()).findById(4L);
        verify(checkinRepo, never()).findById(1L);
        assertThat(sang.getStatus()).isEqualTo("APPROVED");
        assertThat(phongKhac.getStatus()).isEqualTo("PENDING");
    }
}
