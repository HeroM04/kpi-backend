package com.trilong.kpibackend.modules.training.service;

import com.trilong.kpibackend.modules.kpi.repository.KpiAutoGrantRepository;
import com.trilong.kpibackend.modules.kpi.repository.KpiLedgerEntryRepository;
import com.trilong.kpibackend.modules.kpi.repository.KpiWeeklyScoreRepository;
import com.trilong.kpibackend.modules.kpi.service.KpiCalculationService;
import com.trilong.kpibackend.modules.notification.service.PushNotificationService;
import com.trilong.kpibackend.modules.training.dto.TrainingRsvpResponseDTO;
import com.trilong.kpibackend.modules.training.entity.TrainingRsvp;
import com.trilong.kpibackend.modules.training.entity.TrainingSession;
import com.trilong.kpibackend.modules.training.repository.TrainingAttendeeRepository;
import com.trilong.kpibackend.modules.training.repository.TrainingRsvpRepository;
import com.trilong.kpibackend.modules.training.repository.TrainingSessionRepository;
import com.trilong.kpibackend.modules.user.entity.User;
import com.trilong.kpibackend.modules.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Xem lại đơn xin vắng đào tạo đã duyệt / đã từ chối trên web. */
@ExtendWith(MockitoExtension.class)
class DonXinVangTheoTrangThaiTest {

    @Mock TrainingSessionRepository trainingSessionRepository;
    @Mock TrainingAttendeeRepository trainingAttendeeRepository;
    @Mock TrainingRsvpRepository trainingRsvpRepository;
    @Mock KpiAutoGrantRepository kpiAutoGrantRepository;
    @Mock KpiLedgerEntryRepository kpiLedgerEntryRepository;
    @Mock KpiWeeklyScoreRepository kpiWeeklyScoreRepository;
    @Mock UserRepository userRepository;
    @Mock KpiCalculationService kpiCalculationService;
    @Mock PushNotificationService pushNotificationService;

    @InjectMocks TrainingService service;

    private TrainingRsvp don(long id, String status, Long nguoiDuyet) {
        TrainingRsvp r = new TrainingRsvp();
        r.setId(id);
        r.setSessionId(3L);
        r.setUserId(7L);
        r.setChoice("DECLINE");
        r.setReason("Không bán dự án này");
        r.setStatus(status);
        r.setReviewedBy(nguoiDuyet);
        r.setCreatedAt(ZonedDateTime.now());
        return r;
    }

    private User nguoi(long id, String ten) {
        User u = new User();
        u.setId(id);
        u.setFullName(ten);
        return u;
    }

    @Test
    @DisplayName("Chọn Đã duyệt → chỉ lấy đơn APPROVED, kèm tên nhân sự, tên buổi và người duyệt")
    void daDuyet() {
        when(trainingRsvpRepository.donXinVangTheoTrangThai("APPROVED"))
                .thenReturn(List.of(don(1, "APPROVED", 1L)));
        when(userRepository.findAllById(anyIterable()))
                .thenReturn(List.of(nguoi(7, "Bùi Thị Linh")))
                .thenReturn(List.of(nguoi(1, "Admin Hùng")));
        TrainingSession buoi = new TrainingSession();
        buoi.setId(3L);
        buoi.setTitle("Đào tạo chuyên sâu");
        when(trainingSessionRepository.findAllById(anyIterable())).thenReturn(List.of(buoi));

        List<TrainingRsvpResponseDTO> kq = service.donXinVangDayDu("APPROVED");

        assertEquals(1, kq.size());
        assertEquals("APPROVED", kq.get(0).getStatus());
        assertEquals("Bùi Thị Linh", kq.get(0).getUserFullName());
        assertEquals("Đào tạo chuyên sâu", kq.get(0).getSessionTitle());
        assertEquals("Admin Hùng", kq.get(0).getReviewedByFullName());
        verify(trainingRsvpRepository, never()).tatCaDonXinVang();
    }

    @Test
    @DisplayName("Viết thường, thừa khoảng trắng vẫn hiểu")
    void vietThuong() {
        when(trainingRsvpRepository.donXinVangTheoTrangThai("REJECTED")).thenReturn(List.of());
        assertTrue(service.donXinVangDayDu(" rejected ").isEmpty());
        verify(trainingRsvpRepository).donXinVangTheoTrangThai("REJECTED");
    }

    @Test
    @DisplayName("Bỏ trống hoặc ALL → mọi đơn xin vắng")
    void tatCa() {
        when(trainingRsvpRepository.tatCaDonXinVang()).thenReturn(List.of());
        service.donXinVangDayDu(null);
        service.donXinVangDayDu("");
        service.donXinVangDayDu("ALL");
        verify(trainingRsvpRepository, times(3)).tatCaDonXinVang();
        verify(trainingRsvpRepository, never()).donXinVangTheoTrangThai(anyString());
    }

    @Test
    @DisplayName("Trạng thái lạ → báo lỗi, không truy vấn")
    void trangThaiLa() {
        assertThrows(IllegalArgumentException.class, () -> service.donXinVangDayDu("DONE"));
        verifyNoInteractions(trainingRsvpRepository);
    }
}
