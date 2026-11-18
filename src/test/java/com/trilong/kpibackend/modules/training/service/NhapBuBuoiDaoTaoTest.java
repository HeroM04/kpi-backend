package com.trilong.kpibackend.modules.training.service;

import com.trilong.kpibackend.modules.kpi.repository.KpiAutoGrantRepository;
import com.trilong.kpibackend.modules.kpi.repository.KpiLedgerEntryRepository;
import com.trilong.kpibackend.modules.kpi.repository.KpiWeeklyScoreRepository;
import com.trilong.kpibackend.modules.kpi.service.KpiCalculationService;
import com.trilong.kpibackend.modules.notification.service.PushNotificationService;
import com.trilong.kpibackend.modules.training.dto.NhapBuBuoiDaoTaoDTO;
import com.trilong.kpibackend.modules.training.entity.TrainingAttendee;
import com.trilong.kpibackend.modules.training.entity.TrainingSession;
import com.trilong.kpibackend.modules.training.repository.TrainingAttendeeRepository;
import com.trilong.kpibackend.modules.training.repository.TrainingRsvpRepository;
import com.trilong.kpibackend.modules.training.repository.TrainingSessionRepository;
import com.trilong.kpibackend.modules.user.entity.User;
import com.trilong.kpibackend.modules.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Nhập bù buổi đào tạo đã diễn ra ngoài app (tuần 46/2026: có học nhưng không lên
 * lịch, hệ thống cộng mặc định 15đ cho cả công ty).
 *
 * <p>Phải lưu buổi + người dự trong một lần, không chấm điểm giữa chừng, không báo
 * "Có buổi đào tạo mới" — nếu không, lúc vừa tạo buổi cả công ty bị trừ điểm và
 * nhận thông báo, người đã dự phải chờ được điểm danh tay mới được trả lại.
 */
class NhapBuBuoiDaoTaoTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private TrainingSessionRepository buoiRepo;
    private TrainingAttendeeRepository duRepo;
    private UserRepository userRepo;
    private KpiCalculationService kpi;
    private PushNotificationService push;
    private TrainingService service;

    @BeforeEach
    void setUp() {
        buoiRepo = mock(TrainingSessionRepository.class);
        duRepo = mock(TrainingAttendeeRepository.class);
        userRepo = mock(UserRepository.class);
        kpi = mock(KpiCalculationService.class);
        push = mock(PushNotificationService.class);
        service = new TrainingService(buoiRepo, duRepo, mock(TrainingRsvpRepository.class),
                mock(KpiAutoGrantRepository.class), mock(KpiLedgerEntryRepository.class),
                mock(KpiWeeklyScoreRepository.class), userRepo, kpi, push);

        when(buoiRepo.findByRoomCode(anyString())).thenReturn(Optional.empty());
        when(buoiRepo.save(any(TrainingSession.class))).thenAnswer(i -> {
            TrainingSession s = i.getArgument(0);
            s.setId(46L);
            return s;
        });
        when(userRepo.findAllById(any())).thenAnswer(i -> {
            Iterable<Long> ids = i.getArgument(0);
            List<User> ds = new java.util.ArrayList<>();
            for (Long id : ids) {
                if (id < 1000) ds.add(User.builder().id(id).fullName("NV " + id).build());
            }
            return ds;
        });
    }

    private NhapBuBuoiDaoTaoDTO buoiTuan46(Long... nguoi) {
        NhapBuBuoiDaoTaoDTO dto = new NhapBuBuoiDaoTaoDTO();
        dto.setTitle("Kỹ năng chốt sale");
        dto.setRoomCode("ROOM-4646");
        dto.setStartTime(ZonedDateTime.of(2026, 11, 11, 9, 0, 0, 0, VN));
        dto.setDurationMinutes(120);
        dto.setMaxSlots(20);
        dto.setNguoiDaDu(Arrays.asList(nguoi));
        return dto;
    }

    @Test
    @DisplayName("Lưu buổi đã kết thúc + điểm danh đúng giờ buổi học, không chấm điểm, không báo buổi mới")
    void luuMotLan() {
        var kq = service.nhapBuBuoiDaDienRa(buoiTuan46(7L, 8L, 9L));

        assertEquals(3, kq.soNguoiDu());
        assertEquals("COMPLETED", kq.buoi().getStatus());

        ArgumentCaptor<TrainingAttendee> du = ArgumentCaptor.forClass(TrainingAttendee.class);
        verify(duRepo, times(3)).save(du.capture());
        assertEquals(List.of(7L, 8L, 9L), du.getAllValues().stream().map(TrainingAttendee::getUserId).toList());
        du.getAllValues().forEach(a -> assertEquals(kq.buoi().getStartTime(), a.getAttendedAt(),
                "giờ điểm danh = giờ buổi học, để rơi đúng tuần 46"));

        verifyNoInteractions(kpi);    // chấm điểm để nơi gọi chạy nền sau khi lưu
        verifyNoInteractions(push);   // không báo "Có buổi đào tạo mới"
    }

    @Test
    @DisplayName("Danh sách đông hơn số chỗ → nới số chỗ, không chặn")
    void noiSoCho() {
        Long[] ds = new Long[35];
        for (int i = 0; i < ds.length; i++) ds[i] = (long) (i + 1);
        var kq = service.nhapBuBuoiDaDienRa(buoiTuan46(ds));
        assertEquals(35, kq.buoi().getMaxSlots());
    }

    @Test
    @DisplayName("Chọn trùng một người hai lần → chỉ điểm danh một lần")
    void boTrung() {
        var kq = service.nhapBuBuoiDaDienRa(buoiTuan46(7L, 7L, null, 8L));
        assertEquals(2, kq.soNguoiDu());
        verify(duRepo, times(2)).save(any(TrainingAttendee.class));
    }

    @Test
    @DisplayName("Buổi chưa kết thúc → từ chối, bảo dùng Thêm buổi bình thường")
    void buoiChuaKetThuc() {
        NhapBuBuoiDaoTaoDTO dto = buoiTuan46(7L);
        dto.setStartTime(ZonedDateTime.now(VN).minusMinutes(30));
        var loi = assertThrows(IllegalArgumentException.class, () -> service.nhapBuBuoiDaDienRa(dto));
        assertTrue(loi.getMessage().contains("chưa kết thúc"));
        verify(buoiRepo, never()).save(any());
    }

    @Test
    @DisplayName("Mã nhân sự không tồn tại → báo rõ, không lưu gì")
    void nguoiKhongTonTai() {
        var loi = assertThrows(IllegalArgumentException.class,
                () -> service.nhapBuBuoiDaDienRa(buoiTuan46(7L, 5000L)));
        assertTrue(loi.getMessage().contains("5000"));
        verify(buoiRepo, never()).save(any());
    }

    @Test
    @DisplayName("Tạo buổi bình thường nhưng ngày đã qua → vẫn không báo 'Có buổi đào tạo mới'")
    void taoBuoiDaQuaKhongBao() {
        TrainingSession s = TrainingSession.builder()
                .startTime(ZonedDateTime.now(VN).minusDays(3)).durationMinutes(120).build();
        assertTrue(TrainingService.daKetThuc(s, ZonedDateTime.now(VN)));
        TrainingSession sapToi = TrainingSession.builder()
                .startTime(ZonedDateTime.now(VN).plusHours(1)).durationMinutes(120).build();
        assertFalse(TrainingService.daKetThuc(sapToi, ZonedDateTime.now(VN)));
    }
}
