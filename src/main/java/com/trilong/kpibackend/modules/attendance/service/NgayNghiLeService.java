package com.trilong.kpibackend.modules.attendance.service;

import com.trilong.kpibackend.modules.attendance.entity.LeaveRequest;
import com.trilong.kpibackend.modules.attendance.entity.NgayNghiLe;
import com.trilong.kpibackend.modules.attendance.repository.LeaveRequestRepository;
import com.trilong.kpibackend.modules.attendance.repository.NgayNghiLeRepository;
import com.trilong.kpibackend.modules.kpi.service.KpiCalculationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Danh sách ngày nghỉ lễ và việc hủy vắng mặt của ngày lễ.
 *
 * <p>Thêm một ngày lễ ĐÃ QUA (máy đã chốt vắng rồi mới biết là ngày nghỉ) thì
 * hoàn ngay điểm đã trừ của ngày đó — đúng số đã thực trừ theo nhật ký điểm (nhóm
 * đang 0đ thì −15 có khi không trừ được gì, hoàn cũng là 0 nhưng vẫn ghi một dòng
 * để nhân sự thấy ngày đó đã được hủy vắng):
 * <ul>
 *   <li>Vắng không phép do máy tự ghi → hoàn điểm, xóa bản ghi (bảng công ghi "L").</li>
 *   <li>Vắng không phép vì đơn xin vắng bị từ chối → hoàn điểm, đơn về "Từ chối".</li>
 *   <li>Đơn nghỉ có phép đã bị trừ −10 (xin trong ngày) → hoàn điểm, đơn giữ "Có phép".</li>
 * </ul>
 * Chạy lại cho cùng ngày không hoàn trùng: bản ghi đã xóa / cờ đã trừ đã về false.
 *
 * <p>Xóa một ngày lễ không trừ lại điểm của ngày đã qua — chỉ là từ nay ngày đó
 * không còn được miễn chốt vắng.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NgayNghiLeService {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String NHOM = "attendance";

    private final NgayNghiLeRepository ngayNghiLeRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final KpiCalculationService kpiCalculationService;

    public record KetQuaThemLe(NgayNghiLe ngayLe, int soNguoiHoan, int tongDiemHoan) {}

    public List<NgayNghiLe> danhSach() {
        return ngayNghiLeRepository.findAllByOrderByNgayAsc();
    }

    public boolean laNgayLe(LocalDate ngay) {
        return ngay != null && ngayNghiLeRepository.existsByNgay(ngay);
    }

    @Transactional
    public KetQuaThemLe them(LocalDate ngay, String ten) {
        if (ngay == null) throw new IllegalArgumentException("Chọn ngày nghỉ lễ.");
        String tenLe = (ten == null || ten.isBlank()) ? "Ngày nghỉ lễ" : ten.trim();
        if (tenLe.length() > 200) tenLe = tenLe.substring(0, 200);

        NgayNghiLe le = ngayNghiLeRepository.findByNgay(ngay).orElse(null);
        if (le == null) {
            le = ngayNghiLeRepository.save(new NgayNghiLe(ngay, tenLe));
        } else if (ten != null && !ten.isBlank() && !tenLe.equals(le.getTen())) {
            le.setTen(tenLe);
            le = ngayNghiLeRepository.save(le);
        }

        int[] kq = ngay.isAfter(LocalDate.now(VN_ZONE)) ? new int[]{0, 0} : huyVangNgay(ngay, le.getTen());
        log.info("[Ngày lễ] {} ({}): hoàn điểm vắng cho {} nhân sự, tổng {}đ.", ngay, le.getTen(), kq[0], kq[1]);
        return new KetQuaThemLe(le, kq[0], kq[1]);
    }

    @Transactional
    public void xoa(LocalDate ngay) {
        ngayNghiLeRepository.findByNgay(ngay).ifPresent(ngayNghiLeRepository::delete);
    }

    /** @return {số nhân sự được hoàn, tổng điểm hoàn} */
    private int[] huyVangNgay(LocalDate ngay, String tenLe) {
        String dd = ngay.format(DateTimeFormatter.ofPattern("dd/MM"));
        ZonedDateTime moc = ngay.atTime(12, 0).atZone(VN_ZONE);
        String dienGiai = "Hoàn điểm vắng ngày " + dd + " — " + tenLe + ", ngày nghỉ của công ty";
        int soNguoi = 0, tong = 0;

        for (LeaveRequest r : leaveRequestRepository.findByLeaveDate(ngay)) {
            boolean daTruDiem = Boolean.TRUE.equals(r.getKpiApplied());
            if ("UNEXCUSED".equals(r.getStatus())) {
                if (daTruDiem) {
                    int daTru = kpiCalculationService.diemThucDaTru(r.getUserId(), NHOM,
                            "Vắng không phép ngày " + dd, moc, -LeaveRequestService.KPI_LEAVE_UNEXCUSED);
                    kpiCalculationService.updateKpiPoints(r.getUserId(), NHOM, daTru, moc, dienGiai);
                    soNguoi++;
                    tong += daTru;
                }
                boolean mayGhi = r.getReason() == null || r.getReason().startsWith(LeaveRequestService.LY_DO_MAY_GHI);
                if (mayGhi) {
                    leaveRequestRepository.delete(r);
                } else {
                    r.setStatus("REJECTED");   // đơn xin vắng bị từ chối, ngày lễ thì không còn là vắng
                    r.setKpiApplied(false);
                    leaveRequestRepository.save(r);
                }
            } else if ("APPROVED".equals(r.getStatus()) && daTruDiem) {
                int daTru = kpiCalculationService.diemThucDaTru(r.getUserId(), NHOM,
                        "Nghỉ có phép ngày " + dd, moc, -LeaveRequestService.KPI_LEAVE_APPROVED);
                kpiCalculationService.updateKpiPoints(r.getUserId(), NHOM, daTru, moc, dienGiai);
                r.setKpiApplied(false);
                leaveRequestRepository.save(r);
                soNguoi++;
                tong += daTru;
            }
        }
        return new int[]{soNguoi, tong};
    }
}
