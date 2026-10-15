package com.trilong.kpibackend.modules.attendance.service;

import com.trilong.kpibackend.core.utils.GioVN;
import com.trilong.kpibackend.modules.attendance.entity.LeaveRequest;
import com.trilong.kpibackend.modules.attendance.repository.LeaveRequestRepository;
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
 * <p>Quy định 15/10/2026: thứ Bảy vẫn chấm công, vẫn ghi vắng có / không phép
 * trên bảng công như ngày thường, chỉ <b>không cộng / trừ điểm KPI</b>
 * (CheckinService, LeaveRequestService bỏ phần điểm từ nay). Các thứ Bảy trước
 * đó đã lỡ tính điểm thì chuẩn hóa bằng hàm này:
 * <ul>
 *   <li>Đi muộn bị trừ → HOÀN lại đúng số đã thực trừ.</li>
 *   <li>Đúng giờ được cộng → THU LẠI đúng số đã thực cộng.</li>
 *   <li>Vắng không phép (−15) / nghỉ có phép (−10) → HOÀN lại đúng số đã thực
 *       trừ. Bảng công giữ nguyên.</li>
 * </ul>
 * "Đã thực" = cột effective_points (nhóm chạm trần hay đang 0đ thì con số thực
 * khác 5 / 10 / 15), nên không ai được hoàn hay bị thu quá phần mình đã nhận.
 *
 * <p>Chỉ ghi thêm dòng vào nhật ký, không sửa dòng cũ — nhân sự thấy cả hai vế
 * trong Thông báo. Chạy lại bao nhiêu lần cũng không trùng: phần đã hoàn / đã thu
 * (dòng mang câu {@link #DAU_HOAN} / {@link #DAU_THU} / {@link #DAU_HOAN_VANG}),
 * phần Admin đã thu hồi duyệt và các khoản hoàn đơn nghỉ đã có được trừ ra
 * trước khi tính.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HoanDiemThuBayService {

    static final String DAU_HOAN = "Hoàn điểm đi muộn thứ Bảy ";
    static final String DAU_THU = "Thu lại điểm đúng giờ thứ Bảy ";
    static final String DAU_HOAN_VANG = "Hoàn điểm vắng thứ Bảy ";
    private static final String DAU_THU_HOI = "Admin thu hồi duyệt chấm công";
    private static final DateTimeFormatter NGAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final KpiLedgerEntryRepository kpiLedgerEntryRepository;
    private final KpiCalculationService kpiCalculationService;
    private final LeaveRequestRepository leaveRequestRepository;

    /** Một người: được hoàn bao nhiêu (đi muộn, vắng), bị thu lại bao nhiêu (đúng giờ). */
    public record KetQua(Long userId, int hoanDiMuon, int thuLaiDungGio, int hoanVang) {}

    @Transactional
    public List<KetQua> chuanHoa(LocalDate ngay) {
        if (ngay.getDayOfWeek() != DayOfWeek.SATURDAY) {
            throw new IllegalArgumentException("Ngày " + ngay.format(NGAY) + " không phải thứ Bảy.");
        }
        String d = ngay.format(NGAY);
        String tuan = kpiCalculationService.getWeekString(ngay.atTime(12, 0).atZone(GioVN.VN));

        // Theo người, chỉ các dòng chuyên cần của đúng ngày đó:
        // [0] đã trừ đi muộn, [1] Admin thu hồi đã trả lại, [2] đã hoàn,
        // [3] đã cộng đúng giờ, [4] Admin thu hồi đã gỡ, [5] đã thu lại,
        // [6] đã trừ vắng (có/không phép), [7] đã hoàn vắng (gửi đơn, từ chối, hoàn thứ Bảy)
        Map<Long, int[]> theoNguoi = new LinkedHashMap<>();
        Map<Long, ZonedDateTime> moc = new LinkedHashMap<>();
        for (KpiLedgerEntry e : kpiLedgerEntryRepository.findByCategoryAndWeekOrderByIdAsc("attendance", tuan)) {
            if (e.getOccurredAt() == null || !ngay.equals(GioVN.ngayCua(e.getOccurredAt()))) continue;
            int thuc = e.getEffectivePoints() == null ? 0 : e.getEffectivePoints();
            String ly = e.getReason() == null ? "" : e.getReason();
            int[] t = theoNguoi.computeIfAbsent(e.getUserId(), k -> new int[8]);
            if (ly.startsWith(DAU_HOAN + d)) {
                t[2] += Math.max(0, thuc);
            } else if (ly.startsWith(DAU_THU + d)) {
                t[5] += Math.max(0, -thuc);
            } else if (ly.startsWith(DAU_HOAN_VANG + d)) {
                t[7] += Math.max(0, thuc);
            } else if (ly.startsWith(DAU_THU_HOI)) {
                if (thuc > 0) t[1] += thuc; else t[4] += -thuc;
            } else if (ly.startsWith("Vắng không phép ngày") || ly.startsWith("Nghỉ có phép ngày")) {
                t[6] += Math.max(0, -thuc);
                moc.putIfAbsent(e.getUserId(), e.getOccurredAt());
            } else if (ly.startsWith("Hoàn lại điểm đơn nghỉ ngày") || ly.startsWith("Đã gửi đơn xin vắng ngày")
                    || ly.startsWith("Hoàn điểm đơn nghỉ thứ Bảy")) {
                t[7] += Math.max(0, thuc);
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
            int hoanVang = t[6] - t[7];
            ZonedDateTime luc = moc.get(m.getKey());
            // Ghi cùng mốc với khoản gốc để vào đúng tuần, đúng tháng KPI đó
            if (hoan > 0) {
                kpiCalculationService.updateKpiPoints(m.getKey(), "attendance", hoan, luc,
                        DAU_HOAN + d + " — thứ Bảy không tính điểm chuyên cần");
            }
            if (thu > 0) {
                kpiCalculationService.updateKpiPoints(m.getKey(), "attendance", -thu, luc,
                        DAU_THU + d + " — thứ Bảy không tính điểm chuyên cần");
            }
            if (hoanVang > 0) {
                kpiCalculationService.updateKpiPoints(m.getKey(), "attendance", hoanVang, luc,
                        DAU_HOAN_VANG + d + " — thứ Bảy nghỉ không bị trừ điểm");
            }
            if (hoan > 0 || thu > 0 || hoanVang > 0) {
                ra.add(new KetQua(m.getKey(), Math.max(0, hoan), Math.max(0, thu), Math.max(0, hoanVang)));
            }
        }

        int boCo = boCoDaTru(ngay);
        log.info("[Chuẩn hóa điểm thứ Bảy {}] {} người: {} — {} bản ghi vắng bỏ cờ đã trừ", d, ra.size(), ra, boCo);
        return ra;
    }

    /**
     * Bảng công giữ nguyên (thứ Bảy vẫn chấm công, vẫn ghi có phép / không
     * phép) — chỉ bỏ cờ "đã trừ điểm" của các bản ghi vắng ngày đó, để app hiện
     * không trừ điểm và không chỗ nào hoàn lại lần nữa.
     */
    private int boCoDaTru(LocalDate ngay) {
        int dem = 0;
        for (LeaveRequest r : leaveRequestRepository.findByLeaveDate(ngay)) {
            if (Boolean.TRUE.equals(r.getKpiApplied())) {
                r.setKpiApplied(false);
                leaveRequestRepository.save(r);
                dem++;
            }
        }
        return dem;
    }
}
