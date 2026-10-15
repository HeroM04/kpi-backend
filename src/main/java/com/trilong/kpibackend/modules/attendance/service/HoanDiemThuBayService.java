package com.trilong.kpibackend.modules.attendance.service;

import com.trilong.kpibackend.core.utils.GioVN;
import com.trilong.kpibackend.modules.kpi.entity.KpiLedgerEntry;
import com.trilong.kpibackend.modules.kpi.repository.KpiLedgerEntryRepository;
import com.trilong.kpibackend.modules.kpi.service.KpiCalculationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Đưa điểm chuyên cần của một ngày thứ Bảy đã qua về đúng luật mới.
 *
 * <p>Quy định 15/10/2026: thứ Bảy vẫn chấm công nhưng không tính điểm chuyên
 * cần (CheckinService bỏ qua từ nay). Các thứ Bảy trước đó đã lỡ tính thì chuẩn
 * hóa bằng hàm này:
 * <ul>
 *   <li>Đi muộn bị trừ → HOÀN lại đúng số đã thực trừ.</li>
 *   <li>Đúng giờ được cộng → THU LẠI đúng số đã thực cộng.</li>
 * </ul>
 * "Đã thực" = cột effective_points (nhóm chạm trần hay đang 0đ thì con số thực
 * khác 5), nên không ai bị hoàn hay thu quá phần mình đã nhận.
 *
 * <p>Chỉ ghi thêm dòng vào nhật ký, không sửa dòng cũ — nhân sự thấy cả hai vế
 * trong Thông báo. Chạy lại bao nhiêu lần cũng không trùng: phần đã hoàn / đã thu
 * (dòng mang câu {@link #DAU_HOAN} / {@link #DAU_THU}) và phần Admin đã thu hồi
 * duyệt được trừ ra trước khi tính.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HoanDiemThuBayService {

    static final String DAU_HOAN = "Hoàn điểm đi muộn thứ Bảy ";
    static final String DAU_THU = "Thu lại điểm đúng giờ thứ Bảy ";
    private static final String DAU_THU_HOI = "Admin thu hồi duyệt chấm công";
    private static final DateTimeFormatter NGAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final KpiLedgerEntryRepository kpiLedgerEntryRepository;
    private final KpiCalculationService kpiCalculationService;

    /** Một người: được hoàn bao nhiêu (đi muộn), bị thu lại bao nhiêu (đúng giờ). */
    public record KetQua(Long userId, int hoanDiMuon, int thuLaiDungGio) {}

    @Transactional
    public List<KetQua> chuanHoa(LocalDate ngay) {
        if (ngay.getDayOfWeek() != DayOfWeek.SATURDAY) {
            throw new IllegalArgumentException("Ngày " + ngay.format(NGAY) + " không phải thứ Bảy.");
        }
        String d = ngay.format(NGAY);
        String duoi = " — thứ Bảy không tính điểm chuyên cần";
        String tuan = kpiCalculationService.getWeekString(ngay.atTime(12, 0).atZone(GioVN.VN));

        // Theo người, chỉ các dòng chuyên cần của đúng ngày đó:
        // [0] đã trừ đi muộn, [1] Admin thu hồi đã trả lại, [2] đã hoàn,
        // [3] đã cộng đúng giờ, [4] Admin thu hồi đã gỡ, [5] đã thu lại
        Map<Long, int[]> theoNguoi = new LinkedHashMap<>();
        Map<Long, ZonedDateTime> moc = new LinkedHashMap<>();
        for (KpiLedgerEntry e : kpiLedgerEntryRepository.findByCategoryAndWeekOrderByIdAsc("attendance", tuan)) {
            if (e.getOccurredAt() == null || !ngay.equals(GioVN.ngayCua(e.getOccurredAt()))) continue;
            int thuc = e.getEffectivePoints() == null ? 0 : e.getEffectivePoints();
            String ly = e.getReason() == null ? "" : e.getReason();
            int[] t = theoNguoi.computeIfAbsent(e.getUserId(), k -> new int[6]);
            if (ly.startsWith(DAU_HOAN + d)) {
                t[2] += Math.max(0, thuc);
            } else if (ly.startsWith(DAU_THU + d)) {
                t[5] += Math.max(0, -thuc);
            } else if (ly.startsWith(DAU_THU_HOI)) {
                if (thuc > 0) t[1] += thuc; else t[4] += -thuc;
            } else if (thuc < 0 && ly.contains("Đi muộn")) {
                t[0] += -thuc;
                moc.putIfAbsent(e.getUserId(), e.getOccurredAt());
            } else if (thuc > 0 && ly.contains("đúng giờ")) {
                t[3] += thuc;
                moc.putIfAbsent(e.getUserId(), e.getOccurredAt());
            }
        }

        List<KetQua> ra = new ArrayList<>();
        for (var m : theoNguoi.entrySet()) {
            int[] t = m.getValue();
            int hoan = t[0] - t[1] - t[2];
            int thu = t[3] - t[4] - t[5];
            // Ghi cùng mốc với lần chấm công để vào đúng tuần, đúng tháng KPI đó
            if (hoan > 0) {
                kpiCalculationService.updateKpiPoints(m.getKey(), "attendance", hoan, moc.get(m.getKey()), DAU_HOAN + d + duoi);
            }
            if (thu > 0) {
                kpiCalculationService.updateKpiPoints(m.getKey(), "attendance", -thu, moc.get(m.getKey()), DAU_THU + d + duoi);
            }
            if (hoan > 0 || thu > 0) ra.add(new KetQua(m.getKey(), Math.max(0, hoan), Math.max(0, thu)));
        }
        log.info("[Chuẩn hóa điểm thứ Bảy {}] {} người: {}", d, ra.size(), ra);
        return ra;
    }
}
