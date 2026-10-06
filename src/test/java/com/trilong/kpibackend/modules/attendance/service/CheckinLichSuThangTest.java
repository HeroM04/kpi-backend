package com.trilong.kpibackend.modules.attendance.service;

import com.trilong.kpibackend.modules.attendance.entity.CheckinLog;
import com.trilong.kpibackend.modules.attendance.repository.CheckinLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/** Tab Lịch sử trên app, chế độ "Theo tháng": đúng khoảng ngày giờ VN, xếp sớm → muộn. */
@ExtendWith(MockitoExtension.class)
class CheckinLichSuThangTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    @Mock CheckinLogRepository checkinLogRepository;
    @InjectMocks CheckinService service;

    private CheckinLog luot(long id, int ngay, int gio) {
        CheckinLog l = new CheckinLog();
        l.setId(id);
        l.setUserId(7L);
        l.setCheckinTime(ZonedDateTime.of(2026, 10, ngay, gio, 0, 0, 0, VN));
        return l;
    }

    @Test
    @DisplayName("Lấy từ 00:00 ngày 1 đến 00:00 ngày 1 tháng sau (giờ VN), trả về theo thời gian tăng dần")
    void khoangThangVaThuTu() {
        ZonedDateTime tu = ZonedDateTime.of(2026, 10, 1, 0, 0, 0, 0, VN);
        ZonedDateTime den = ZonedDateTime.of(2026, 11, 1, 0, 0, 0, 0, VN);
        when(checkinLogRepository.findByUserIdAndCheckinTimeBetween(eq(7L), eq(tu), eq(den)))
                .thenReturn(List.of(luot(3, 5, 17), luot(1, 2, 8), luot(2, 5, 8)));

        List<CheckinLog> kq = service.getCheckinsByUserIdAndMonth(7L, YearMonth.of(2026, 10));

        assertThat(kq).extracting(CheckinLog::getId).containsExactly(1L, 2L, 3L);
    }
}
