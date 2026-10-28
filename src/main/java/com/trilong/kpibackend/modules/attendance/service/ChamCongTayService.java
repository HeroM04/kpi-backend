package com.trilong.kpibackend.modules.attendance.service;

import com.trilong.kpibackend.core.utils.GioVN;
import com.trilong.kpibackend.modules.attendance.entity.CheckinLog;
import com.trilong.kpibackend.modules.attendance.entity.LeaveRequest;
import com.trilong.kpibackend.modules.attendance.repository.CheckinLogRepository;
import com.trilong.kpibackend.modules.attendance.repository.LeaveRequestRepository;
import com.trilong.kpibackend.modules.kpi.service.KpiCalculationService;
import com.trilong.kpibackend.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Admin thêm chấm công tay cho nhân sự — quên chấm công, điện thoại hỏng, mất
 * mạng… Admin chọn người, ngày, giờ vào và / hoặc giờ ra.
 *
 * <p>Mỗi giờ thành một lượt chấm công loại {@link #LOAI}, được duyệt ngay qua
 * đúng đường Admin duyệt chấm công ngoại tuyến ({@link CheckinService#updateStatus})
 * nên điểm tính y như vậy: đúng giờ +5 / muộn −5 một lần mỗi ngày, thứ Bảy
 * không tính, ra từ 20:00 thứ Hai–Sáu được tăng ca.
 *
 * <p>Ngày đó đã bị chốt vắng không phép (đêm hôm đó máy không thấy ai chấm
 * công) thì gỡ luôn: hoàn đúng số điểm đã thực trừ và bỏ dấu vắng trên bảng
 * công — người ta có đi làm, chỉ là chưa chấm được.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChamCongTayService {

    /** Giá trị cột checkin_type của lượt Admin thêm tay. */
    public static final String LOAI = "MANUAL";

    private static final DateTimeFormatter DD_MM = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter DD_MM_YYYY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final CheckinLogRepository checkinLogRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final UserRepository userRepository;
    private final KpiCalculationService kpiCalculationService;
    private final CheckinService checkinService;

    @Transactional
    public List<CheckinLog> themTay(Long userId, LocalDate ngay, LocalTime gioVao, LocalTime gioRa, String ghiChu) {
        if (userId == null) throw new IllegalArgumentException("Chưa chọn nhân sự.");
        if (ngay == null) throw new IllegalArgumentException("Chưa chọn ngày.");
        if (gioVao == null && gioRa == null) {
            throw new IllegalArgumentException("Nhập ít nhất giờ vào hoặc giờ ra.");
        }
        if (gioVao != null && gioRa != null && !gioRa.isAfter(gioVao)) {
            throw new IllegalArgumentException("Giờ ra phải sau giờ vào.");
        }
        if (ghiChu == null || ghiChu.isBlank()) {
            throw new IllegalArgumentException("Nhập lý do thêm chấm công tay.");
        }
        userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhân sự."));

        ZonedDateTime bayGio = ZonedDateTime.now(GioVN.VN);
        ZonedDateTime vao = gioVao == null ? null : ngay.atTime(gioVao.truncatedTo(ChronoUnit.MINUTES)).atZone(GioVN.VN);
        ZonedDateTime ra = gioRa == null ? null : ngay.atTime(gioRa.truncatedTo(ChronoUnit.MINUTES)).atZone(GioVN.VN);
        if ((vao != null && vao.isAfter(bayGio)) || (ra != null && ra.isAfter(bayGio))) {
            throw new IllegalArgumentException("Không thêm chấm công cho thời điểm chưa tới.");
        }

        // Đã có lượt cùng loại, cùng phút (chưa bị từ chối) thì là bấm lặp
        List<CheckinLog> trongNgay = checkinLogRepository.findByUserIdAndCheckinTimeBetween(userId,
                ngay.atStartOfDay(GioVN.VN), ngay.plusDays(1).atStartOfDay(GioVN.VN));
        kiemTraTrung(trongNgay, "CHECK_IN", vao);
        kiemTraTrung(trongNgay, "CHECK_OUT", ra);

        String note = "Admin thêm tay — " + ghiChu.trim();
        List<CheckinLog> daTao = new ArrayList<>();
        if (vao != null) daTao.add(taoVaDuyet(userId, "CHECK_IN", vao, note));
        if (ra != null) daTao.add(taoVaDuyet(userId, "CHECK_OUT", ra, note));

        goVangKhongPhep(userId, ngay);
        log.info("[ChamCongTay] userId={} ngày {}: vào {}, ra {}", userId, ngay, gioVao, gioRa);
        return daTao;
    }

    private void kiemTraTrung(List<CheckinLog> trongNgay, String loai, ZonedDateTime luc) {
        if (luc == null) return;
        for (CheckinLog c : trongNgay) {
            if (!loai.equals(c.getActionType()) || "REJECTED".equals(c.getStatus()) || c.getCheckinTime() == null) continue;
            if (c.getCheckinTime().truncatedTo(ChronoUnit.MINUTES).toInstant().equals(luc.toInstant())) {
                throw new IllegalArgumentException(("CHECK_IN".equals(loai) ? "Giờ vào " : "Giờ ra ")
                        + luc.toLocalTime() + " ngày " + luc.format(DD_MM_YYYY) + " đã có rồi.");
            }
        }
    }

    private CheckinLog taoVaDuyet(Long userId, String loai, ZonedDateTime luc, String note) {
        CheckinLog c = new CheckinLog();
        c.setUserId(userId);
        c.setCheckinTime(luc);
        c.setActionType(loai);
        c.setCheckinType(LOAI);
        c.setStatus("PENDING");
        c.setNote(note);
        c.setAddress("Admin thêm tay trên Web Admin");
        CheckinLog luu = checkinLogRepository.save(c);
        // Duyệt qua đúng đường Admin duyệt để điểm tính y hệt
        return checkinService.updateStatus(luu.getId(), "APPROVED", null);
    }

    /** Ngày đã bị chốt vắng không phép → hoàn đúng số đã trừ, bỏ dấu vắng. */
    private void goVangKhongPhep(Long userId, LocalDate ngay) {
        LeaveRequest r = leaveRequestRepository.findByUserIdAndLeaveDate(userId, ngay).orElse(null);
        if (r == null || !"UNEXCUSED".equals(r.getStatus())) return;

        if (Boolean.TRUE.equals(r.getKpiApplied())) {
            ZonedDateTime trua = ngay.atTime(12, 0).atZone(GioVN.VN);
            int daTru = kpiCalculationService.diemThucDaTru(userId, "attendance",
                    "Vắng không phép ngày " + ngay.format(DD_MM), trua, -LeaveRequestService.KPI_LEAVE_UNEXCUSED);
            if (daTru > 0) {
                kpiCalculationService.updateKpiPoints(userId, "attendance", daTru, trua,
                        "Hoàn điểm vắng không phép ngày " + ngay.format(DD_MM) + " — Admin đã thêm chấm công tay");
            }
        }
        // Bản ghi máy tự tạo thì xóa; vốn là đơn xin vắng bị từ chối thì trả về "từ chối"
        if (r.getReason() == null || r.getReason().startsWith("Hệ thống tự ghi nhận")) {
            leaveRequestRepository.delete(r);
        } else {
            r.setStatus("REJECTED");
            r.setKpiApplied(false);
            leaveRequestRepository.save(r);
        }
    }
}
