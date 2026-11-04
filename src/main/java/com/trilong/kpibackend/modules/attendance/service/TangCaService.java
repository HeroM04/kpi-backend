package com.trilong.kpibackend.modules.attendance.service;

import com.trilong.kpibackend.core.utils.GioVN;
import com.trilong.kpibackend.modules.attendance.repository.CheckinLogRepository;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Điểm tăng ca: chấm công ra từ 20:00 thứ Hai–Sáu được +5 nhóm Thực chiến,
 * <b>mỗi ngày một lần</b>.
 *
 * <p>Trước 04/11/2026 mỗi lượt chấm công ra sau 20:00 đều được +5: bấm Xác nhận
 * hai lần hay ra về rồi chấm lại là được tăng ca hai lần trong một ngày; Admin
 * thu hồi lượt về cũng không lấy lại điểm tăng ca. Lớp này giữ luật một lần mỗi
 * ngày và sửa lại các ngày đã lỡ tính sai.
 *
 * <p>Nhật ký chỉ ghi thêm, không sửa dòng cũ — nhân sự thấy cả hai vế trong
 * Thông báo. Thu lại tính trên số điểm ĐÃ THỰC VÀO (effective_points; nhóm chạm
 * trần thì có khi dưới 5) nên không ai bị thu quá phần mình đã nhận.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TangCaService {

    /** Đầu câu khoản cộng tăng ca — {@link CheckinService} ghi "Tăng ca — về lúc HH:mm". */
    public static final String DAU_CONG = "Tăng ca — về lúc ";
    /** Đầu câu khoản thu lại, nối ngày dd/MM/yyyy được tính. */
    static final String DAU_THU_LAI = "Thu lại điểm tăng ca ngày ";
    static final String VI_TRUNG = "mỗi ngày chỉ tính tăng ca một lần";
    static final String VI_THU_HOI = "lượt về đã bị thu hồi";
    private static final int DIEM_TANG_CA = 5;
    private static final String NHOM = "meeting";
    private static final DateTimeFormatter NGAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final CheckinLogRepository checkinLogRepository;
    private final KpiLedgerEntryRepository ledgerRepository;
    private final KpiCalculationService kpiCalculationService;

    /**
     * Một ngày của một người đang có điểm tăng ca nhiều hơn mức đúng.
     *
     * @param dangCo điểm tăng ca ngày đó đang có (đã cộng trừ phần đã thu lại)
     * @param dungRa mức đúng: một lần tăng ca, hoặc 0 nếu không còn lượt về nào
     */
    public record NgayThua(Long userId, LocalDate ngay, int dangCo, int dungRa, String lyDo) {
        public int thua() { return dangCo - dungRa; }
    }

    /** Số liệu tăng ca của một người trong một ngày, đọc từ nhật ký điểm. */
    private record SoLieu(int dangCo, int lanCongCaoNhat, ZonedDateTime moc) {}

    /**
     * Ngày đó người này còn lượt về nào ĐÃ DUYỆT, đủ giờ tăng ca, ngoài lượt
     * {@code boQuaId} không — có tức là ngày đó đã (hoặc vẫn) được tăng ca.
     */
    public boolean conLuotVeTangCa(Long userId, LocalDate ngay, Long boQuaId) {
        return checkinLogRepository.findByUserIdAndCheckinTimeBetween(userId,
                        ngay.atStartOfDay(GioVN.VN), ngay.plusDays(1).atStartOfDay(GioVN.VN))
                .stream()
                .anyMatch(c -> "APPROVED".equals(c.getStatus())
                        && (boQuaId == null || !boQuaId.equals(c.getId()))
                        && CheckinService.duDieuKienTangCa(c.getActionType(), c.getCheckinTime()));
    }

    /**
     * Admin vừa thu hồi một lượt về: ngày đó không còn lượt về nào đủ giờ tăng
     * ca thì thu lại điểm tăng ca ngày ấy đang có. Còn lượt khác thì ngày đó vẫn
     * được tăng ca — giữ nguyên.
     *
     * @return số điểm đã thu lại (0 nếu không thu gì)
     */
    @Transactional
    public int thuLaiNeuHetLuot(Long userId, LocalDate ngay) {
        if (conLuotVeTangCa(userId, ngay, null)) return 0;
        String tuan = kpiCalculationService.getWeekString(ngay.atTime(12, 0).atZone(GioVN.VN));
        SoLieu so = tinh(ledgerRepository.findByUserIdAndCategoryAndWeekOrderByIdAsc(userId, NHOM, tuan), ngay);
        if (so == null || so.dangCo() <= 0) return 0;
        thuLai(userId, ngay, so.dangCo(), so.moc(), VI_THU_HOI);
        return so.dangCo();
    }

    /**
     * Rà các ngày từ {@code tu} tới {@code den}: ai được tăng ca quá một lần
     * trong một ngày, hoặc vẫn còn điểm tăng ca dù lượt về đã bị thu hồi.
     * Chạy lại bao nhiêu lần cũng không thu trùng — phần đã thu lại được trừ ra
     * trước khi tính.
     *
     * @param apDung false = chỉ xem, không ghi gì; true = thu lại phần thừa
     */
    @Transactional
    public List<NgayThua> chuanHoa(LocalDate tu, LocalDate den, boolean apDung) {
        // Gom khoản cộng / thu lại tăng ca theo (người, ngày) của cả tuần một lượt
        Map<String, List<KpiLedgerEntry>> theoNguoiNgay = new LinkedHashMap<>();
        for (LocalDate thuHai = tu.with(DayOfWeek.MONDAY); !thuHai.isAfter(den); thuHai = thuHai.plusWeeks(1)) {
            String tuan = kpiCalculationService.getWeekString(thuHai.atTime(12, 0).atZone(GioVN.VN));
            for (KpiLedgerEntry e : ledgerRepository.findByCategoryAndWeekOrderByIdAsc(NHOM, tuan)) {
                if (e.getOccurredAt() == null || e.getReason() == null) continue;
                LocalDate ngay = GioVN.ngayCua(e.getOccurredAt());
                if (ngay.isBefore(tu) || ngay.isAfter(den)) continue;
                theoNguoiNgay.computeIfAbsent(e.getUserId() + "|" + ngay, k -> new ArrayList<>()).add(e);
            }
        }

        List<NgayThua> ra = new ArrayList<>();
        for (List<KpiLedgerEntry> dong : theoNguoiNgay.values()) {
            Long userId = dong.get(0).getUserId();
            LocalDate ngay = GioVN.ngayCua(dong.get(0).getOccurredAt());
            SoLieu so = tinh(dong, ngay);
            if (so == null || so.dangCo() <= 0) continue;

            boolean conLuot = conLuotVeTangCa(userId, ngay, null);
            int dungRa = conLuot ? Math.min(DIEM_TANG_CA, so.lanCongCaoNhat()) : 0;
            if (so.dangCo() <= dungRa) continue;

            NgayThua t = new NgayThua(userId, ngay, so.dangCo(), dungRa, conLuot ? VI_TRUNG : VI_THU_HOI);
            ra.add(t);
            if (apDung) thuLai(userId, ngay, t.thua(), so.moc(), t.lyDo());
        }
        ra.sort(Comparator.comparing(NgayThua::ngay).thenComparing(NgayThua::userId));
        log.info("[Tăng ca trùng {}–{}] {} ngày-người thừa điểm, {}", tu, den, ra.size(),
                apDung ? "đã thu lại" : "chỉ xem");
        return ra;
    }

    /**
     * Điểm tăng ca một người đang có trong một ngày. Phần đã thu lại tính theo
     * số YÊU CẦU (points) chứ không theo số thực trừ: điểm tuần chạm sàn 0 thì
     * khoản thu thực trừ ít hơn, tính theo số thực thì lần chạy sau lại thu nữa.
     *
     * @return null nếu ngày đó không có khoản tăng ca nào
     */
    private SoLieu tinh(List<KpiLedgerEntry> dong, LocalDate ngay) {
        String dauThu = DAU_THU_LAI + ngay.format(NGAY);
        int cong = 0, daThu = 0, caoNhat = 0;
        ZonedDateTime moc = null;
        for (KpiLedgerEntry e : dong) {
            if (e.getOccurredAt() == null || !ngay.equals(GioVN.ngayCua(e.getOccurredAt()))) continue;
            String ly = e.getReason() == null ? "" : e.getReason();
            if (ly.startsWith(DAU_CONG)) {
                int thuc = e.getEffectivePoints() == null ? 0 : Math.max(0, e.getEffectivePoints());
                cong += thuc;
                caoNhat = Math.max(caoNhat, thuc);
                if (moc == null) moc = e.getOccurredAt();
            } else if (ly.startsWith(dauThu)) {
                daThu += e.getPoints() == null ? 0 : Math.max(0, -e.getPoints());
            }
        }
        return moc == null ? null : new SoLieu(cong - daThu, caoNhat, moc);
    }

    /** Ghi khoản thu lại cùng mốc với khoản cộng để vào đúng tuần, đúng tháng đó. */
    private void thuLai(Long userId, LocalDate ngay, int diem, ZonedDateTime moc, String vi) {
        kpiCalculationService.updateKpiPoints(userId, NHOM, -diem, moc,
                DAU_THU_LAI + ngay.format(NGAY) + " — " + vi);
        log.info("[Tăng ca] Thu lại {}đ của userId={} ngày {} — {}", diem, userId, ngay, vi);
    }
}
