package com.trilong.kpibackend.modules.kpi.service;

import com.trilong.kpibackend.modules.kpi.entity.KpiLedgerEntry;
import com.trilong.kpibackend.modules.kpi.entity.KpiScore;
import com.trilong.kpibackend.modules.kpi.entity.KpiWeeklyScore;
import com.trilong.kpibackend.modules.kpi.repository.KpiLedgerEntryRepository;
import com.trilong.kpibackend.modules.kpi.repository.KpiScoreRepository;
import com.trilong.kpibackend.modules.kpi.repository.KpiWeeklyScoreRepository;
import com.trilong.kpibackend.modules.user.entity.User;
import com.trilong.kpibackend.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import com.trilong.kpibackend.modules.kpi.dto.KpiScoreResponseDTO;
import jakarta.annotation.PostConstruct;
import java.util.Map;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class KpiCalculationService {

    private final KpiScoreRepository kpiScoreRepository;
    private final KpiWeeklyScoreRepository kpiWeeklyScoreRepository;
    private final KpiLedgerEntryRepository kpiLedgerEntryRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final com.trilong.kpibackend.modules.notification.service.PushNotificationService pushNotificationService;

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    // ── Trần điểm từng nhóm tiêu chí (mỗi tuần) ─────────────────────────────
    // Theo bảng tiêu chí KPI: 30 + 40 + 30 = 100 điểm/tuần.
    /** Nhóm 1 — Phát triển cá nhân: chuyên cần + học tập/đào tạo */
    public static final int CAP_PERSONAL = 30;
    /** Nhóm 2 — Thực chiến: gặp khách, one-on-one, tăng ca */
    public static final int CAP_BATTLE = 40;
    /** Nhóm 3 — Lan tỏa giá trị: video xây kênh, bài đăng, gieo hạt */
    public static final int CAP_SPREAD = 30;
    /** Tổng tối đa mỗi tuần */
    public static final int CAP_WEEK = 100;

    /** Giới hạn giá trị trong khoảng [0, max] — điểm tuần không bao giờ âm. */
    private int clamp(int value, int max) {
        return Math.max(0, Math.min(max, value));
    }

    @PostConstruct
    public void initMissingKpiScores() {
        String currentMonth = extractMonth(ZonedDateTime.now());
        List<User> activeUsers = userRepository.findAll();
        
        for (User user : activeUsers) {
            if ("ACTIVE".equals(user.getStatus()) && ("SALE".equals(user.getRole()) || "TRUONG_PHONG".equals(user.getRole()))) {
                kpiScoreRepository.findByUserIdAndMonth(user.getId(), currentMonth)
                        .orElseGet(() -> {
                            KpiScore dummy = KpiScore.builder()
                                    .user(user)
                                    .month(currentMonth)
                                    .attendance(0)
                                    .meeting(0)
                                    .post(0)
                                    .deal(0)
                                    .total(0)
                                    .isFlagged(false)
                                    .build();
                            return kpiScoreRepository.save(dummy);
                        });
            }
        }
    }

    /**
     * Xác định "tháng KPI" của một mốc thời gian.
     *
     * <p>Quy tắc công ty: <b>một tuần thuộc trọn về một tháng</b> — tháng chứa
     * ngày THỨ HAI của tuần đó. Nhờ vậy tuần cuối tháng không bị cắt đôi:
     * ví dụ tuần 31/08–06/09/2026 tính trọn cho tháng 8, nên việc làm ngày
     * 01–06/09 vẫn cộng vào KPI tháng 8; tháng 9 bắt đầu từ thứ Hai 07/09.
     *
     * <p>Cách này cũng khớp với {@link #getMaxKpiForMonth(String)} (đếm số ngày
     * thứ Hai trong tháng × 100 → tháng 4 tuần = 400 điểm, 5 tuần = 500 điểm).
     */
    private String monthOfWeek(java.time.LocalDate date) {
        java.time.LocalDate monday = date.with(java.time.temporal.WeekFields.ISO.dayOfWeek(), 1);
        return String.format("%d-%02d", monday.getYear(), monday.getMonthValue());
    }

    public String extractMonth(ZonedDateTime dateTime) {
        ZonedDateTime t = (dateTime == null) ? ZonedDateTime.now(VN_ZONE)
                : dateTime.withZoneSameInstant(VN_ZONE);
        return monthOfWeek(t.toLocalDate());
    }

    public String extractMonth(LocalDateTime dateTime) {
        LocalDateTime t = (dateTime == null) ? LocalDateTime.now() : dateTime;
        return monthOfWeek(t.toLocalDate());
    }

    public String extractMonth(Instant instant) {
        Instant i = (instant == null) ? Instant.now() : instant;
        return monthOfWeek(i.atZone(VN_ZONE).toLocalDate());
    }

    public String getWeekString(ZonedDateTime dateTime) {
        if (dateTime == null) dateTime = ZonedDateTime.now();
        java.time.temporal.WeekFields weekFields = java.time.temporal.WeekFields.ISO;
        int weekNumber = dateTime.get(weekFields.weekOfWeekBasedYear());
        int year = dateTime.get(weekFields.weekBasedYear());
        return String.format("%d-W%02d", year, weekNumber);
    }

    /**
     * Chỉ tiêu KPI của tháng = số TUẦN thuộc tháng đó × 100.
     *
     * <p>Một tuần thuộc tháng nào được xác định bằng ngày thứ Hai của tuần
     * (xem {@link #monthOfWeek}), nên đếm số ngày thứ Hai trong tháng chính là
     * đếm số tuần: tháng 4 tuần → 400 điểm, tháng 5 tuần → 500 điểm.
     */
    public int getMaxKpiForMonth(String monthStr) {
        try {
            java.time.YearMonth ym = java.time.YearMonth.parse(monthStr, MONTH_FORMATTER);
            int mondays = 0;
            for (int i = 1; i <= ym.lengthOfMonth(); i++) {
                if (ym.atDay(i).getDayOfWeek() == java.time.DayOfWeek.MONDAY) {
                    mondays++;
                }
            }
            return mondays * 100;
        } catch (Exception e) {
            return 400; // fallback
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  XẾP LOẠI KPI — chỉ có hai mức: đạt 50% hoặc đạt 100%
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Kết quả xếp loại KPI tháng.
     *
     * @param percent   mức KPI đạt được: 100, 50 hoặc 0
     * @param label     nhãn hiển thị
     * @param reason    lý do (đặc biệt hữu ích khi đạt 100% nhờ chốt căn)
     * @param min50     ngưỡng điểm để đạt 50%
     * @param min100    ngưỡng điểm để đạt 100%
     * @param isNewbie  có đang áp mức nhân sự mới (80%) hay không
     */
    public record KpiGrade(int percent, String label, String reason,
                           int min50, int min100, boolean isNewbie) {}

    /**
     * Xếp loại KPI tháng theo quy định công ty.
     *
     * <p>Ngưỡng điểm (nhân sự chính thức):
     * <ul>
     *   <li>Tháng 4 tuần (tối đa 400đ): từ 250 → đạt 50%; từ 320 → đạt 100%</li>
     *   <li>Tháng 5 tuần (tối đa 500đ): từ 310 → đạt 50%; từ 400 → đạt 100%</li>
     * </ul>
     *
     * <p>Nhân sự mới (2 tháng đầu kể từ ngày tạo tài khoản) chỉ cần đạt
     * <b>80%</b> các ngưỡng trên. Ví dụ tháng 4 tuần: 200 → 50%, 255 → 100%.
     *
     * <p>Chốt được căn (deal đã duyệt trong tháng) → <b>đạt 100% KPI</b> bất kể
     * điểm số, nhưng điểm ba nhóm tiêu chí vẫn ghi nhận bình thường.
     *
     * @param totalPoints  tổng điểm KPI tháng (tổng điểm các tuần)
     * @param month        tháng dạng yyyy-MM
     * @param hasClosedDeal tháng đó có chốt căn được duyệt hay không
     * @param joinedAt     ngày vào làm (để xác định nhân sự mới); null = coi là chính thức
     */
    public KpiGrade gradeMonth(int totalPoints, String month, boolean hasClosedDeal,
                               ZonedDateTime joinedAt) {
        int maxKpi = getMaxKpiForMonth(month);

        // Ngưỡng gốc theo số tuần của tháng
        int base50 = (maxKpi >= 500) ? 310 : 250;
        int base100 = (maxKpi >= 500) ? 400 : 320;

        boolean newbie = isNewStaff(joinedAt, month);
        int min50 = newbie ? (int) Math.round(base50 * 0.8) : base50;
        int min100 = newbie ? (int) Math.round(base100 * 0.8) : base100;

        if (hasClosedDeal) {
            return new KpiGrade(100, "Đạt 100% KPI",
                    "Có chốt căn được duyệt trong tháng", min50, min100, newbie);
        }
        if (totalPoints >= min100) {
            return new KpiGrade(100, "Đạt 100% KPI",
                    "Đạt " + totalPoints + " điểm (ngưỡng " + min100 + ")", min50, min100, newbie);
        }
        if (totalPoints >= min50) {
            return new KpiGrade(50, "Đạt 50% KPI",
                    "Đạt " + totalPoints + " điểm (ngưỡng 100% là " + min100 + ")", min50, min100, newbie);
        }
        return new KpiGrade(0, "Không đạt KPI",
                "Chỉ đạt " + totalPoints + " điểm, chưa tới ngưỡng " + min50, min50, min100, newbie);
    }

    /**
     * Nhân sự mới = trong 2 tháng đầu kể từ ngày tạo tài khoản.
     * Tháng đang xét được so với tháng vào làm (theo quy tắc tuần trọn vẹn).
     */
    public boolean isNewStaff(ZonedDateTime joinedAt, String month) {
        if (joinedAt == null) return false;
        try {
            java.time.YearMonth joinMonth = java.time.YearMonth.parse(
                    extractMonth(joinedAt), MONTH_FORMATTER);
            java.time.YearMonth target = java.time.YearMonth.parse(month, MONTH_FORMATTER);
            long diff = joinMonth.until(target, java.time.temporal.ChronoUnit.MONTHS);
            return diff >= 0 && diff < 2;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Danh sách mã tuần (yyyy-Www) thuộc một tháng KPI, theo thứ tự.
     * Mỗi phần tử ứng với một cột "Tuần N" trên báo cáo.
     */
    public List<String> getWeeksOfMonth(String monthStr) {
        List<String> weeks = new java.util.ArrayList<>();
        try {
            java.time.YearMonth ym = java.time.YearMonth.parse(monthStr, MONTH_FORMATTER);
            for (int i = 1; i <= ym.lengthOfMonth(); i++) {
                java.time.LocalDate d = ym.atDay(i);
                if (d.getDayOfWeek() == java.time.DayOfWeek.MONDAY) {
                    java.time.temporal.WeekFields wf = java.time.temporal.WeekFields.ISO;
                    weeks.add(String.format("%d-W%02d",
                            d.get(wf.weekBasedYear()), d.get(wf.weekOfWeekBasedYear())));
                }
            }
        } catch (Exception ignored) {
            // tháng không hợp lệ → trả danh sách rỗng
        }
        return weeks;
    }

    /**
     * Cộng (hoặc trừ) điểm KPI cho một nhân sự và ghi lại một dòng nhật ký.
     *
     * @param type      nhóm điểm: attendance / meeting / post / deal
     * @param points    số điểm theo quy định, âm nghĩa là trừ
     * @param dienGiai  câu ngắn gọn giải thích khoản điểm này cho nhân sự đọc,
     *                  ví dụ "Chấm công vào lúc 08:12" hay "Admin từ chối bài đăng".
     *                  Bắt buộc — điểm nào cũng phải nói được vì sao có.
     */
    @Transactional
    public KpiScore updateKpiPoints(Long userId, String type, int points,
                                    ZonedDateTime submittedAt, String dienGiai) {
        String month = extractMonth(submittedAt);
        String week = getWeekString(submittedAt);
        String lowerType = type.toLowerCase();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng có ID: " + userId));

        KpiScore kpiScore = kpiScoreRepository.findByUserIdAndMonth(userId, month)
                .orElseGet(() -> KpiScore.builder()
                        .user(user)
                        .month(month)
                        .attendance(0)
                        .meeting(0)
                        .post(0)
                        .deal(0)
                        .total(0)
                        .isFlagged(false)
                        .build());

        // Điểm THỰC NHẬN sau khi áp trần nhóm và chặn số âm. Có thể nhỏ hơn
        // `points` khi nhóm đã kịch trần tuần — nhật ký cần cả hai con số thì
        // nhân sự mới hiểu vì sao làm thêm mà điểm đứng yên.
        int diemThucNhan;

        if (lowerType.equals("deal")) {
            int truoc = kpiScore.getDeal();
            kpiScore.setDeal(Math.max(0, truoc + points));
            diemThucNhan = kpiScore.getDeal() - truoc;
        } else {
            // Update weekly score capped at 100
            KpiWeeklyScore weeklyScore = kpiWeeklyScoreRepository.findByUserIdAndWeek(userId, week)
                    .orElseGet(() -> KpiWeeklyScore.builder()
                            .user(user)
                            .month(month)
                            .week(week)
                            .attendance(0)
                            .meeting(0)
                            .post(0)
                            .total(0)
                            .build());

            // Mỗi nhóm có trần riêng theo bảng tiêu chí; điểm không xuống dưới 0
            // (có điểm mới bị trừ, không có điểm thì không âm).
            switch (lowerType) {
                case "attendance": {
                    int truoc = weeklyScore.getAttendance();
                    weeklyScore.setAttendance(clamp(truoc + points, CAP_PERSONAL));
                    diemThucNhan = weeklyScore.getAttendance() - truoc;
                    break;
                }
                case "meeting": {
                    int truoc = weeklyScore.getMeeting();
                    weeklyScore.setMeeting(clamp(truoc + points, CAP_BATTLE));
                    diemThucNhan = weeklyScore.getMeeting() - truoc;
                    break;
                }
                case "post": {
                    int truoc = weeklyScore.getPost();
                    weeklyScore.setPost(clamp(truoc + points, CAP_SPREAD));
                    diemThucNhan = weeklyScore.getPost() - truoc;
                    break;
                }
                default:
                    throw new IllegalArgumentException("Loại điểm KPI không hợp lệ: " + type);
            }

            int rawWeekly = weeklyScore.getAttendance() + weeklyScore.getMeeting() + weeklyScore.getPost();
            weeklyScore.setTotal(Math.min(CAP_WEEK, rawWeekly));
            kpiWeeklyScoreRepository.save(weeklyScore);
        }

        // Recalculate Monthly total based on Weekly Scores + Deal
        List<KpiWeeklyScore> weeklyScores = kpiWeeklyScoreRepository.findByUserIdAndMonth(userId, month);
        
        int sumAttendance = 0;
        int sumMeeting = 0;
        int sumPost = 0;
        int sumWeeklyTotal = 0;

        for (KpiWeeklyScore ws : weeklyScores) {
            sumAttendance += ws.getAttendance();
            sumMeeting += ws.getMeeting();
            sumPost += ws.getPost();
            sumWeeklyTotal += ws.getTotal();
        }

        kpiScore.setAttendance(sumAttendance);
        kpiScore.setMeeting(sumMeeting);
        kpiScore.setPost(sumPost);

        int maxKpi = getMaxKpiForMonth(month);

        // Điểm tháng = tổng điểm các TUẦN (mỗi tuần tối đa 100).
        // Chốt căn KHÔNG cộng điểm vào đây: theo quy định, chốt được căn thì
        // nghiễm nhiên đạt 100% KPI tháng — xử lý ở khâu xếp loại, còn ba nhóm
        // tiêu chí vẫn được chấm và hiển thị bình thường.
        // (Doanh số và hoa hồng quản lý ở hệ thống khác, không thuộc phạm vi này.)
        kpiScore.setTotal(Math.min(maxKpi, sumWeeklyTotal));

        KpiScore savedScore = kpiScoreRepository.save(kpiScore);

        // Ghi nhật ký để màn hình Thông báo dựng lại được từng khoản điểm.
        // Không chặn luồng chính: nhật ký hỏng thì điểm vẫn phải được cộng.
        try {
            KpiLedgerEntry buocGhi = new KpiLedgerEntry();
            buocGhi.setUserId(userId);
            buocGhi.setCategory(lowerType);
            buocGhi.setPoints(points);
            buocGhi.setEffectivePoints(diemThucNhan);
            buocGhi.setReason(rutGon(dienGiai, 300));
            buocGhi.setWeek(week);
            buocGhi.setMonth(month);
            buocGhi.setOccurredAt(submittedAt != null ? submittedAt : ZonedDateTime.now(VN_ZONE));
            kpiLedgerEntryRepository.save(buocGhi);
        } catch (Exception e) {
            log.warn("[KPI] Không ghi được nhật ký điểm cho userId={}: {}", userId, e.getMessage());
        }

        // Báo thông báo đẩy khi điểm thực sự đổi. Bỏ qua lần điều chỉnh 0đ (ví
        // dụ chấm lại đào tạo tuần mà không có gì thay đổi) để khỏi làm phiền.
        if (diemThucNhan != 0) {
            try {
                boolean cong = diemThucNhan > 0;
                pushNotificationService.guiToiNhanSu(userId,
                        cong ? "Bạn được cộng " + diemThucNhan + "đ KPI"
                             : "Bạn bị trừ " + (-diemThucNhan) + "đ KPI",
                        rutGon(dienGiai, 200),
                        Map.of("type", "kpi"));
            } catch (Exception e) {
                log.warn("[Push] Không gửi được thông báo điểm KPI cho userId={}: {}", userId, e.getMessage());
            }
        }

        int currentWeeklyTotal = kpiWeeklyScoreRepository.findByUserIdAndWeek(userId, getWeekString(ZonedDateTime.now()))
                .map(KpiWeeklyScore::getTotal)
                .orElse(0);

        try {
            // Gửi kèm chính khoản điểm vừa phát sinh: ứng dụng khỏi phải suy ra
            // bằng cách so sánh các con số tổng, và báo được đúng lý do.
            messagingTemplate.convertAndSend(
                "/topic/kpi/" + userId,
                (Object) Map.of(
                    "status", "SUCCESS",
                    "data", KpiScoreResponseDTO.from(savedScore, currentWeeklyTotal, maxKpi),
                    "change", Map.of(
                        "category", lowerType,
                        "points", points,
                        "effectivePoints", diemThucNhan,
                        "reason", rutGon(dienGiai, 300)
                    )
                )
            );
        } catch (Exception e) {
            // Ignore messaging errors
        }

        return savedScore;
    }

    /** Cắt bớt diễn giải cho vừa cột, và không bao giờ để trống. */
    private String rutGon(String s, int max) {
        if (s == null || s.isBlank()) return "Điều chỉnh điểm KPI";
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max - 1) + "…";
    }
}
