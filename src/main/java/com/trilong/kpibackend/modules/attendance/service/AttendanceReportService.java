package com.trilong.kpibackend.modules.attendance.service;

import com.trilong.kpibackend.modules.attendance.entity.CheckinLog;
import com.trilong.kpibackend.modules.attendance.entity.LeaveRequest;
import com.trilong.kpibackend.modules.attendance.repository.CheckinLogRepository;
import com.trilong.kpibackend.modules.attendance.repository.LeaveRequestRepository;
import com.trilong.kpibackend.modules.user.entity.User;
import com.trilong.kpibackend.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFormulaEvaluator;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Báo cáo chấm công tháng — "BẢNG CHI TIẾT CHẤM CÔNG", bố cục y hệt file xuất
 * từ máy chấm công (Wise Eye).
 *
 * <p>Một sheet "Chi Tiet", mỗi nhân sự một khối cách nhau {@link #BUOC_KHOI}
 * dòng: tiêu đề, dòng "Mã nhân viên / Tên nhân viên / Bộ phận" nền vàng, ô tổng
 * hợp (ngày thường, cuối tuần, tăng ca, đi trễ, về sớm), các loại vắng, rồi
 * bảng từng ngày với 3 cặp Vào–Ra, Trễ, Sớm, Công, T.Giờ, T.Ca1–3, Nơi làm
 * việc, cuối khối là chỗ ký tên. Cùng cột, cùng ô gộp, cùng phông, cùng bước
 * dòng nên kế toán đọc quen mắt và chép nguyên khối gộp chung với file máy chấm
 * công được (kể cả chạy cùng script tính công trên Google Sheets).
 *
 * <p>Số liệu là của hệ thống KPI, không phải của máy chấm công:
 * <ul>
 *   <li>Mã nhân viên là số điện thoại đăng nhập — không trùng mã máy chấm công
 *       (dạng 00738) nên gộp chung không lẫn người.</li>
 *   <li>Công: đủ vào–ra thì theo số giờ (từ 5 giờ = 1, từ 2 giờ = 0,5, dưới 2
 *       giờ = 0, trừ 1,5 giờ nghỉ trưa nếu làm qua trưa); chỉ có giờ vào hoặc chỉ
 *       có giờ ra = 0,5. Lượt bị từ chối không tính; lượt đang chờ duyệt vẫn
 *       tính và cột Nơi làm việc ghi "Chờ duyệt".</li>
 *   <li>Trễ: số phút vào sau mốc đúng giờ (08:45, người được châm chước 09:00;
 *       vào buổi chiều so với 13:45). Sớm: số phút ra trước 17:30, thứ Hai–Sáu.
 *       T.Ca1: số giờ làm sau 18:00, thứ Hai–Sáu.</li>
 *   <li>Các loại vắng: V = vắng không phép, P = nghỉ có phép, theo đơn xin vắng
 *       và chốt ngày của hệ thống.</li>
 * </ul>
 * Các ô tổng là công thức trên cột ngày nên sửa công ngày nào thì tổng tự đổi.
 */
@Service
@RequiredArgsConstructor
public class AttendanceReportService {

    /** Công của một ngày chỉ có giờ vào hoặc chỉ có giờ ra. */
    static final double CONG_THIEU_MOT_LUOT = 0.5;

    /** Mỗi khối nhân sự cách nhau 62 dòng — đúng bước của file máy chấm công. */
    static final int BUOC_KHOI = 62;

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int SO_COT = 16;                       // A..P
    private static final LocalTime TRUA = LocalTime.of(12, 0);
    private static final LocalTime HET_NGHI_TRUA = LocalTime.of(13, 30);
    private static final LocalTime MOC_SANG = LocalTime.of(8, 45);
    private static final LocalTime MOC_SANG_CHAM_CHUOC = LocalTime.of(9, 0);
    private static final LocalTime MOC_CHIEU = LocalTime.of(13, 45);
    private static final LocalTime HET_GIO = LocalTime.of(17, 30);
    private static final LocalTime BAT_DAU_LAM_THEM = LocalTime.of(18, 0);

    private static final String NEN_VANG = "FFFF00";
    private static final String NEN_CHU_NHAT = "C0C0C0";

    private final CheckinLogRepository checkinLogRepository;
    private final UserRepository userRepository;
    private final LeaveRequestRepository leaveRequestRepository;

    public byte[] generateMonthlyReport(int year, int month) throws IOException {
        YearMonth thang = YearMonth.of(year, month);
        LocalDate dau = thang.atDay(1), cuoi = thang.atEndOfMonth();

        List<CheckinLog> logs = checkinLogRepository.findByCheckinTimeBetween(
                dau.atStartOfDay(VN), cuoi.plusDays(1).atStartOfDay(VN));
        Set<Long> coChamCong = nguoiCoChamCong(logs);
        List<User> users = xepNguoiCoChamCongTruoc(userRepository.findAll().stream()
                .filter(u -> coChamCong.contains(u.getId())
                        || ("ACTIVE".equals(u.getStatus()) && !"ADMIN".equals(u.getRole())))
                .toList(), coChamCong);

        Map<Long, Map<LocalDate, List<CheckinLog>>> theoNguoiNgay = logs.stream()
                .filter(l -> l.getUserId() != null && l.getCheckinTime() != null)
                .collect(Collectors.groupingBy(CheckinLog::getUserId,
                        Collectors.groupingBy(l -> l.getCheckinTime().withZoneSameInstant(VN).toLocalDate())));
        Map<Long, Map<String, Long>> vang = new HashMap<>();
        for (LeaveRequest r : leaveRequestRepository.findByLeaveDateBetweenAndStatusIn(
                dau, cuoi, List.of("APPROVED", "UNEXCUSED"))) {
            vang.computeIfAbsent(r.getUserId(), k -> new HashMap<>()).merge(r.getStatus(), 1L, Long::sum);
        }

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            BoKieu kieu = new BoKieu(wb);
            Sheet sheet = wb.createSheet("Chi Tiet");
            dungTrang(sheet);

            int khoi = 0;
            for (User u : users) {
                veKhoi(sheet, kieu, BUOC_KHOI * khoi++, u, thang,
                        theoNguoiNgay.getOrDefault(u.getId(), Map.of()), vang.getOrDefault(u.getId(), Map.of()));
            }

            // Lưu sẵn kết quả công thức để trình xem trước / Google Sheets đọc ra số ngay
            XSSFFormulaEvaluator.evaluateAllFormulaCells(wb);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    // ── Một khối nhân sự ────────────────────────────────────────────────────

    private void veKhoi(Sheet sh, BoKieu k, int t, User u, YearMonth thang,
                        Map<LocalDate, List<CheckinLog>> luotTheoNgay, Map<String, Long> vang) {
        int soNgay = thang.lengthOfMonth();
        int dongDau = t + 11, dongCuoi = dongDau + soNgay - 1;          // chỉ số 0
        String tu = String.valueOf(dongDau + 1), den = String.valueOf(dongCuoi + 1);
        java.util.function.Function<String, String> cot = c -> c + tu + ":" + c + den;

        for (int r = t; r <= dongCuoi + 6; r++) sh.createRow(r).setHeightInPoints(12.75f);
        sh.getRow(t).setHeightInPoints(21f);

        Kieu tieuDe = Kieu.CO_BAN.co(14).chuDam().giua();
        Kieu nhan = Kieu.CO_BAN.chuDam().chuNghieng();
        Kieu dauBang = Kieu.CO_BAN.chuDam().giua().ngatDong();

        o(sh, k, t, t, 0, 15, tieuDe, "BẢNG CHI TIẾT CHẤM CÔNG");
        String ma = u.getPhoneNumber() != null ? u.getPhoneNumber() : String.valueOf(u.getId());
        String phong = u.getDepartment() != null ? u.getDepartment().getName() : "------";
        o(sh, k, t + 1, t + 1, 0, 15, Kieu.CO_BAN.chuDam().nen(NEN_VANG),
                "Mã nhân viên: " + ma + "   Tên nhân viên: " + u.getFullName() + "   Bộ phận: " + phong);

        // Ô tổng hợp — đều là công thức trên cột ngày
        String[] dongTong = {"Ngày thường", "Cuối tuần", "TỔNG"};
        String[][] cong = {
                {"SUMIF(" + cot.apply("B") + ",\"<>CN\"," + cot.apply("K") + ")", "SUMIF(" + cot.apply("B") + ",\"<>CN\"," + cot.apply("L") + ")"},
                {"SUMIF(" + cot.apply("B") + ",\"CN\"," + cot.apply("K") + ")", "SUMIF(" + cot.apply("B") + ",\"CN\"," + cot.apply("L") + ")"},
                {"SUM(" + cot.apply("K") + ")", "SUM(" + cot.apply("L") + ")"}};
        for (int i = 0; i < 3; i++) {
            int r = t + 2 + i;
            o(sh, k, r, r, 0, 1, nhan, dongTong[i]);
            o(sh, k, r, r, 2, 2, nhan, "=" + cong[i][0]);
            o(sh, k, r, r, 3, 3, nhan, "=" + cong[i][1]);
            o(sh, k, r, r, 5, 6, nhan, "Tăng ca " + (i + 1));
            o(sh, k, r, r, 7, 7, nhan, "=SUM(" + cot.apply(String.valueOf((char) ('M' + i))) + ")");
        }
        o(sh, k, t + 2, t + 4, 4, 4, nhan, null);
        o(sh, k, t + 2, t + 4, 8, 8, nhan, null);
        o(sh, k, t + 2, t + 4, 12, 12, nhan, null);
        o(sh, k, t + 2, t + 2, 9, 11, nhan, "Đi trễ");
        o(sh, k, t + 3, t + 3, 9, 10, nhan, "Số lần");
        o(sh, k, t + 3, t + 3, 11, 11, nhan, "=COUNTIF(" + cot.apply("I") + ",\">0\")");
        o(sh, k, t + 4, t + 4, 9, 10, nhan, "Số phút");
        o(sh, k, t + 4, t + 4, 11, 11, nhan, "=SUM(" + cot.apply("I") + ")");
        o(sh, k, t + 2, t + 2, 13, 15, nhan, "Về sớm");
        o(sh, k, t + 3, t + 3, 13, 14, nhan, "Số lần");
        o(sh, k, t + 3, t + 3, 15, 15, nhan, "=COUNTIF(" + cot.apply("J") + ",\">0\")");
        o(sh, k, t + 4, t + 4, 13, 14, nhan, "Số phút");
        o(sh, k, t + 4, t + 4, 15, 15, nhan, "=SUM(" + cot.apply("J") + ")");

        // Các loại vắng
        o(sh, k, t + 5, t + 7, 0, 0, nhan, null);
        o(sh, k, t + 5, t + 5, 1, 14, Kieu.CO_BAN.chuNghieng().giua(), "Các loại vắng");
        o(sh, k, t + 5, t + 7, 15, 15, dauBang, null);
        String[] loaiVang = {"V", "OM", "TS", "R", "Ro", "P", "F", "CO", "CD", "H", "CT", "Le", null, null};
        for (int c = 1; c <= 14; c++) {
            String ma1 = loaiVang[c - 1];
            o(sh, k, t + 6, t + 6, c, c, dauBang, ma1);
            Object soNgayVang = ma1 == null ? null
                    : "V".equals(ma1) ? vang.getOrDefault("UNEXCUSED", 0L)
                    : "P".equals(ma1) ? vang.getOrDefault("APPROVED", 0L) : 0L;
            o(sh, k, t + 7, t + 7, c, c, dauBang, soNgayVang);
        }

        // Bảng từng ngày
        o(sh, k, t + 8, t + 8, 0, 15, Kieu.CO_BAN.chuNghieng().giua(), "Chi tiết");
        o(sh, k, t + 9, t + 10, 0, 0, dauBang, "Ngày");
        o(sh, k, t + 9, t + 10, 1, 1, dauBang, "Thứ");
        for (int i = 0; i < 3; i++) {
            o(sh, k, t + 9, t + 9, 2 + 2 * i, 3 + 2 * i, dauBang, String.valueOf(i + 1));
            o(sh, k, t + 10, t + 10, 2 + 2 * i, 2 + 2 * i, dauBang, "Vào");
            o(sh, k, t + 10, t + 10, 3 + 2 * i, 3 + 2 * i, dauBang, "Ra");
        }
        String[] dauCot = {"Trễ", "Sớm", "Công", "T.Giờ", "T.Ca1", "T.Ca2", "T.Ca3", "Nơi làm việc"};
        for (int i = 0; i < dauCot.length; i++) o(sh, k, t + 9, t + 10, 8 + i, 8 + i, dauBang, dauCot[i]);

        LocalTime mocSang = Boolean.TRUE.equals(u.getAllowCheckinUntil9()) ? MOC_SANG_CHAM_CHUOC : MOC_SANG;
        for (int i = 0; i < soNgay; i++) {
            LocalDate d = thang.atDay(i + 1);
            NgayCong n = tinhNgay(d, luotTheoNgay.getOrDefault(d, List.of()), mocSang);
            Row row = sh.getRow(dongDau + i);
            Kieu ngay = Kieu.NGAY;
            if (d.getDayOfWeek() == DayOfWeek.SUNDAY) ngay = ngay.nen(NEN_CHU_NHAT);

            Cell c0 = row.createCell(0);
            c0.setCellValue(d);
            c0.setCellStyle(k.lay(ngay.so("m/d/yyyy")));
            ghi(row, k, 1, ngay, thu(d.getDayOfWeek()));
            for (int p = 0; p < 3; p++) {
                LocalTime[] cap = n != null && p < n.cap().size() ? n.cap().get(p) : null;
                for (int j = 0; j < 2; j++) {
                    LocalTime g = cap == null ? null : cap[j];
                    Cell c = row.createCell(2 + 2 * p + j);
                    c.setCellStyle(k.lay(ngay.so("hh:mm")));
                    if (g != null) c.setCellValue(g.toSecondOfDay() / 86400.0);
                }
            }
            ghi(row, k, 8, ngay, n == null ? 0 : n.tre());
            ghi(row, k, 9, ngay, n == null ? 0 : n.som());
            ghi(row, k, 10, ngay, n == null ? 0 : n.cong());
            ghi(row, k, 11, ngay, n == null ? 0 : n.gio());
            ghi(row, k, 12, ngay, n == null ? 0 : n.tangCa());
            ghi(row, k, 13, ngay, 0);
            ghi(row, k, 14, ngay, 0);
            ghi(row, k, 15, ngay, n == null ? null : n.noiLam());
        }

        // Đóng khung bảng, chỗ ký tên, tổng công
        Row dong = sh.getRow(dongCuoi + 1);
        for (int c = 0; c < SO_COT; c++) dong.createCell(c).setCellStyle(k.lay(Kieu.CO_BAN.vien(BorderStyle.THIN, null, null, null)));
        Kieu ky = Kieu.CO_BAN.chuDam().giua().khongVien();
        o(sh, k, dongCuoi + 2, dongCuoi + 2, 9, 12, ky, "Kí tên");
        o(sh, k, dongCuoi + 5, dongCuoi + 5, 9, 12, ky, u.getFullName());
        o(sh, k, dongCuoi + 6, dongCuoi + 6, 9, 12, ky, "=\"Tổng công: \"&SUM(" + cot.apply("K") + ")");
    }

    /**
     * Ghi một vùng (gộp nếu nhiều ô) có khung viền mảnh bao ngoài — đúng cách file
     * máy chấm công kẻ: viền chỉ ở mép vùng gộp. Giá trị bắt đầu bằng "=" là công thức.
     */
    private static void o(Sheet sh, BoKieu k, int r1, int r2, int c1, int c2, Kieu kieu, Object giaTri) {
        for (int r = r1; r <= r2; r++) {
            Row row = sh.getRow(r) != null ? sh.getRow(r) : sh.createRow(r);
            for (int c = c1; c <= c2; c++) {
                Kieu kc = kieu.boVien() ? kieu
                        : kieu.vien(r == r1 ? BorderStyle.THIN : null, r == r2 ? BorderStyle.THIN : null,
                                    c == c1 ? BorderStyle.THIN : null, c == c2 ? BorderStyle.THIN : null);
                Cell cell = row.getCell(c) != null ? row.getCell(c) : row.createCell(c);
                cell.setCellStyle(k.lay(kc));
            }
        }
        if (r1 != r2 || c1 != c2) sh.addMergedRegion(new CellRangeAddress(r1, r2, c1, c2));
        Cell dau = sh.getRow(r1).getCell(c1);
        if (giaTri instanceof String s && s.startsWith("=")) dau.setCellFormula(s.substring(1));
        else if (giaTri instanceof String s) dau.setCellValue(s);
        else if (giaTri instanceof Number n) dau.setCellValue(n.doubleValue());
    }

    private static void ghi(Row row, BoKieu k, int c, Kieu kieu, Object giaTri) {
        Cell cell = row.createCell(c);
        cell.setCellStyle(k.lay(kieu));
        if (giaTri instanceof Number n) cell.setCellValue(n.doubleValue());
        else if (giaTri instanceof String s) cell.setCellValue(s);
    }

    private static void dungTrang(Sheet sh) {
        sh.setColumnWidth(0, (int) Math.round(11.38 * 256));
        for (int c = 1; c <= 14; c++) sh.setColumnWidth(c, (int) Math.round(6.13 * 256));
        sh.setColumnWidth(15, (int) Math.round(8.63 * 256));
        PrintSetup in = sh.getPrintSetup();
        in.setPaperSize(PrintSetup.A4_PAPERSIZE);
        in.setLandscape(false);
        in.setScale((short) 94);
        sh.setMargin(PageMargin.TOP, 0.7);
        sh.setMargin(PageMargin.BOTTOM, 0.6);
        sh.setMargin(PageMargin.LEFT, 0.25);
        sh.setMargin(PageMargin.RIGHT, 0.13);
        sh.setMargin(PageMargin.HEADER, 0);
        sh.setMargin(PageMargin.FOOTER, 0);
    }

    // ── Số liệu một ngày ────────────────────────────────────────────────────

    /**
     * Số liệu một ngày của một người, đúng các cột C..P của file máy chấm công.
     *
     * @param cap    tối đa 3 cặp {vào, ra}; thiếu vế nào thì vế đó null
     * @param tre    số phút vào sau mốc đúng giờ
     * @param som    số phút ra trước 17:30 (thứ Hai–Sáu)
     * @param gio    số giờ làm, đã trừ nghỉ trưa
     * @param tangCa số giờ làm sau 18:00 (thứ Hai–Sáu)
     */
    record NgayCong(List<LocalTime[]> cap, int tre, int som, double cong, double gio, double tangCa, String noiLam) {}

    /** null nếu ngày đó không có lượt chấm công nào còn hiệu lực (chưa chấm, hoặc toàn bị từ chối). */
    static NgayCong tinhNgay(LocalDate ngay, List<CheckinLog> luot, LocalTime mocSang) {
        List<CheckinLog> hopLe = luot.stream()
                .filter(l -> !"REJECTED".equals(l.getStatus()) && l.getCheckinTime() != null)
                .sorted(Comparator.comparing(CheckinLog::getCheckinTime))
                .toList();
        if (hopLe.isEmpty()) return null;

        // Ghép cặp vào–ra theo thứ tự thời gian như máy chấm công; quá 3 cặp thì
        // cặp thứ 3 lấy giờ vào của nó và giờ ra cuối cùng trong ngày.
        List<LocalTime[]> cap = new ArrayList<>();
        LocalTime[] dang = null;
        for (CheckinLog l : hopLe) {
            LocalTime g = gioVN(l);
            if ("CHECK_OUT".equals(l.getActionType())) {
                if (dang != null && dang[1] == null) dang[1] = g;
                else { dang = new LocalTime[]{null, g}; cap.add(dang); }
            } else {
                dang = new LocalTime[]{g, null};
                cap.add(dang);
            }
        }
        if (cap.size() > 3) {
            LocalTime[] thuBa = {cap.get(2)[0], cap.get(cap.size() - 1)[1]};
            cap = new ArrayList<>(cap.subList(0, 2));
            cap.add(thuBa);
        }

        LocalTime vao = hopLe.stream().filter(l -> !"CHECK_OUT".equals(l.getActionType()))
                .map(AttendanceReportService::gioVN).min(Comparator.naturalOrder()).orElse(null);
        LocalTime ra = hopLe.stream().filter(l -> "CHECK_OUT".equals(l.getActionType()))
                .map(AttendanceReportService::gioVN).max(Comparator.naturalOrder()).orElse(null);

        double gio = 0, cong;
        if (vao != null && ra != null) {
            long giay = Math.max(0, Duration.between(vao, ra).getSeconds());
            if (vao.isBefore(TRUA) && ra.isAfter(HET_NGHI_TRUA)) giay = Math.max(0, giay - 90 * 60);
            gio = giay / 3600.0;
            // Đủ vào–ra: từ 5 giờ = 1 công, từ 2 giờ = 0,5 công, dưới 2 giờ = 0
            cong = gio >= 5.0 ? 1.0 : gio >= 2.0 ? 0.5 : 0;
        } else {
            // Luật công ty: chỉ có giờ vào HOẶC chỉ có giờ ra thì tính 0,5 công
            cong = CONG_THIEU_MOT_LUOT;
        }

        DayOfWeek thu = ngay.getDayOfWeek();
        boolean ngayThuong = thu != DayOfWeek.SATURDAY && thu != DayOfWeek.SUNDAY;
        int tre = 0;
        if (vao != null && thu != DayOfWeek.SUNDAY) {
            LocalTime moc = vao.isBefore(TRUA) ? mocSang : MOC_CHIEU;
            tre = (int) Math.max(0, Duration.between(moc, vao.truncatedTo(ChronoUnit.MINUTES)).toMinutes());
        }
        int som = ra != null && ngayThuong && ra.isBefore(HET_GIO)
                ? (int) Duration.between(ra, HET_GIO).toMinutes() : 0;
        double tangCa = 0;
        if (ra != null && ngayThuong && ra.isAfter(BAT_DAU_LAM_THEM)) {
            LocalTime tu = vao != null && vao.isAfter(BAT_DAU_LAM_THEM) ? vao : BAT_DAU_LAM_THEM;
            tangCa = Math.max(0, Duration.between(tu, ra).getSeconds()) / 3600.0;
        }

        String noiLam;
        if (hopLe.stream().anyMatch(l -> "PENDING".equals(l.getStatus()))) noiLam = "Chờ duyệt";
        else noiLam = switch (String.valueOf(hopLe.get(0).getCheckinType())) {
            case "FIELD" -> "Ngoài VP";
            case "MANUAL" -> "Thêm tay";
            default -> "App";
        };
        return new NgayCong(cap, tre, som, cong, lamTron(gio), lamTron(tangCa), noiLam);
    }

    private static LocalTime gioVN(CheckinLog l) {
        return l.getCheckinTime().withZoneSameInstant(VN).toLocalTime();
    }

    private static double lamTron(double x) {
        return Math.round(x * 100.0) / 100.0;
    }

    /** Người có ít nhất một lượt chấm công không bị từ chối trong khoảng đã lấy. */
    static Set<Long> nguoiCoChamCong(List<CheckinLog> logs) {
        return logs.stream()
                .filter(l -> !"REJECTED".equals(l.getStatus()))
                .map(CheckinLog::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /**
     * Người có chấm công trong tháng lên trước, người không có lượt hợp lệ nào
     * (chưa chấm lần nào, hoặc chỉ toàn lượt bị từ chối) xuống cuối. Trong mỗi
     * nhóm xếp theo phòng ban rồi tên, như Bảng công tháng trên web.
     */
    static List<User> xepNguoiCoChamCongTruoc(List<User> users, Set<Long> coChamCong) {
        List<User> ds = new ArrayList<>(users);
        ds.sort(Comparator.comparing((User u) -> !coChamCong.contains(u.getId()))
                .thenComparing(u -> u.getDepartment() != null && u.getDepartment().getName() != null
                        ? u.getDepartment().getName() : "")
                .thenComparing(u -> u.getFullName() != null ? u.getFullName() : "")
                .thenComparing(User::getId, Comparator.nullsLast(Comparator.naturalOrder())));
        return ds;
    }

    private static String thu(DayOfWeek d) {
        return switch (d) {
            case MONDAY -> "Hai";
            case TUESDAY -> "Ba";
            case WEDNESDAY -> "Tư";
            case THURSDAY -> "Năm";
            case FRIDAY -> "Sáu";
            case SATURDAY -> "Bảy";
            case SUNDAY -> "CN";
        };
    }

    // ── Kiểu ô ──────────────────────────────────────────────────────────────

    /**
     * Mô tả một kiểu ô. Viền null = không kẻ. {@code boVien} = vùng không kẻ
     * khung (chỗ ký tên).
     */
    record Kieu(int co, boolean dam, boolean nghieng, HorizontalAlignment ngang, boolean xuongDong,
                BorderStyle tren, BorderStyle duoi, BorderStyle trai, BorderStyle phai,
                String nen, String dinhDang, boolean boVien) {

        /** Arial 10, căn trái, giữa theo chiều dọc — nền của mọi ô trong file máy chấm công. */
        static final Kieu CO_BAN = new Kieu(10, false, false, HorizontalAlignment.LEFT, false,
                null, null, null, null, null, null, false);
        /** Dòng ngày: chữ nghiêng, viền trái/phải liền, viền dưới chấm. */
        static final Kieu NGAY = CO_BAN.chuNghieng().vien(null, BorderStyle.DOTTED, BorderStyle.THIN, BorderStyle.THIN);

        Kieu co(int c) { return new Kieu(c, dam, nghieng, ngang, xuongDong, tren, duoi, trai, phai, nen, dinhDang, boVien); }
        Kieu chuDam() { return new Kieu(co, true, nghieng, ngang, xuongDong, tren, duoi, trai, phai, nen, dinhDang, boVien); }
        Kieu chuNghieng() { return new Kieu(co, dam, true, ngang, xuongDong, tren, duoi, trai, phai, nen, dinhDang, boVien); }
        Kieu giua() { return new Kieu(co, dam, nghieng, HorizontalAlignment.CENTER, xuongDong, tren, duoi, trai, phai, nen, dinhDang, boVien); }
        Kieu ngatDong() { return new Kieu(co, dam, nghieng, ngang, true, tren, duoi, trai, phai, nen, dinhDang, boVien); }
        Kieu nen(String hex) { return new Kieu(co, dam, nghieng, ngang, xuongDong, tren, duoi, trai, phai, hex, dinhDang, boVien); }
        Kieu so(String fmt) { return new Kieu(co, dam, nghieng, ngang, xuongDong, tren, duoi, trai, phai, nen, fmt, boVien); }
        Kieu khongVien() { return new Kieu(co, dam, nghieng, ngang, xuongDong, null, null, null, null, nen, dinhDang, true); }
        Kieu vien(BorderStyle t, BorderStyle d, BorderStyle tr, BorderStyle p) {
            return new Kieu(co, dam, nghieng, ngang, xuongDong, t, d, tr, p, nen, dinhDang, boVien);
        }
    }

    /** Dựng mỗi kiểu một lần rồi dùng lại — Excel giới hạn số kiểu trong một file. */
    private static final class BoKieu {
        private final XSSFWorkbook wb;
        private final Map<Kieu, CellStyle> daCo = new HashMap<>();
        private final Map<String, Font> phong = new HashMap<>();

        BoKieu(XSSFWorkbook wb) {
            this.wb = wb;
            // Phông mặc định của file là Arial 10 như file máy chấm công
            Font macDinh = wb.getFontAt(0);
            macDinh.setFontName("Arial");
            macDinh.setFontHeightInPoints((short) 10);
        }

        CellStyle lay(Kieu k) {
            return daCo.computeIfAbsent(k, this::dung);
        }

        private CellStyle dung(Kieu k) {
            XSSFCellStyle s = wb.createCellStyle();
            s.setFont(phong.computeIfAbsent(k.co() + "|" + k.dam() + "|" + k.nghieng(), x -> {
                Font f = wb.createFont();
                f.setFontName("Arial");
                f.setFontHeightInPoints((short) k.co());
                f.setBold(k.dam());
                f.setItalic(k.nghieng());
                return f;
            }));
            s.setAlignment(k.ngang());
            s.setVerticalAlignment(VerticalAlignment.CENTER);
            s.setWrapText(k.xuongDong());
            if (k.tren() != null) s.setBorderTop(k.tren());
            if (k.duoi() != null) s.setBorderBottom(k.duoi());
            if (k.trai() != null) s.setBorderLeft(k.trai());
            if (k.phai() != null) s.setBorderRight(k.phai());
            if (k.nen() != null) {
                s.setFillForegroundColor(new XSSFColor(HexFormat.of().parseHex(k.nen()), null));
                s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            }
            if (k.dinhDang() != null) s.setDataFormat(wb.createDataFormat().getFormat(k.dinhDang()));
            return s;
        }
    }
}
