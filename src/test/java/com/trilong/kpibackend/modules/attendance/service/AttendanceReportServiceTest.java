package com.trilong.kpibackend.modules.attendance.service;

import com.trilong.kpibackend.modules.attendance.entity.CheckinLog;
import com.trilong.kpibackend.modules.attendance.repository.CheckinLogRepository;
import com.trilong.kpibackend.modules.user.entity.User;
import com.trilong.kpibackend.modules.user.repository.UserRepository;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Báo cáo Excel chấm công tháng: ai có chấm công nằm trên, ai không có nằm dưới. */
@ExtendWith(MockitoExtension.class)
class AttendanceReportServiceTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    @Mock CheckinLogRepository checkinLogRepository;
    @Mock UserRepository userRepository;
    @InjectMocks AttendanceReportService service;

    private User nguoi(long id, String ten) {
        return User.builder().id(id).fullName(ten).phoneNumber("09000000" + id).passwordHash("x").role("SALE").build();
    }

    private CheckinLog luot(long userId, String trangThai, int ngay, int gio, String loai) {
        CheckinLog l = new CheckinLog();
        l.setUserId(userId);
        l.setStatus(trangThai);
        l.setActionType(loai);
        l.setCheckinTime(ZonedDateTime.of(2026, 9, ngay, gio, 0, 0, 0, VN));
        return l;
    }

    @Test
    @DisplayName("Người có chấm công lên đầu, người không chấm (hoặc chỉ có lượt bị từ chối) xuống cuối sau một dòng ngăn")
    void coChamCongLenDau() throws Exception {
        when(userRepository.findAll()).thenReturn(List.of(
                nguoi(1, "Khong Cham"), nguoi(2, "Co Cham B"), nguoi(3, "Bi Tu Choi"), nguoi(4, "Co Cham A")));
        when(checkinLogRepository.findByCheckinTimeBetween(any(), any())).thenReturn(List.of(
                luot(4, "APPROVED", 8, 8, "CHECK_IN"), luot(4, "APPROVED", 8, 17, "CHECK_OUT"),
                luot(2, "PENDING", 9, 8, "CHECK_IN"),
                luot(3, "REJECTED", 10, 8, "CHECK_IN")));

        byte[] file = service.generateMonthlyReport(2026, 9);

        List<String> thuTu = new ArrayList<>();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(file))) {
            Sheet sheet = wb.getSheetAt(0);
            for (Row row : sheet) {
                Cell c0 = row.getCell(0);
                if (c0 != null && c0.getStringCellValue().startsWith("NHÂN SỰ KHÔNG CÓ CHẤM CÔNG")) {
                    thuTu.add("--- " + c0.getStringCellValue());
                }
                Cell c2 = row.getCell(2);
                if (c2 != null && c2.getStringCellValue().startsWith("Tên nhân viên: ")) {
                    thuTu.add(c2.getStringCellValue().substring("Tên nhân viên: ".length()));
                }
            }
        }

        assertThat(thuTu).containsExactly(
                "Co Cham B", "Co Cham A",
                "--- NHÂN SỰ KHÔNG CÓ CHẤM CÔNG TRONG THÁNG (2 người)",
                "Khong Cham", "Bi Tu Choi");
    }

    @Test
    @DisplayName("Cả công ty đều có chấm công thì không có dòng ngăn")
    void khongCoDongNganKhiAiCungCham() throws Exception {
        when(userRepository.findAll()).thenReturn(List.of(nguoi(1, "A"), nguoi(2, "B")));
        when(checkinLogRepository.findByCheckinTimeBetween(any(), any())).thenReturn(List.of(
                luot(1, "APPROVED", 8, 8, "CHECK_IN"), luot(2, "APPROVED", 8, 8, "CHECK_IN")));

        byte[] file = service.generateMonthlyReport(2026, 9);

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(file))) {
            for (Row row : wb.getSheetAt(0)) {
                Cell c0 = row.getCell(0);
                if (c0 != null) assertThat(c0.getStringCellValue()).doesNotStartWith("NHÂN SỰ KHÔNG CÓ CHẤM CÔNG");
            }
        }
    }
}
