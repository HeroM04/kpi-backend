package com.trilong.kpibackend.modules.kpi.service;

import com.trilong.kpibackend.modules.kpi.entity.KpiLedgerEntry;
import com.trilong.kpibackend.modules.kpi.repository.KpiLedgerEntryRepository;
import com.trilong.kpibackend.modules.kpi.repository.KpiScoreRepository;
import com.trilong.kpibackend.modules.kpi.repository.KpiWeeklyScoreRepository;
import com.trilong.kpibackend.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Đọc nhật ký điểm KPI cho màn hình Thông báo: gom theo tuần hoặc theo tháng,
 * kèm tổng cộng / tổng trừ và điểm chốt của kỳ để nhân sự tự đối chiếu.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KpiLedgerService {

    private final KpiLedgerEntryRepository ledgerRepository;
    private final KpiWeeklyScoreRepository weeklyScoreRepository;
    private final KpiScoreRepository kpiScoreRepository;
    private final UserRepository userRepository;
    private final KpiCalculationService kpiCalculationService;

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter NGAY = DateTimeFormatter.ofPattern("dd/MM");

    /**
     * Tên hiển thị của từng nhóm điểm, dùng chung cho ứng dụng và web.
     *
     * <p>Dùng đúng tên ba nhóm trong bảng tiêu chí KPI của công ty. Khi nhân sự
     * thắc mắc điểm, họ và Admin phải nhìn thấy cùng một cách gọi tên như trong
     * bảng tiêu chí, không phải một cách gọi riêng của phần mềm.
     */
    public static String nhanNhom(String category) {
        if (category == null) return "Khác";
        return switch (category.toLowerCase()) {
            // Nhóm 1 (trần 30đ/tuần): chuyên cần + học tập, đào tạo nhóm
            case "attendance" -> "Phát triển cá nhân";
            // Nhóm 2 (trần 40đ/tuần): gặp khách, đào tạo 1-1, tăng ca
            case "meeting" -> "Thực chiến";
            // Nhóm 3 (trần 30đ/tuần): video xây kênh, bài đăng, gieo hạt
            case "post" -> "Lan tỏa";
            case "deal" -> "Chốt căn";
            default -> "Khác";
        };
    }

    /**
     * Lấy nhật ký một kỳ.
     *
     * @param loai   "week" hoặc "month"
     * @param lui    số kỳ lùi về trước: 0 là kỳ hiện tại, 1 là kỳ liền trước…
     */
    public Map<String, Object> nhatKy(Long userId, String loai, int lui) {
        boolean theoTuan = !"month".equalsIgnoreCase(loai);
        int buoc = Math.max(0, lui);

        String maKy;
        String nhan;
        List<KpiLedgerEntry> banGhi;
        Integer diemKy;
        Integer tranKy;

        if (theoTuan) {
            LocalDate mocTrongTuan = LocalDate.now(VN_ZONE).minusWeeks(buoc);
            LocalDate thuHai = mocTrongTuan.with(WeekFields.ISO.dayOfWeek(), 1);
            maKy = maTuan(thuHai);
            nhan = "Tuần " + thuHai.get(WeekFields.ISO.weekOfWeekBasedYear())
                    + " · " + thuHai.format(NGAY) + " – " + thuHai.plusDays(6).format(NGAY);
            banGhi = ledgerRepository.findByUserIdAndWeekOrderByOccurredAtDesc(userId, maKy);
            diemKy = weeklyScoreRepository.findByUserIdAndWeek(userId, maKy)
                    .map(w -> w.getTotal()).orElse(0);
            tranKy = KpiCalculationService.CAP_WEEK;
        } else {
            // Tháng KPI theo quy tắc tuần trọn vẹn, nên lùi tháng bằng cách lùi
            // từ mã tháng hiện tại chứ không lùi từ ngày hôm nay.
            YearMonth thangHienTai = YearMonth.parse(
                    kpiCalculationService.extractMonth(ZonedDateTime.now(VN_ZONE)),
                    DateTimeFormatter.ofPattern("yyyy-MM"));
            YearMonth ym = thangHienTai.minusMonths(buoc);
            maKy = String.format("%d-%02d", ym.getYear(), ym.getMonthValue());
            nhan = "Tháng " + ym.getMonthValue() + "/" + ym.getYear();
            banGhi = ledgerRepository.findByUserIdAndMonthOrderByOccurredAtDesc(userId, maKy);
            diemKy = kpiScoreRepository.findByUserIdAndMonth(userId, maKy)
                    .map(s -> s.getTotal()).orElse(0);
            tranKy = kpiCalculationService.getMaxKpiForMonth(maKy);
        }

        int tongCong = 0;
        int tongTru = 0;
        Map<String, Integer> theoNhom = new LinkedHashMap<>();
        List<Map<String, Object>> items = new ArrayList<>();

        for (KpiLedgerEntry e : banGhi) {
            int thuc = e.getEffectivePoints() == null ? 0 : e.getEffectivePoints();
            if (thuc > 0) tongCong += thuc;
            else tongTru += thuc;
            theoNhom.merge(e.getCategory(), thuc, Integer::sum);

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", e.getId());
            item.put("category", e.getCategory());
            item.put("categoryLabel", nhanNhom(e.getCategory()));
            item.put("points", e.getPoints());
            item.put("effectivePoints", thuc);
            // Chạm trần: quy định cho điểm nhưng nhóm đã đầy nên không vào được hết.
            item.put("capped", e.getPoints() != null && !e.getPoints().equals(thuc));
            item.put("reason", e.getReason());
            item.put("occurredAt", e.getOccurredAt());
            item.put("createdAt", e.getCreatedAt());
            items.add(item);
        }

        List<Map<String, Object>> nhom = new ArrayList<>();
        for (Map.Entry<String, Integer> en : theoNhom.entrySet()) {
            nhom.add(Map.of(
                    "category", en.getKey(),
                    "label", nhanNhom(en.getKey()),
                    "points", en.getValue()));
        }

        String cuNhat = theoTuan ? ledgerRepository.tuanCuNhat(userId) : ledgerRepository.thangCuNhat(userId);
        boolean conCuHon = cuNhat != null && cuNhat.compareTo(maKy) < 0;

        Map<String, Object> kq = new LinkedHashMap<>();
        kq.put("type", theoTuan ? "week" : "month");
        kq.put("offset", buoc);
        kq.put("periodKey", maKy);
        kq.put("periodLabel", nhan);
        kq.put("isCurrent", buoc == 0);
        kq.put("hasOlder", conCuHon);
        kq.put("totalPlus", tongCong);
        kq.put("totalMinus", tongTru);
        kq.put("net", tongCong + tongTru);
        kq.put("periodScore", diemKy);
        kq.put("periodMax", tranKy);
        kq.put("byCategory", nhom);
        kq.put("items", items);
        return kq;
    }

    /** Số khoản điểm phát sinh từ lần mở màn hình Thông báo gần nhất. */
    public long soChuaDoc(Long userId) {
        return userRepository.findById(userId)
                .map(u -> u.getKpiNotificationsSeenAt() == null
                        ? ledgerRepository.countByUserId(userId)
                        : ledgerRepository.countByUserIdAndCreatedAtAfter(userId, u.getKpiNotificationsSeenAt()))
                .orElse(0L);
    }

    /** Đánh dấu đã xem hết — gọi khi nhân sự mở màn hình Thông báo. */
    @Transactional
    public void danhDauDaXem(Long userId) {
        userRepository.findById(userId).ifPresent(u -> {
            u.setKpiNotificationsSeenAt(ZonedDateTime.now(VN_ZONE));
            userRepository.save(u);
        });
    }

    private String maTuan(LocalDate d) {
        return String.format("%d-W%02d",
                d.get(WeekFields.ISO.weekBasedYear()),
                d.get(WeekFields.ISO.weekOfWeekBasedYear()));
    }
}
