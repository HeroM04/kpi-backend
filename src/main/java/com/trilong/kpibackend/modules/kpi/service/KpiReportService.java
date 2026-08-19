package com.trilong.kpibackend.modules.kpi.service;

import com.trilong.kpibackend.modules.attendance.entity.CheckinLog;
import com.trilong.kpibackend.modules.attendance.repository.CheckinLogRepository;
import com.trilong.kpibackend.modules.battle.entity.FieldBattle;
import com.trilong.kpibackend.modules.battle.repository.FieldBattleRepository;
import com.trilong.kpibackend.modules.deal.entity.Deal;
import com.trilong.kpibackend.modules.deal.repository.DealRepository;
import com.trilong.kpibackend.modules.kpi.entity.KpiScore;
import com.trilong.kpibackend.modules.kpi.entity.KpiWeeklyScore;
import com.trilong.kpibackend.modules.kpi.repository.KpiScoreRepository;
import com.trilong.kpibackend.modules.kpi.repository.KpiWeeklyScoreRepository;
import com.trilong.kpibackend.modules.post.entity.SocialPost;
import com.trilong.kpibackend.modules.post.repository.SocialPostRepository;
import com.trilong.kpibackend.modules.user.entity.User;
import com.trilong.kpibackend.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.function.Predicate;

/**
 * KpiReportService — xuất báo cáo KPI ra Excel.
 *
 * <p>Hai loại báo cáo:
 * <ul>
 *   <li><b>Cá nhân</b> — 1 nhân sự / 1 tháng, gồm bảng điểm theo tuần, số liệu hoạt động
 *       thực tế và các sheet chi tiết từng bản ghi (chấm công, thực chiến, bài đăng, chốt căn).
 *       Mục đích: xem được vì sao ra con số đó, không chỉ nhìn điểm tổng.</li>
 *   <li><b>Toàn công ty</b> — bảng tổng hợp mọi nhân sự theo mẫu quen dùng
 *       (mỗi người một khối, các mục điểm × các tuần trong tháng).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class KpiReportService {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final UserRepository userRepository;
    private final KpiScoreRepository kpiScoreRepository;
    private final KpiWeeklyScoreRepository kpiWeeklyScoreRepository;
    private final CheckinLogRepository checkinLogRepository;
    private final FieldBattleRepository fieldBattleRepository;
    private final SocialPostRepository socialPostRepository;
    private final DealRepository dealRepository;
    private final KpiCalculationService kpiCalculationService;

    // ══════════════════════════════════════════════════════════════════════
    //  BÁO CÁO CÁ NHÂN — 1 người / 1 tháng
    // ══════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public byte[] generatePersonalReport(Long userId, String month) throws IOException {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhân sự có ID: " + userId));

        YearMonth ym = YearMonth.parse(month, MONTH_FMT);
        List<String> weeks = weeksOfMonth(ym);
        int maxKpi = kpiCalculationService.getMaxKpiForMonth(month);

        // Dữ liệu điểm
        KpiScore monthScore = kpiScoreRepository.findByUserIdAndMonth(userId, month).orElse(null);
        Map<String, KpiWeeklyScore> weekScores = new HashMap<>();
        for (KpiWeeklyScore w : kpiWeeklyScoreRepository.findByUserIdAndMonth(userId, month)) {
            weekScores.put(w.getWeek(), w);
        }

        // Bản ghi hoạt động thực tế trong tháng
        List<CheckinLog> checkins = filterByMonth(
                checkinLogRepository.findByUserIdOrderByCheckinTimeDesc(userId), ym, CheckinLog::getCheckinTime);
        List<FieldBattle> battles = filterByMonth(
                fieldBattleRepository.findByUserIdOrderBySubmittedAtDesc(userId), ym, FieldBattle::getSubmittedAt);
        List<SocialPost> posts = filterByMonth(
                socialPostRepository.findByUserIdOrderBySubmittedAtDesc(userId), ym, SocialPost::getSubmittedAt);
        List<Deal> deals = filterByMonth(
                dealRepository.findByUserIdOrderBySubmittedAtDesc(userId), ym, Deal::getSubmittedAt);

        try (Workbook wb = new XSSFWorkbook()) {
            Styles st = new Styles(wb);

            buildPersonalSummarySheet(wb, st, user, ym, weeks, maxKpi,
                    monthScore, weekScores, checkins, battles, posts, deals);
            buildCheckinSheet(wb, st, checkins);
            buildBattleSheet(wb, st, battles);
            buildPostSheet(wb, st, posts);
            buildDealSheet(wb, st, deals);

            return toBytes(wb);
        }
    }

    /** Sheet 1 — Tổng hợp: bảng điểm theo tuần + số liệu hoạt động + đánh giá. */
    private void buildPersonalSummarySheet(Workbook wb, Styles st, User user, YearMonth ym,
                                           List<String> weeks, int maxKpi,
                                           KpiScore monthScore, Map<String, KpiWeeklyScore> weekScores,
                                           List<CheckinLog> checkins, List<FieldBattle> battles,
                                           List<SocialPost> posts, List<Deal> deals) {
        Sheet sh = wb.createSheet("Tổng hợp KPI");
        int nWeeks = weeks.size();
        int lastCol = 1 + nWeeks; // cột 0 = tên mục, sau đó N tuần, rồi cột tổng

        // ── Tiêu đề ──────────────────────────────────────────────────────
        Row r0 = sh.createRow(0);
        setCell(r0, 0, "BÁO CÁO KPI CÁ NHÂN — THÁNG " + String.format("%02d/%d", ym.getMonthValue(), ym.getYear()), st.title);
        sh.addMergedRegion(new CellRangeAddress(0, 0, 0, lastCol));

        String deptName = user.getDepartment() != null ? user.getDepartment().getName() : "Chưa phân phòng";
        Row r2 = sh.createRow(2);
        setCell(r2, 0, "Họ và tên:", st.label);
        setCell(r2, 1, user.getFullName(), st.normal);
        setCell(r2, 3, "Phòng ban:", st.label);
        setCell(r2, 4, deptName, st.normal);

        Row r3 = sh.createRow(3);
        setCell(r3, 0, "Số điện thoại:", st.label);
        setCell(r3, 1, user.getPhoneNumber(), st.normal);
        setCell(r3, 3, "Ngày xuất:", st.label);
        setCell(r3, 4, ZonedDateTime.now(VN_ZONE).format(DATETIME_FMT), st.normal);

        int row = 5;

        // ── Bảng 1: ĐIỂM KPI THEO TUẦN ───────────────────────────────────
        setCell(sh.createRow(row), 0, "I. ĐIỂM KPI THEO TUẦN", st.section);
        sh.addMergedRegion(new CellRangeAddress(row, row, 0, lastCol));
        row++;

        Row hdr = sh.createRow(row++);
        setCell(hdr, 0, "Mục điểm", st.header);
        for (int i = 0; i < nWeeks; i++) {
            setCell(hdr, 1 + i, weekLabel(i + 1, weeks.get(i)), st.header);
        }
        setCell(hdr, lastCol, "Tổng tháng", st.header);

        int firstScoreRow = row;
        // Ba nhóm tiêu chí theo bảng KPI công ty: 30 + 40 + 30 = 100 điểm/tuần.
        // Chốt căn KHÔNG phải nhóm tính điểm — xem phần đánh giá bên dưới.
        row = writeScoreRow(sh, st, row, "1. Phát triển cá nhân (tối đa 30đ)",
                weeks, weekScores, KpiWeeklyScore::getAttendance, nWeeks, lastCol);
        row = writeScoreRow(sh, st, row, "2. Thực chiến (tối đa 40đ)",
                weeks, weekScores, KpiWeeklyScore::getMeeting, nWeeks, lastCol);
        row = writeScoreRow(sh, st, row, "3. Lan tỏa giá trị (tối đa 30đ)",
                weeks, weekScores, KpiWeeklyScore::getPost, nWeeks, lastCol);
        int lastScoreRow = row - 1;

        // Dòng tổng (mỗi tuần tối đa 100đ)
        Row totalRow = sh.createRow(row);
        setCell(totalRow, 0, "TỔNG ĐIỂM TUẦN (tối đa 100đ)", st.totalLabel);
        for (int i = 0; i <= nWeeks; i++) {
            String col = colName(1 + i);
            setFormula(totalRow, 1 + i,
                    String.format("SUM(%s%d:%s%d)", col, firstScoreRow + 1, col, lastScoreRow + 1), st.totalNumber);
        }
        int totalRowIdx = row;
        row += 2;

        // ── Bảng 2: SỐ LIỆU HOẠT ĐỘNG THỰC TẾ ────────────────────────────
        setCell(sh.createRow(row), 0, "II. SỐ LIỆU HOẠT ĐỘNG THỰC TẾ (đã được duyệt)", st.section);
        sh.addMergedRegion(new CellRangeAddress(row, row, 0, lastCol));
        row++;

        Row hdr2 = sh.createRow(row++);
        setCell(hdr2, 0, "Chỉ số", st.header);
        for (int i = 0; i < nWeeks; i++) setCell(hdr2, 1 + i, weekLabel(i + 1, weeks.get(i)), st.header);
        setCell(hdr2, lastCol, "Cả tháng", st.header);

        row = writeCountRow(sh, st, row, "Số lần chấm công", weeks, nWeeks, lastCol,
                w -> checkins.stream().filter(c -> "APPROVED".equals(c.getStatus()))
                        .filter(inWeek(w, CheckinLog::getCheckinTime)).count());

        row = writeCountRow(sh, st, row, "Số buổi thực chiến", weeks, nWeeks, lastCol,
                w -> battles.stream().filter(b -> "APPROVED".equals(b.getStatus()))
                        .filter(inWeek(w, FieldBattle::getSubmittedAt)).count());

        // Mỗi báo cáo thực chiến ứng với một khách hàng đi gặp
        row = writeCountRow(sh, st, row, "Số khách đi gặp", weeks, nWeeks, lastCol,
                w -> battles.stream().filter(b -> "APPROVED".equals(b.getStatus()))
                        .filter(inWeek(w, FieldBattle::getSubmittedAt))
                        .map(FieldBattle::getCustomerName)
                        .filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty())
                        .distinct().count());

        row = writeCountRow(sh, st, row, "Số bài đăng lan tỏa", weeks, nWeeks, lastCol,
                w -> posts.stream().filter(p -> "APPROVED".equals(p.getStatus()))
                        .filter(inWeek(w, SocialPost::getSubmittedAt)).count());

        row = writeCountRow(sh, st, row, "Số căn chốt", weeks, nWeeks, lastCol,
                w -> deals.stream().filter(d -> "APPROVED".equals(d.getStatus()))
                        .filter(inWeek(w, Deal::getSubmittedAt)).count());

        // Giá trị chốt — hiển thị theo đơn vị đồng
        Row valueRow = sh.createRow(row);
        setCell(valueRow, 0, "Giá trị chốt (VNĐ)", st.normal);
        for (int i = 0; i < nWeeks; i++) {
            double v = deals.stream().filter(d -> "APPROVED".equals(d.getStatus()))
                    .filter(inWeek(weeks.get(i), Deal::getSubmittedAt))
                    .mapToDouble(d -> d.getPrice() != null ? d.getPrice() : 0).sum();
            setCell(valueRow, 1 + i, v, st.money);
        }
        setFormula(valueRow, lastCol, sumRowFormula(row, 1, nWeeks), st.moneyBold);
        row += 2;

        // ── Bảng 3: ĐÁNH GIÁ ─────────────────────────────────────────────
        setCell(sh.createRow(row), 0, "III. ĐÁNH GIÁ THÁNG", st.section);
        sh.addMergedRegion(new CellRangeAddress(row, row, 0, lastCol));
        row++;

        int official = monthScore != null ? monthScore.getTotal() : 0;
        boolean hasDeal = deals.stream().anyMatch(d -> "APPROVED".equals(d.getStatus()));
        var grade = kpiCalculationService.gradeMonth(
                official, String.format("%d-%02d", ym.getYear(), ym.getMonthValue()),
                hasDeal, user.getCreatedAt());

        Row e1 = sh.createRow(row++);
        setCell(e1, 0, "Tổng điểm KPI tháng", st.label);
        setCell(e1, 1, official, st.totalNumber);
        setCell(e1, 2, "Tổng điểm các tuần (mỗi tuần tối đa 100đ)", st.note);

        Row e2 = sh.createRow(row++);
        setCell(e2, 0, "Điểm tối đa của tháng", st.label);
        setCell(e2, 1, maxKpi, st.number);
        setCell(e2, 2, "(" + (maxKpi / 100) + " tuần × 100 điểm)", st.note);

        Row e3 = sh.createRow(row++);
        setCell(e3, 0, "Ngưỡng đạt 50%", st.label);
        setCell(e3, 1, grade.min50(), st.number);
        setCell(e3, 2, grade.isNewbie() ? "Mức nhân sự mới (80% quy định)" : "", st.note);

        Row e4 = sh.createRow(row++);
        setCell(e4, 0, "Ngưỡng đạt 100%", st.label);
        setCell(e4, 1, grade.min100(), st.number);
        setCell(e4, 2, grade.isNewbie() ? "Mức nhân sự mới (80% quy định)" : "", st.note);

        Row e5 = sh.createRow(row++);
        setCell(e5, 0, "KẾT QUẢ XẾP LOẠI", st.totalLabel);
        setCell(e5, 1, grade.label(), st.totalNumber);
        setCell(e5, 2, grade.reason(), st.note);

        Row e6 = sh.createRow(row++);
        setCell(e6, 0, "Chốt căn trong tháng", st.label);
        setCell(e6, 1, hasDeal ? "CÓ → đạt 100% KPI" : "Không", hasDeal ? st.ok : st.normal);

        Row e7 = sh.createRow(row++);
        setCell(e7, 0, "Cờ đỏ (nghi ngờ gian lận)", st.label);
        setCell(e7, 1, (monthScore != null && monthScore.isFlagged()) ? "CÓ" : "Không", st.normal);

        row++;
        setCell(sh.createRow(row), 0,
                "Ghi chú: Chỉ tính các bản ghi ĐÃ DUYỆT. Mỗi tuần tối đa 100đ, chia theo nhóm: "
                        + "Phát triển cá nhân 30đ + Thực chiến 40đ + Lan tỏa 30đ. Điểm tuần không âm. "
                        + "Chốt căn không cộng điểm nhưng được tính đạt 100% KPI tháng. "
                        + "KPI chỉ có hai mức: đạt 50% hoặc đạt 100%.",
                st.note);
        sh.addMergedRegion(new CellRangeAddress(row, row, 0, lastCol));

        sh.setColumnWidth(0, 26 * 256);
        for (int i = 1; i <= nWeeks; i++) sh.setColumnWidth(i, 12 * 256);
        sh.setColumnWidth(lastCol, 16 * 256);
        sh.createFreezePane(1, 7);
    }

    private int writeScoreRow(Sheet sh, Styles st, int row, String label, List<String> weeks,
                              Map<String, KpiWeeklyScore> scores,
                              java.util.function.ToIntFunction<KpiWeeklyScore> getter,
                              int nWeeks, int lastCol) {
        Row r = sh.createRow(row);
        setCell(r, 0, label, st.normal);
        for (int i = 0; i < nWeeks; i++) {
            KpiWeeklyScore w = scores.get(weeks.get(i));
            setCell(r, 1 + i, w != null ? getter.applyAsInt(w) : 0, st.number);
        }
        setFormula(r, lastCol, sumRowFormula(row, 1, nWeeks), st.numberBold);
        return row + 1;
    }

    private int writeCountRow(Sheet sh, Styles st, int row, String label, List<String> weeks,
                              int nWeeks, int lastCol, java.util.function.Function<String, Long> counter) {
        Row r = sh.createRow(row);
        setCell(r, 0, label, st.normal);
        for (int i = 0; i < nWeeks; i++) {
            setCell(r, 1 + i, counter.apply(weeks.get(i)), st.number);
        }
        setFormula(r, lastCol, sumRowFormula(row, 1, nWeeks), st.numberBold);
        return row + 1;
    }

    // ── Các sheet chi tiết ────────────────────────────────────────────────

    private void buildCheckinSheet(Workbook wb, Styles st, List<CheckinLog> logs) {
        Sheet sh = wb.createSheet("Chi tiết chấm công");
        String[] cols = {"Ngày", "Thứ", "Giờ", "Loại", "Trạng thái", "Khoảng cách (m)", "Địa chỉ", "Ghi chú", "Lý do từ chối"};
        writeHeader(sh, st, cols);
        int r = 1;
        for (CheckinLog c : sortedByTime(logs, CheckinLog::getCheckinTime)) {
            ZonedDateTime t = c.getCheckinTime().withZoneSameInstant(VN_ZONE);
            Row row = sh.createRow(r++);
            setCell(row, 0, t.format(DATE_FMT), st.normal);
            setCell(row, 1, vietnameseDayOfWeek(t.getDayOfWeek()), st.normal);
            setCell(row, 2, t.format(DateTimeFormatter.ofPattern("HH:mm")), st.normal);
            setCell(row, 3, "CHECK_OUT".equals(c.getActionType()) ? "Ra về" : "Vào làm", st.normal);
            setCell(row, 4, statusVi(c.getStatus()), statusStyle(st, c.getStatus()));
            setCell(row, 5, c.getDistanceToOffice() != null ? Math.round(c.getDistanceToOffice()) : 0, st.number);
            setCell(row, 6, nz(c.getAddress()), st.normal);
            setCell(row, 7, nz(c.getNote()), st.normal);
            setCell(row, 8, nz(c.getRejectReason()), st.normal);
        }
        autoSize(sh, cols.length, new int[]{12, 10, 8, 10, 14, 14, 45, 30, 25});
    }

    private void buildBattleSheet(Workbook wb, Styles st, List<FieldBattle> battles) {
        Sheet sh = wb.createSheet("Chi tiết thực chiến");
        String[] cols = {"Ngày", "Khách hàng", "Số điện thoại", "Dự án", "Nội dung trao đổi", "Địa điểm", "Trạng thái"};
        writeHeader(sh, st, cols);
        int r = 1;
        for (FieldBattle b : sortedByTime(battles, FieldBattle::getSubmittedAt)) {
            Row row = sh.createRow(r++);
            setCell(row, 0, b.getSubmittedAt().withZoneSameInstant(VN_ZONE).format(DATETIME_FMT), st.normal);
            setCell(row, 1, nz(b.getCustomerName()), st.normal);
            setCell(row, 2, nz(b.getCustomerPhone()), st.normal);
            setCell(row, 3, nz(b.getProject()), st.normal);
            setCell(row, 4, nz(b.getContent()), st.normal);
            setCell(row, 5, nz(b.getLocation()), st.normal);
            setCell(row, 6, statusVi(b.getStatus()), statusStyle(st, b.getStatus()));
        }
        autoSize(sh, cols.length, new int[]{18, 22, 15, 22, 50, 30, 14});
    }

    private void buildPostSheet(Workbook wb, Styles st, List<SocialPost> posts) {
        Sheet sh = wb.createSheet("Chi tiết bài đăng");
        String[] cols = {"Ngày", "Nền tảng", "Đường dẫn", "Nội dung", "Trạng thái"};
        writeHeader(sh, st, cols);
        int r = 1;
        for (SocialPost p : sortedByTime(posts, SocialPost::getSubmittedAt)) {
            Row row = sh.createRow(r++);
            setCell(row, 0, p.getSubmittedAt().withZoneSameInstant(VN_ZONE).format(DATETIME_FMT), st.normal);
            setCell(row, 1, nz(p.getPlatform()), st.normal);
            setCell(row, 2, nz(p.getLink()), st.normal);
            setCell(row, 3, nz(p.getCaption()), st.normal);
            setCell(row, 4, statusVi(p.getStatus()), statusStyle(st, p.getStatus()));
        }
        autoSize(sh, cols.length, new int[]{18, 14, 45, 50, 14});
    }

    private void buildDealSheet(Workbook wb, Styles st, List<Deal> deals) {
        Sheet sh = wb.createSheet("Chi tiết chốt căn");
        String[] cols = {"Ngày", "Khách hàng", "Số điện thoại", "Dự án", "Căn", "Giá bán (VNĐ)", "Hoa hồng (VNĐ)", "Điểm KPI", "Trạng thái"};
        writeHeader(sh, st, cols);
        int r = 1;
        for (Deal d : sortedByTime(deals, Deal::getSubmittedAt)) {
            Row row = sh.createRow(r++);
            setCell(row, 0, d.getSubmittedAt().withZoneSameInstant(VN_ZONE).format(DATETIME_FMT), st.normal);
            setCell(row, 1, nz(d.getCustomerName()), st.normal);
            setCell(row, 2, nz(d.getCustomerPhone()), st.normal);
            setCell(row, 3, nz(d.getProjectName()), st.normal);
            setCell(row, 4, nz(d.getUnit()), st.normal);
            setCell(row, 5, d.getPrice() != null ? d.getPrice() : 0, st.money);
            setCell(row, 6, d.getCommission() != null ? d.getCommission() : 0, st.money);
            setCell(row, 7, d.getKpiTriggered() != null ? d.getKpiTriggered() : 0, st.number);
            setCell(row, 8, statusVi(d.getStatus()), statusStyle(st, d.getStatus()));
        }
        // Dòng cộng cuối bảng
        if (r > 1) {
            Row sum = sh.createRow(r);
            setCell(sum, 4, "TỔNG", st.totalLabel);
            setFormula(sum, 5, String.format("SUM(F2:F%d)", r), st.moneyBold);
            setFormula(sum, 6, String.format("SUM(G2:G%d)", r), st.moneyBold);
            setFormula(sum, 7, String.format("SUM(H2:H%d)", r), st.totalNumber);
        }
        autoSize(sh, cols.length, new int[]{18, 22, 15, 22, 12, 18, 18, 10, 14});
    }

    // ══════════════════════════════════════════════════════════════════════
    //  BÁO CÁO TOÀN CÔNG TY — theo mẫu quen dùng
    // ══════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public byte[] generateCompanyReport(String month) throws IOException {
        YearMonth ym = YearMonth.parse(month, MONTH_FMT);
        List<String> weeks = weeksOfMonth(ym);
        int nWeeks = weeks.size();
        int maxKpi = kpiCalculationService.getMaxKpiForMonth(month);

        List<User> users = userRepository.findAll().stream()
                .filter(u -> !"INACTIVE".equalsIgnoreCase(nz(u.getStatus())))
                .sorted(Comparator
                        .comparing((User u) -> u.getDepartment() != null ? u.getDepartment().getName() : "zzz")
                        .thenComparing(u -> nz(u.getFullName())))
                .toList();

        try (Workbook wb = new XSSFWorkbook()) {
            Styles st = new Styles(wb);
            Sheet sh = wb.createSheet("Tổng hợp T" + ym.getMonthValue());

            // Cột: STT | Họ tên | Phòng ban | Mục điểm | Tuần 1..N | Tổng tháng | % đạt | Chú thích
            int cMuc = 3, cW1 = 4, cTotal = cW1 + nWeeks, cPct = cTotal + 1, cNote = cPct + 1;

            Row title = sh.createRow(0);
            setCell(title, 0, "TỔNG HỢP BÁO CÁO KPI THÁNG " + String.format("%02d/%d", ym.getMonthValue(), ym.getYear()), st.title);
            sh.addMergedRegion(new CellRangeAddress(0, 0, 0, cNote));

            // Ngưỡng xếp loại của tháng (dùng chung cho nhân sự chính thức)
            var sample = kpiCalculationService.gradeMonth(0, month, false, null);
            Row sub = sh.createRow(1);
            setCell(sub, 0, String.format(
                    "Tối đa %d điểm (%d tuần × 100đ)   |   Đạt 50%%: từ %d điểm   |   Đạt 100%%: từ %d điểm"
                            + "   |   Nhân sự mới áp mức 80%%   |   Ngày xuất: %s",
                    maxKpi, nWeeks, sample.min50(), sample.min100(),
                    ZonedDateTime.now(VN_ZONE).format(DATETIME_FMT)), st.note);
            sh.addMergedRegion(new CellRangeAddress(1, 1, 0, cNote));

            Row hdr = sh.createRow(3);
            setCell(hdr, 0, "STT", st.header);
            setCell(hdr, 1, "Họ và tên", st.header);
            setCell(hdr, 2, "Phòng ban", st.header);
            setCell(hdr, cMuc, "Mục điểm", st.header);
            for (int i = 0; i < nWeeks; i++) setCell(hdr, cW1 + i, weekLabel(i + 1, weeks.get(i)), st.header);
            setCell(hdr, cTotal, "Tổng điểm THÁNG", st.header);
            setCell(hdr, cPct, "XẾP LOẠI KPI", st.header);
            setCell(hdr, cNote, "Chú thích", st.header);

            // Ba nhóm tính điểm (30/40/30) + một dòng số lượng để đối chiếu thực tế
            String[] muc = {
                    "1. Phát triển cá nhân (30đ)",
                    "2. Thực chiến (40đ)",
                    "3. Lan tỏa giá trị (30đ)",
                    "Số khách đi gặp (số lượng)",
                    "TỔNG ĐIỂM TUẦN (100đ)"
            };
            int row = 4;
            int stt = 1;

            for (User u : users) {
                Long uid = u.getId();
                Map<String, KpiWeeklyScore> ws = new HashMap<>();
                for (KpiWeeklyScore w : kpiWeeklyScoreRepository.findByUserIdAndMonth(uid, month)) ws.put(w.getWeek(), w);

                List<FieldBattle> battles = filterByMonth(
                        fieldBattleRepository.findByUserIdOrderBySubmittedAtDesc(uid), ym, FieldBattle::getSubmittedAt);
                List<Deal> deals = filterByMonth(
                        dealRepository.findByUserIdOrderBySubmittedAtDesc(uid), ym, Deal::getSubmittedAt);
                KpiScore ms = kpiScoreRepository.findByUserIdAndMonth(uid, month).orElse(null);

                int blockStart = row;
                for (int m = 0; m < muc.length; m++) {
                    Row r = sh.createRow(row);
                    boolean isTotal = (m == muc.length - 1);
                    setCell(r, cMuc, muc[m], isTotal ? st.totalLabel : st.normal);

                    for (int i = 0; i < nWeeks; i++) {
                        String wk = weeks.get(i);
                        KpiWeeklyScore w = ws.get(wk);
                        Object val;
                        switch (m) {
                            case 0 -> val = w != null ? w.getAttendance() : 0;
                            case 1 -> val = w != null ? w.getMeeting() : 0;
                            case 2 -> val = w != null ? w.getPost() : 0;
                            case 3 -> val = battles.stream().filter(b -> "APPROVED".equals(b.getStatus()))
                                    .filter(inWeek(wk, FieldBattle::getSubmittedAt))
                                    .map(FieldBattle::getCustomerName).filter(Objects::nonNull)
                                    .map(String::trim).filter(s -> !s.isEmpty()).distinct().count();
                            default -> val = null;
                        }
                        if (isTotal) {
                            // Tổng tuần = 3 nhóm tính điểm (bỏ dòng "Số khách đi gặp" vì là số lượng)
                            String c = colName(cW1 + i);
                            setFormula(r, cW1 + i, String.format("%s%d+%s%d+%s%d",
                                    c, blockStart + 1, c, blockStart + 2, c, blockStart + 3),
                                    st.totalNumber);
                        } else if (val instanceof Long lv) {
                            setCell(r, cW1 + i, lv, st.number);
                        } else {
                            setCell(r, cW1 + i, (Integer) val, st.number);
                        }
                    }

                    // Cột tổng tháng của từng dòng
                    String cs = colName(cW1), ce = colName(cW1 + nWeeks - 1);
                    setFormula(r, cTotal, String.format("SUM(%s%d:%s%d)", cs, row + 1, ce, row + 1),
                            isTotal ? st.totalNumber : st.numberBold);
                    row++;
                }

                // Gộp ô STT / Họ tên / Phòng ban cho cả khối
                setCell(sh.getRow(blockStart), 0, stt++, st.center);
                setCell(sh.getRow(blockStart), 1, nz(u.getFullName()), st.normalBold);
                setCell(sh.getRow(blockStart), 2, u.getDepartment() != null ? u.getDepartment().getName() : "Chưa phân phòng", st.normal);
                sh.addMergedRegion(new CellRangeAddress(blockStart, row - 1, 0, 0));
                sh.addMergedRegion(new CellRangeAddress(blockStart, row - 1, 1, 1));
                sh.addMergedRegion(new CellRangeAddress(blockStart, row - 1, 2, 2));

                // Xếp loại KPI — chỉ có hai mức: đạt 50% hoặc đạt 100%
                boolean hasDeal = deals.stream().anyMatch(d -> "APPROVED".equals(d.getStatus()));
                int monthTotal = (ms != null) ? ms.getTotal() : 0;
                var grade = kpiCalculationService.gradeMonth(monthTotal, month, hasDeal, u.getCreatedAt());

                setCell(sh.getRow(blockStart), cPct, grade.label(),
                        grade.percent() == 100 ? st.ok : (grade.percent() == 50 ? st.pending : st.bad));
                sh.addMergedRegion(new CellRangeAddress(blockStart, row - 1, cPct, cPct));

                // Chú thích: lý do xếp loại + các dấu hiệu cần lưu ý
                List<String> notes = new ArrayList<>();
                notes.add(grade.reason());
                if (grade.isNewbie()) notes.add("Nhân sự mới — áp mức 80%");
                if (ms != null && ms.isFlagged()) notes.add("Bị gắn cờ đỏ — cần hậu kiểm");
                setCell(sh.getRow(blockStart), cNote, String.join(" · ", notes), st.note);
                sh.addMergedRegion(new CellRangeAddress(blockStart, row - 1, cNote, cNote));

                row++; // dòng trống giữa các nhân sự
            }

            sh.setColumnWidth(0, 6 * 256);
            sh.setColumnWidth(1, 26 * 256);
            sh.setColumnWidth(2, 20 * 256);
            sh.setColumnWidth(cMuc, 20 * 256);
            for (int i = 0; i < nWeeks; i++) sh.setColumnWidth(cW1 + i, 11 * 256);
            sh.setColumnWidth(cTotal, 17 * 256);
            sh.setColumnWidth(cPct, 12 * 256);
            sh.setColumnWidth(cNote, 30 * 256);
            sh.createFreezePane(4, 4);

            return toBytes(wb);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Tiện ích
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Danh sách mã tuần (yyyy-Www) thuộc tháng KPI này.
     * Một tuần thuộc tháng chứa ngày thứ Hai của nó, nên tháng luôn có đúng
     * 4 hoặc 5 tuần — khớp với chỉ tiêu 400/500 điểm.
     */
    private List<String> weeksOfMonth(YearMonth ym) {
        return kpiCalculationService.getWeeksOfMonth(
                String.format("%d-%02d", ym.getYear(), ym.getMonthValue()));
    }

    /**
     * Nhãn cột tuần kèm khoảng ngày thực tế, ví dụ "Tuần 1\n(27/07 - 02/08)".
     * Ghi rõ ngày để tránh nhầm: một tháng có thể chạm 5-6 tuần ISO, trong khi
     * chỉ tiêu tháng lại tính theo số ngày thứ Hai.
     */
    private String weekLabel(int index, String weekCode) {
        try {
            int year = Integer.parseInt(weekCode.substring(0, 4));
            int wk = Integer.parseInt(weekCode.substring(6));
            LocalDate monday = LocalDate.of(year, 1, 4)
                    .with(WeekFields.ISO.weekOfWeekBasedYear(), wk)
                    .with(WeekFields.ISO.dayOfWeek(), 1);
            DateTimeFormatter f = DateTimeFormatter.ofPattern("dd/MM");
            return "Tuần " + index + "\n(" + monday.format(f) + " - " + monday.plusDays(6).format(f) + ")";
        } catch (Exception e) {
            return "Tuần " + index;
        }
    }

    private String weekOf(LocalDate date) {
        WeekFields wf = WeekFields.ISO;
        return String.format("%d-W%02d", date.get(wf.weekBasedYear()), date.get(wf.weekOfWeekBasedYear()));
    }

    /** Bộ lọc: bản ghi thuộc tuần chỉ định. */
    private <T> Predicate<T> inWeek(String week, java.util.function.Function<T, ZonedDateTime> timeGetter) {
        return item -> {
            ZonedDateTime t = timeGetter.apply(item);
            if (t == null) return false;
            return week.equals(weekOf(t.withZoneSameInstant(VN_ZONE).toLocalDate()));
        };
    }

    /**
     * Lọc bản ghi thuộc tháng KPI.
     *
     * <p>Xét theo TUẦN chứ không theo ngày lịch: bản ghi thuộc tháng nếu tuần
     * của nó nằm trong danh sách tuần của tháng đó. Nhờ vậy tuần cuối tháng
     * không bị cắt đôi — việc làm ngày 01–06/09/2026 vẫn nằm trong báo cáo
     * tháng 8 (tuần bắt đầu 31/08).
     */
    private <T> List<T> filterByMonth(List<T> items, YearMonth ym,
                                      java.util.function.Function<T, ZonedDateTime> timeGetter) {
        Set<String> weeks = new HashSet<>(weeksOfMonth(ym));
        List<T> out = new ArrayList<>();
        for (T it : items) {
            ZonedDateTime t = timeGetter.apply(it);
            if (t == null) continue;
            if (weeks.contains(weekOf(t.withZoneSameInstant(VN_ZONE).toLocalDate()))) out.add(it);
        }
        return out;
    }

    private <T> List<T> sortedByTime(List<T> items, java.util.function.Function<T, ZonedDateTime> g) {
        List<T> copy = new ArrayList<>(items);
        copy.sort(Comparator.comparing(g, Comparator.nullsLast(Comparator.naturalOrder())));
        return copy;
    }

    private String sumRowFormula(int rowIdx, int firstCol, int nCols) {
        return String.format("SUM(%s%d:%s%d)", colName(firstCol), rowIdx + 1,
                colName(firstCol + nCols - 1), rowIdx + 1);
    }

    private String colName(int idx) {
        StringBuilder sb = new StringBuilder();
        int i = idx;
        while (i >= 0) {
            sb.insert(0, (char) ('A' + i % 26));
            i = i / 26 - 1;
        }
        return sb.toString();
    }

    private String statusVi(String s) {
        if (s == null) return "";
        return switch (s.toUpperCase()) {
            case "APPROVED" -> "Đã duyệt";
            case "PENDING" -> "Chờ duyệt";
            case "REJECTED" -> "Từ chối";
            default -> s;
        };
    }

    private CellStyle statusStyle(Styles st, String status) {
        if (status == null) return st.normal;
        return switch (status.toUpperCase()) {
            case "APPROVED" -> st.ok;
            case "REJECTED" -> st.bad;
            default -> st.pending;
        };
    }

    private String vietnameseDayOfWeek(DayOfWeek d) {
        return switch (d) {
            case MONDAY -> "Thứ 2";
            case TUESDAY -> "Thứ 3";
            case WEDNESDAY -> "Thứ 4";
            case THURSDAY -> "Thứ 5";
            case FRIDAY -> "Thứ 6";
            case SATURDAY -> "Thứ 7";
            case SUNDAY -> "Chủ nhật";
        };
    }

    private String nz(String s) { return s == null ? "" : s; }

    private void writeHeader(Sheet sh, Styles st, String[] cols) {
        Row h = sh.createRow(0);
        for (int i = 0; i < cols.length; i++) setCell(h, i, cols[i], st.header);
        sh.createFreezePane(0, 1);
    }

    private void autoSize(Sheet sh, int nCols, int[] widths) {
        for (int i = 0; i < nCols; i++) {
            sh.setColumnWidth(i, (i < widths.length ? widths[i] : 15) * 256);
        }
    }

    private void setCell(Row row, int col, String v, CellStyle s) {
        Cell c = row.createCell(col); c.setCellValue(v); c.setCellStyle(s);
    }
    private void setCell(Row row, int col, double v, CellStyle s) {
        Cell c = row.createCell(col); c.setCellValue(v); c.setCellStyle(s);
    }
    private void setFormula(Row row, int col, String f, CellStyle s) {
        Cell c = row.createCell(col); c.setCellFormula(f); c.setCellStyle(s);
    }

    private byte[] toBytes(Workbook wb) throws IOException {
        // Tính sẵn kết quả các công thức và lưu vào file. Nếu không làm bước này,
        // ô công thức sẽ trống cho tới khi người dùng mở bằng Excel — các công cụ
        // xem nhanh (Google Sheets preview, thư viện đọc file) sẽ thấy ô rỗng.
        try {
            wb.getCreationHelper().createFormulaEvaluator().evaluateAll();
        } catch (Exception ignored) {
            // Công thức vẫn còn nguyên trong file, Excel sẽ tự tính khi mở
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            wb.write(out);
            return out.toByteArray();
        }
    }

    /** Bộ style dùng chung cho toàn báo cáo. */
    private static class Styles {
        final CellStyle title, section, header, label, normal, normalBold, center,
                number, numberBold, totalLabel, totalNumber, percent, money, moneyBold,
                note, ok, bad, pending;

        Styles(Workbook wb) {
            String fontName = "Arial";

            Font fTitle = wb.createFont();
            fTitle.setBold(true); fTitle.setFontHeightInPoints((short) 14); fTitle.setFontName(fontName);
            Font fSection = wb.createFont();
            fSection.setBold(true); fSection.setFontHeightInPoints((short) 11);
            fSection.setColor(IndexedColors.WHITE.getIndex()); fSection.setFontName(fontName);
            Font fHeader = wb.createFont();
            fHeader.setBold(true); fHeader.setFontHeightInPoints((short) 10);
            fHeader.setColor(IndexedColors.WHITE.getIndex()); fHeader.setFontName(fontName);
            Font fBold = wb.createFont();
            fBold.setBold(true); fBold.setFontHeightInPoints((short) 10); fBold.setFontName(fontName);
            Font fNormal = wb.createFont();
            fNormal.setFontHeightInPoints((short) 10); fNormal.setFontName(fontName);
            Font fNote = wb.createFont();
            fNote.setItalic(true); fNote.setFontHeightInPoints((short) 9);
            fNote.setColor(IndexedColors.GREY_50_PERCENT.getIndex()); fNote.setFontName(fontName);

            title = wb.createCellStyle();
            title.setFont(fTitle); title.setAlignment(HorizontalAlignment.CENTER);
            title.setVerticalAlignment(VerticalAlignment.CENTER);

            section = wb.createCellStyle();
            section.setFont(fSection);
            section.setFillForegroundColor(IndexedColors.DARK_TEAL.getIndex());
            section.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            section.setAlignment(HorizontalAlignment.LEFT);
            section.setVerticalAlignment(VerticalAlignment.CENTER);

            header = wb.createCellStyle();
            header.setFont(fHeader);
            header.setFillForegroundColor(IndexedColors.SEA_GREEN.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setAlignment(HorizontalAlignment.CENTER);
            header.setVerticalAlignment(VerticalAlignment.CENTER);
            header.setWrapText(true);
            border(header);

            label = wb.createCellStyle(); label.setFont(fBold); border(label);
            normal = wb.createCellStyle(); normal.setFont(fNormal); border(normal);
            normal.setVerticalAlignment(VerticalAlignment.CENTER);
            normalBold = wb.createCellStyle(); normalBold.setFont(fBold); border(normalBold);
            normalBold.setVerticalAlignment(VerticalAlignment.CENTER);

            center = wb.createCellStyle(); center.setFont(fNormal);
            center.setAlignment(HorizontalAlignment.CENTER);
            center.setVerticalAlignment(VerticalAlignment.CENTER); border(center);

            DataFormat df = wb.createDataFormat();

            number = wb.createCellStyle(); number.setFont(fNormal);
            number.setAlignment(HorizontalAlignment.CENTER);
            number.setDataFormat(df.getFormat("#,##0;-#,##0;-")); border(number);

            numberBold = wb.createCellStyle(); numberBold.setFont(fBold);
            numberBold.setAlignment(HorizontalAlignment.CENTER);
            numberBold.setDataFormat(df.getFormat("#,##0;-#,##0;-")); border(numberBold);

            totalLabel = wb.createCellStyle(); totalLabel.setFont(fBold);
            totalLabel.setFillForegroundColor(IndexedColors.LEMON_CHIFFON.getIndex());
            totalLabel.setFillPattern(FillPatternType.SOLID_FOREGROUND); border(totalLabel);

            totalNumber = wb.createCellStyle(); totalNumber.setFont(fBold);
            totalNumber.setAlignment(HorizontalAlignment.CENTER);
            totalNumber.setFillForegroundColor(IndexedColors.LEMON_CHIFFON.getIndex());
            totalNumber.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            totalNumber.setDataFormat(df.getFormat("#,##0;-#,##0;-")); border(totalNumber);

            percent = wb.createCellStyle(); percent.setFont(fBold);
            percent.setAlignment(HorizontalAlignment.CENTER);
            percent.setVerticalAlignment(VerticalAlignment.CENTER);
            percent.setDataFormat(df.getFormat("0.0%")); border(percent);

            money = wb.createCellStyle(); money.setFont(fNormal);
            money.setDataFormat(df.getFormat("#,##0;-#,##0;-"));
            money.setAlignment(HorizontalAlignment.RIGHT); border(money);

            moneyBold = wb.createCellStyle(); moneyBold.setFont(fBold);
            moneyBold.setDataFormat(df.getFormat("#,##0;-#,##0;-"));
            moneyBold.setAlignment(HorizontalAlignment.RIGHT); border(moneyBold);

            note = wb.createCellStyle(); note.setFont(fNote); note.setWrapText(true);
            note.setVerticalAlignment(VerticalAlignment.CENTER);

            ok = wb.createCellStyle(); ok.setFont(fNormal);
            ok.setAlignment(HorizontalAlignment.CENTER);
            ok.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
            ok.setFillPattern(FillPatternType.SOLID_FOREGROUND); border(ok);

            bad = wb.createCellStyle(); bad.setFont(fNormal);
            bad.setAlignment(HorizontalAlignment.CENTER);
            bad.setFillForegroundColor(IndexedColors.ROSE.getIndex());
            bad.setFillPattern(FillPatternType.SOLID_FOREGROUND); border(bad);

            pending = wb.createCellStyle(); pending.setFont(fNormal);
            pending.setAlignment(HorizontalAlignment.CENTER);
            pending.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
            pending.setFillPattern(FillPatternType.SOLID_FOREGROUND); border(pending);
        }

        private void border(CellStyle s) {
            s.setBorderTop(BorderStyle.THIN); s.setBorderBottom(BorderStyle.THIN);
            s.setBorderLeft(BorderStyle.THIN); s.setBorderRight(BorderStyle.THIN);
            s.setTopBorderColor(IndexedColors.GREY_40_PERCENT.getIndex());
            s.setBottomBorderColor(IndexedColors.GREY_40_PERCENT.getIndex());
            s.setLeftBorderColor(IndexedColors.GREY_40_PERCENT.getIndex());
            s.setRightBorderColor(IndexedColors.GREY_40_PERCENT.getIndex());
        }
    }
}
