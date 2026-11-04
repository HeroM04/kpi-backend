package com.trilong.kpibackend.modules.attendance.service;

import com.trilong.kpibackend.modules.attendance.entity.CheckinLog;
import com.trilong.kpibackend.modules.attendance.entity.LeaveRequest;
import com.trilong.kpibackend.modules.attendance.repository.CheckinLogRepository;
import com.trilong.kpibackend.modules.attendance.repository.LeaveRequestRepository;
import com.trilong.kpibackend.modules.user.entity.Department;
import com.trilong.kpibackend.modules.user.entity.User;
import com.trilong.kpibackend.modules.user.repository.UserRepository;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFFormulaEvaluator;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Báo cáo Excel chấm công tháng, bố cục "BẢNG CHI TIẾT CHẤM CÔNG" như file máy
 * chấm công: khối 62 dòng mỗi người, cột A–P, ô tổng là công thức.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AttendanceReportServiceTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    @Mock CheckinLogRepository checkinLogRepository;
    @Mock UserRepository userRepository;
    @Mock LeaveRequestRepository leaveRequestRepository;
    @InjectMocks AttendanceReportService service;

    private final DataFormatter hien = new DataFormatter();

    private User nguoi(long id, String ten) {
        return User.builder().id(id).fullName(ten).phoneNumber("09000000" + id).passwordHash("x")
                .role("SALE").status("ACTIVE").build();
    }

    private CheckinLog luot(long userId, String trangThai, int thang, int ngay, int gio, int phut, String loai) {
        CheckinLog l = new CheckinLog();
        l.setUserId(userId);
        l.setStatus(trangThai);
        l.setActionType(loai);
        l.setCheckinType("OFFICE");
        l.setCheckinTime(ZonedDateTime.of(2026, thang, ngay, gio, phut, 0, 0, VN));
        return l;
    }

    /** Máy chủ không tính sẵn công thức (Excel tự tính khi mở) — ở đây tính hộ như Excel. */
    private XSSFWorkbook xuat(int thang) throws Exception {
        XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(service.generateMonthlyReport(2026, thang)));
        XSSFFormulaEvaluator.evaluateAllFormulaCells(wb);
        return wb;
    }

    private String chu(Sheet sh, int dong, int cot) {
        var r = sh.getRow(dong);
        Cell c = r == null ? null : r.getCell(cot);
        if (c == null) return "";
        return c.getCellType() == CellType.FORMULA
                ? (c.getCachedFormulaResultType() == CellType.NUMERIC ? hien.formatRawCellContents(c.getNumericCellValue(), -1, "General") : c.getStringCellValue())
                : hien.formatCellValue(c);
    }

    @Test
    @DisplayName("Người có chấm công lên đầu, người không chấm (hoặc chỉ có lượt bị từ chối) xuống cuối; Admin không chấm thì bỏ")
    void coChamCongLenDau() throws Exception {
        User admin = nguoi(9, "Admin");
        admin.setRole("ADMIN");
        when(userRepository.findAll()).thenReturn(List.of(
                nguoi(1, "Khong Cham"), nguoi(2, "Co Cham B"), nguoi(3, "Bi Tu Choi"), nguoi(4, "Co Cham A"), admin));
        when(checkinLogRepository.findByCheckinTimeBetween(any(), any())).thenReturn(List.of(
                luot(4, "APPROVED", 9, 8, 8, 0, "CHECK_IN"), luot(4, "APPROVED", 9, 8, 17, 0, "CHECK_OUT"),
                luot(2, "PENDING", 9, 9, 8, 0, "CHECK_IN"),
                luot(3, "REJECTED", 9, 10, 8, 0, "CHECK_IN")));

        List<String> thuTu = new ArrayList<>();
        try (XSSFWorkbook wb = xuat(9)) {
            Sheet sh = wb.getSheet("Chi Tiet");
            for (int t = 0; sh.getRow(t) != null; t += AttendanceReportService.BUOC_KHOI) {
                assertThat(chu(sh, t, 0)).isEqualTo("BẢNG CHI TIẾT CHẤM CÔNG");
                String dau = chu(sh, t + 1, 0);
                thuTu.add(dau.substring(dau.indexOf("Tên nhân viên: ") + 15, dau.indexOf("   Bộ phận")));
            }
        }
        assertThat(thuTu).containsExactly("Co Cham A", "Co Cham B", "Bi Tu Choi", "Khong Cham");
    }

    @Test
    @DisplayName("Khối giống file máy chấm công: tiêu đề, dòng nhân viên, đầu bảng, 3 cặp Vào–Ra, chỗ ký tên, tổng công")
    void boCucNhuMayChamCong() throws Exception {
        User u = nguoi(7, "Tran Thi Huong");
        u.setDepartment(Department.builder().id(8L).name("Phòng Kinh Doanh 8").build());
        when(userRepository.findAll()).thenReturn(List.of(u));
        when(checkinLogRepository.findByCheckinTimeBetween(any(), any())).thenReturn(List.of(
                luot(7, "APPROVED", 10, 1, 8, 50, "CHECK_IN"), luot(7, "APPROVED", 10, 1, 12, 0, "CHECK_OUT"),   // 01/10 hai cặp
                luot(7, "APPROVED", 10, 1, 13, 30, "CHECK_IN"), luot(7, "APPROVED", 10, 1, 17, 0, "CHECK_OUT"),
                luot(7, "APPROVED", 10, 2, 8, 30, "CHECK_IN")));                                                 // 02/10 chỉ vào
        LeaveRequest p = new LeaveRequest();
        p.setUserId(7L);
        p.setStatus("APPROVED");
        p.setLeaveDate(LocalDate.of(2026, 10, 5));
        when(leaveRequestRepository.findByLeaveDateBetweenAndStatusIn(any(), any(), any())).thenReturn(List.of(p));

        try (XSSFWorkbook wb = xuat(10)) {
            Sheet sh = wb.getSheet("Chi Tiet");
            assertThat(chu(sh, 1, 0)).isEqualTo("Mã nhân viên: 090000007   Tên nhân viên: Tran Thi Huong   Bộ phận: Phòng Kinh Doanh 8");
            assertThat(List.of(chu(sh, 9, 0), chu(sh, 9, 1), chu(sh, 9, 2), chu(sh, 9, 8), chu(sh, 9, 10), chu(sh, 9, 11), chu(sh, 9, 15)))
                    .containsExactly("Ngày", "Thứ", "1", "Trễ", "Công", "T.Giờ", "Nơi làm việc");
            assertThat(List.of(chu(sh, 10, 2), chu(sh, 10, 3))).containsExactly("Vào", "Ra");
            assertThat(List.of(chu(sh, 6, 1), chu(sh, 6, 6))).containsExactly("V", "P");
            assertThat(chu(sh, 7, 6)).isEqualTo("1");                                   // 1 ngày nghỉ có phép

            // 01/10 (dòng 12): 08:50–12:00 và 13:30–17:00, trễ 5 phút, ra trước 17:30 là 30 phút
            assertThat(List.of(chu(sh, 11, 1), chu(sh, 11, 2), chu(sh, 11, 3), chu(sh, 11, 4), chu(sh, 11, 5)))
                    .containsExactly("Năm", "08:50", "12:00", "13:30", "17:00");
            assertThat(List.of(chu(sh, 11, 8), chu(sh, 11, 9), chu(sh, 11, 10), chu(sh, 11, 15)))
                    .containsExactly("5", "30", "1", "App");
            // 02/10 chỉ có giờ vào → 0,5 công
            assertThat(chu(sh, 12, 10)).isEqualTo("0.5");
            // Ô TỔNG và dòng "Tổng công" dưới chữ ký là công thức cộng cột Công
            assertThat(sh.getRow(4).getCell(2).getCellFormula()).isEqualTo("SUM(K12:K42)");
            assertThat(chu(sh, 4, 2)).isEqualTo("1.5");
            assertThat(chu(sh, 43, 9)).isEqualTo("Kí tên");
            assertThat(chu(sh, 46, 9)).isEqualTo("Tran Thi Huong");
            assertThat(chu(sh, 47, 9)).isEqualTo("Tổng công: 1.5");
            // Ô gộp đúng như file máy (+1 ô gộp cho dòng Tổng công)
            List<String> gop = sh.getMergedRegions().stream().map(CellRangeAddress::formatAsString).toList();
            assertThat(gop).contains("A1:P1", "A2:P2", "A3:B3", "E3:E5", "J3:L3", "N3:P3", "A6:A8", "B6:O6", "P6:P8",
                    "A9:P9", "A10:A11", "C10:D10", "P10:P11", "J44:M44", "J47:M47", "J48:M48");
            assertThat(gop).hasSize(37);
            // Chủ nhật 04/10 tô nền xám
            assertThat(chu(sh, 14, 1)).isEqualTo("CN");
            assertThat(sh.getRow(14).getCell(0).getCellStyle().getFillPattern().name()).isEqualTo("SOLID_FOREGROUND");
        }
    }

    @Test
    @DisplayName("Thứ Bảy tối đa 0,5 công dù ở lại cả ngày; Chủ nhật không tính công; dưới 2 giờ vẫn 0")
    void thuBayToiDaNuaCong() {
        LocalDate thuBay = LocalDate.of(2026, 10, 3);
        LocalTime moc = LocalTime.of(8, 45);
        assertThat(AttendanceReportService.tinhNgay(thuBay, List.of(
                luot(1, "APPROVED", 10, 3, 8, 30, "CHECK_IN"), luot(1, "APPROVED", 10, 3, 17, 30, "CHECK_OUT")), moc).cong())
                .isEqualTo(0.5);
        assertThat(AttendanceReportService.tinhNgay(thuBay, List.of(
                luot(1, "APPROVED", 10, 3, 8, 30, "CHECK_IN"), luot(1, "APPROVED", 10, 3, 11, 50, "CHECK_OUT")), moc).cong())
                .isEqualTo(0.5);
        assertThat(AttendanceReportService.tinhNgay(thuBay, List.of(luot(1, "APPROVED", 10, 3, 8, 30, "CHECK_IN")), moc).cong())
                .isEqualTo(0.5);
        assertThat(AttendanceReportService.tinhNgay(thuBay, List.of(
                luot(1, "APPROVED", 10, 3, 8, 30, "CHECK_IN"), luot(1, "APPROVED", 10, 3, 9, 30, "CHECK_OUT")), moc).cong())
                .isZero();
        // Chủ nhật không tính công dù có chấm, giờ vào/ra vẫn ghi
        var chuNhat = AttendanceReportService.tinhNgay(thuBay.plusDays(1), List.of(
                luot(1, "APPROVED", 10, 4, 8, 30, "CHECK_IN"), luot(1, "APPROVED", 10, 4, 17, 30, "CHECK_OUT")), moc);
        assertThat(chuNhat.cong()).isZero();
        assertThat(chuNhat.cap().get(0)).containsExactly(LocalTime.of(8, 30), LocalTime.of(17, 30));
        assertThat(AttendanceReportService.tinhNgay(thuBay.plusDays(1), List.of(
                luot(1, "APPROVED", 10, 4, 9, 0, "CHECK_IN")), moc).cong()).isZero();
        // Ngày thường cả ngày vẫn 1 công
        assertThat(AttendanceReportService.tinhNgay(thuBay.plusDays(2), List.of(
                luot(1, "APPROVED", 10, 5, 8, 30, "CHECK_IN"), luot(1, "APPROVED", 10, 5, 17, 30, "CHECK_OUT")), moc).cong())
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("Công một ngày: chỉ vào hoặc chỉ ra 0,5; đủ vào–ra theo số giờ; lượt bị từ chối bỏ qua; chờ duyệt ghi rõ")
    void congMotNgay() {
        LocalDate thuHai = LocalDate.of(2026, 10, 5);
        LocalTime moc = LocalTime.of(8, 45);
        assertThat(AttendanceReportService.tinhNgay(thuHai, List.of(luot(1, "APPROVED", 10, 5, 8, 0, "CHECK_IN")), moc).cong())
                .isEqualTo(0.5);
        assertThat(AttendanceReportService.tinhNgay(thuHai, List.of(luot(1, "APPROVED", 10, 5, 17, 0, "CHECK_OUT")), moc).cong())
                .isEqualTo(0.5);
        var du = AttendanceReportService.tinhNgay(thuHai, List.of(
                luot(1, "APPROVED", 10, 5, 8, 0, "CHECK_IN"), luot(1, "APPROVED", 10, 5, 20, 30, "CHECK_OUT")), moc);
        assertThat(du.cong()).isEqualTo(1.0);
        assertThat(du.gio()).isEqualTo(11.0);                                           // 12,5 giờ trừ 1,5 giờ nghỉ trưa
        assertThat(du.tre()).isZero();
        assertThat(du.tangCa()).isEqualTo(2.5);                                         // 18:00 → 20:30
        assertThat(AttendanceReportService.tinhNgay(thuHai, List.of(
                luot(1, "APPROVED", 10, 5, 8, 0, "CHECK_IN"), luot(1, "APPROVED", 10, 5, 9, 30, "CHECK_OUT")), moc).cong())
                .isZero();                                                              // dưới 2 giờ
        assertThat(AttendanceReportService.tinhNgay(thuHai, List.of(luot(1, "REJECTED", 10, 5, 8, 0, "CHECK_IN")), moc))
                .isNull();
        var cho = AttendanceReportService.tinhNgay(thuHai, List.of(luot(1, "PENDING", 10, 5, 9, 0, "CHECK_IN")), moc);
        assertThat(cho.noiLam()).isEqualTo("Chờ duyệt");
        assertThat(cho.tre()).isEqualTo(15);
        // Người được châm chước tới 09:00 thì 09:00 chưa trễ
        assertThat(AttendanceReportService.tinhNgay(thuHai, List.of(luot(1, "APPROVED", 10, 5, 9, 0, "CHECK_IN")),
                LocalTime.of(9, 0)).tre()).isZero();
    }
}
