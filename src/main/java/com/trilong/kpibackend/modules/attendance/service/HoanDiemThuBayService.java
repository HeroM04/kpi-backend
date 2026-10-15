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
 * Hoàn điểm "đi muộn" đã trừ vào một ngày thứ Bảy.
 *
 * <p>Quy định 15/10/2026: thứ Bảy vẫn chấm công nhưng không tính điểm chuyên
 * cần (CheckinService bỏ qua từ nay). Các ngày thứ Bảy trước đó đã lỡ trừ −5đ
 * đi muộn thì hoàn lại bằng hàm này — đúng số ĐÃ THỰC TRỪ (cột effective_points;
 * nhóm đang 0đ thì lúc đó không trừ được gì nên cũng không hoàn).
 *
 * <p>Chỉ ghi thêm dòng cộng vào nhật ký, không sửa dòng cũ — nhân sự thấy cả hai
 * vế trong Thông báo. Chạy lại bao nhiêu lần cũng không hoàn trùng: phần đã hoàn
 * (dòng mang câu {@link #DAU_CAU}) được trừ ra trước khi tính.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HoanDiemThuBayService {

    static final String DAU_CAU = "Hoàn điểm đi muộn thứ Bảy ";
    private static final DateTimeFormatter NGAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final KpiLedgerEntryRepository kpiLedgerEntryRepository;
    private final KpiCalculationService kpiCalculationService;

    /** Một người được hoàn bao nhiêu điểm. */
    public record KetQua(Long userId, int diemHoan) {}

    @Transactional
    public List<KetQua> hoanDiemDiMuon(LocalDate ngay) {
        if (ngay.getDayOfWeek() != DayOfWeek.SATURDAY) {
            throw new IllegalArgumentException("Ngày " + ngay.format(NGAY) + " không phải thứ Bảy.");
        }
        String nhan = DAU_CAU + ngay.format(NGAY) + " — thứ Bảy không tính điểm chuyên cần";
        String tuan = kpiCalculationService.getWeekString(ngay.atTime(12, 0).atZone(GioVN.VN));

        // Gom theo người: đã trừ đi muộn bao nhiêu, Admin thu hồi duyệt đã trả lại
        // bao nhiêu, đã hoàn bao nhiêu — chỉ các dòng chuyên cần của đúng ngày đó.
        Map<Long, int[]> theoNguoi = new LinkedHashMap<>();   // [đã trừ, đã trả lại, đã hoàn]
        Map<Long, ZonedDateTime> mocTru = new LinkedHashMap<>();
        for (KpiLedgerEntry e : kpiLedgerEntryRepository.findByCategoryAndWeekOrderByIdAsc("attendance", tuan)) {
            if (e.getOccurredAt() == null || !ngay.equals(GioVN.ngayCua(e.getOccurredAt()))) continue;
            int thuc = e.getEffectivePoints() == null ? 0 : e.getEffectivePoints();
            String ly = e.getReason() == null ? "" : e.getReason();
            int[] t = theoNguoi.computeIfAbsent(e.getUserId(), k -> new int[3]);
            if (thuc < 0 && ly.contains("Đi muộn")) {
                t[0] += -thuc;
                mocTru.putIfAbsent(e.getUserId(), e.getOccurredAt());
            } else if (thuc > 0 && ly.startsWith("Admin thu hồi duyệt chấm công")) {
                t[1] += thuc;
            } else if (ly.startsWith(DAU_CAU + ngay.format(NGAY))) {
                t[2] += Math.max(0, thuc);
            }
        }

        List<KetQua> ra = new ArrayList<>();
        for (var m : theoNguoi.entrySet()) {
            int[] t = m.getValue();
            int canHoan = t[0] - t[1] - t[2];
            if (canHoan <= 0) continue;
            // Ghi cùng mốc với lần bị trừ để vào đúng tuần, đúng tháng KPI đó
            kpiCalculationService.updateKpiPoints(m.getKey(), "attendance", canHoan, mocTru.get(m.getKey()), nhan);
            ra.add(new KetQua(m.getKey(), canHoan));
        }
        log.info("[Hoàn điểm thứ Bảy {}] {} người: {}", ngay.format(NGAY), ra.size(), ra);
        return ra;
    }
}
