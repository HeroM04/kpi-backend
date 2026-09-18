package com.trilong.kpibackend.modules.attendance.service;

import com.trilong.kpibackend.modules.attendance.entity.CheckinLog;
import com.trilong.kpibackend.modules.attendance.entity.LeaveRequest;
import com.trilong.kpibackend.modules.attendance.repository.CheckinLogRepository;
import com.trilong.kpibackend.modules.attendance.repository.LeaveRequestRepository;
import com.trilong.kpibackend.modules.user.entity.User;
import com.trilong.kpibackend.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BẢNG CÔNG THÁNG — lưới người × ngày.
 *
 * Danh sách chấm công là danh sách SỰ KIỆN: trăm người, mỗi ngày hai lần bấm,
 * một tháng vài nghìn dòng. Không ai đọc chấm công theo cách đó; kế toán và
 * nhân sự đọc theo LƯỚI: mỗi người một hàng, mỗi ngày một ô, một ký hiệu. Cả
 * công ty một tháng nằm trong một màn hình.
 *
 * Ký hiệu ô:
 *   V  đúng giờ (có chấm công được duyệt, giờ vào đúng mốc)
 *   M  muộn
 *   ?  có lần chấm công đang chờ duyệt (ngoài văn phòng)
 *   R  chấm công bị từ chối, không có lần nào được duyệt
 *   P  vắng có phép (đơn được duyệt)
 *   X  vắng không phép (hệ thống chốt đêm)
 *   -  Chủ nhật
 *   (rỗng) chưa tới ngày, hoặc trước ngày vào làm, hoặc chưa có dữ liệu
 *
 * Ưu tiên khi một ngày có nhiều thứ: đơn nghỉ có phép > chờ duyệt > có chấm
 * công duyệt > bị từ chối > vắng không phép. Đơn nghỉ đặt trước chấm công vì
 * đã xin nghỉ được duyệt thì ngày đó tính nghỉ, có bấm nhầm cũng không tính công.
 */
@Service
@RequiredArgsConstructor
public class BangCongService {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter GIO = DateTimeFormatter.ofPattern("HH:mm");

    private final CheckinLogRepository checkinLogRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final UserRepository userRepository;
    private final CheckinService checkinService;

    public Map<String, Object> bangCongThang(String thang, Long departmentId) {
        YearMonth ym = YearMonth.parse(thang);
        LocalDate dau = ym.atDay(1), cuoi = ym.atEndOfMonth();
        LocalDate homNay = LocalDate.now(VN_ZONE);

        // Người: đang làm, đúng phòng (nếu lọc), xếp theo phòng rồi tên
        List<User> nguoi = userRepository.findAll().stream()
                .filter(u -> "ACTIVE".equals(u.getStatus()))
                .filter(u -> !"ADMIN".equals(u.getRole()))
                .filter(u -> departmentId == null
                        || (u.getDepartment() != null && departmentId.equals(u.getDepartment().getId())))
                .sorted((a, b) -> {
                    String pa = a.getDepartment() != null ? a.getDepartment().getName() : "";
                    String pb = b.getDepartment() != null ? b.getDepartment().getName() : "";
                    int c = pa.compareTo(pb);
                    return c != 0 ? c : a.getFullName().compareTo(b.getFullName());
                })
                .toList();

        // Chấm công cả tháng, một truy vấn — vài nghìn dòng, gộp trong bộ nhớ
        ZonedDateTime tuLuc = dau.atStartOfDay(VN_ZONE);
        ZonedDateTime denLuc = cuoi.plusDays(1).atStartOfDay(VN_ZONE);
        Map<Long, Map<LocalDate, List<CheckinLog>>> chamCong = new HashMap<>();
        for (CheckinLog c : checkinLogRepository.findByCheckinTimeBetween(tuLuc, denLuc)) {
            LocalDate ngay = c.getCheckinTime().withZoneSameInstant(VN_ZONE).toLocalDate();
            chamCong.computeIfAbsent(c.getUserId(), k -> new HashMap<>())
                    .computeIfAbsent(ngay, k -> new ArrayList<>()).add(c);
        }
        Map<Long, Map<LocalDate, String>> vang = new HashMap<>();
        for (LeaveRequest r : leaveRequestRepository.findByLeaveDateBetweenAndStatusIn(dau, cuoi, List.of("APPROVED", "UNEXCUSED"))) {
            vang.computeIfAbsent(r.getUserId(), k -> new HashMap<>()).put(r.getLeaveDate(), r.getStatus());
        }

        // Cột ngày: số + thứ, để lưới tô Chủ nhật và hôm nay
        List<Map<String, Object>> cotNgay = new ArrayList<>();
        for (LocalDate d = dau; !d.isAfter(cuoi); d = d.plusDays(1)) {
            cotNgay.add(Map.of("ngay", d.getDayOfMonth(), "thu", d.getDayOfWeek().getValue(),
                    "cn", d.getDayOfWeek() == DayOfWeek.SUNDAY, "homNay", d.equals(homNay)));
        }

        List<Map<String, Object>> hang = new ArrayList<>();
        for (User u : nguoi) {
            Map<LocalDate, List<CheckinLog>> cuaNguoi = chamCong.getOrDefault(u.getId(), Map.of());
            Map<LocalDate, String> vangCua = vang.getOrDefault(u.getId(), Map.of());
            Map<String, Map<String, String>> o = new LinkedHashMap<>();
            int cong = 0, muon = 0, khongPhep = 0, coPhep = 0, choDuyet = 0;

            for (LocalDate d = dau; !d.isAfter(cuoi); d = d.plusDays(1)) {
                String ky = "";
                String vao = null, ra = null;

                List<CheckinLog> logs = cuaNguoi.get(d);
                boolean coCho = false, coDuyet = false, coTuChoi = false;
                ZonedDateTime vaoDauTien = null, raCuoi = null;
                if (logs != null) {
                    for (CheckinLog c : logs) {
                        if ("PENDING".equals(c.getStatus())) coCho = true;
                        else if ("REJECTED".equals(c.getStatus())) coTuChoi = true;
                        else if ("APPROVED".equals(c.getStatus())) {
                            coDuyet = true;
                            if ("CHECK_OUT".equals(c.getActionType())) {
                                if (raCuoi == null || c.getCheckinTime().isAfter(raCuoi)) raCuoi = c.getCheckinTime();
                            } else if (vaoDauTien == null || c.getCheckinTime().isBefore(vaoDauTien)) {
                                vaoDauTien = c.getCheckinTime();
                            }
                        }
                    }
                }
                if (vaoDauTien != null) vao = vaoDauTien.withZoneSameInstant(VN_ZONE).format(GIO);
                if (raCuoi != null) ra = raCuoi.withZoneSameInstant(VN_ZONE).format(GIO);

                String trangThaiVang = vangCua.get(d);
                if (d.getDayOfWeek() == DayOfWeek.SUNDAY) ky = "-";
                else if ("APPROVED".equals(trangThaiVang)) { ky = "P"; coPhep++; }
                else if (coCho) { ky = "?"; choDuyet++; }
                else if (coDuyet && vaoDauTien != null) {
                    boolean dung = checkinService.laDungGio(u, vaoDauTien);
                    ky = dung ? "V" : "M"; cong++; if (!dung) muon++;
                }
                else if (coDuyet) { ky = "V"; cong++; }            // chỉ có check-out được duyệt
                else if (coTuChoi) ky = "R";
                else if ("UNEXCUSED".equals(trangThaiVang)) { ky = "X"; khongPhep++; }
                // còn lại: chưa tới ngày / trước ngày vào làm / chưa chốt → rỗng
                if (u.getJoinedDate() != null && d.isBefore(u.getJoinedDate())) ky = "";

                Map<String, String> cell = new HashMap<>();
                cell.put("k", ky);
                if (vao != null) cell.put("vao", vao);
                if (ra != null) cell.put("ra", ra);
                o.put(String.valueOf(d.getDayOfMonth()), cell);
            }

            Map<String, Object> h = new LinkedHashMap<>();
            h.put("userId", u.getId());
            h.put("fullName", u.getFullName());
            h.put("departmentName", u.getDepartment() != null ? u.getDepartment().getName() : null);
            h.put("role", u.getRole());
            h.put("o", o);
            h.put("tong", Map.of("cong", cong, "muon", muon, "khongPhep", khongPhep, "coPhep", coPhep, "choDuyet", choDuyet));
            hang.add(h);
        }

        Map<String, Object> kq = new LinkedHashMap<>();
        kq.put("thang", thang);
        kq.put("ngay", cotNgay);
        kq.put("hang", hang);
        return kq;
    }
}
