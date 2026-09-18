package com.trilong.kpibackend.modules.attendance.service;

import com.trilong.kpibackend.modules.attendance.dto.LeaveRequestDTO;
import com.trilong.kpibackend.modules.attendance.dto.LeaveRequestResponseDTO;
import com.trilong.kpibackend.modules.attendance.entity.LeaveRequest;
import com.trilong.kpibackend.modules.attendance.repository.CheckinLogRepository;
import com.trilong.kpibackend.modules.attendance.repository.LeaveRequestRepository;
import com.trilong.kpibackend.modules.kpi.service.KpiCalculationService;
import com.trilong.kpibackend.modules.user.entity.User;
import com.trilong.kpibackend.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * LeaveRequestService — Xử lý đơn xin vắng và chấm điểm vắng mặt.
 *
 * <p>Theo quy định KPI của công ty:
 * <ul>
 *   <li><b>Vắng có phép</b> (đơn được Admin duyệt): −10đ</li>
 *   <li><b>Vắng không phép</b> (không chấm công, không có đơn được duyệt): −15đ</li>
 * </ul>
 * Điểm trừ nằm trong nhóm "Phát triển cá nhân" (trần 30đ/tuần) và không kéo
 * điểm tuần xuống dưới 0 — cơ chế sàn nằm trong {@link KpiCalculationService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeaveRequestService {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    /** Vắng có phép — đơn đã được Admin duyệt */
    public static final int KPI_LEAVE_APPROVED = -10;

    /** Vắng không phép — không chấm công và không có đơn được duyệt */
    public static final int KPI_LEAVE_UNEXCUSED = -15;

    private final LeaveRequestRepository leaveRequestRepository;
    private final CheckinLogRepository checkinLogRepository;
    private final UserRepository userRepository;
    private final KpiCalculationService kpiCalculationService;

    @Autowired(required = false)
    private SimpMessagingTemplate messagingTemplate;

    // ----------------------------------------------------------------- Nhân sự

    /** Nhân sự gửi đơn xin vắng. Mỗi người mỗi ngày chỉ một đơn. */
    @Transactional
    public LeaveRequest submit(Long userId, LeaveRequestDTO dto) {
        LocalDate date = dto.getLeaveDate();
        LocalDate today = LocalDate.now(VN_ZONE);

        if (date.isBefore(today)) {
            throw new IllegalArgumentException("Không thể xin vắng cho ngày đã qua.");
        }
        if (date.isAfter(today.plusMonths(3))) {
            throw new IllegalArgumentException("Chỉ được xin vắng trong vòng 3 tháng tới.");
        }
        if (date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            throw new IllegalArgumentException("Chủ nhật không phải ngày làm việc, không cần xin vắng.");
        }

        LeaveRequest existing = leaveRequestRepository.findByUserIdAndLeaveDate(userId, date).orElse(null);
        if (existing != null) {
            if ("APPROVED".equals(existing.getStatus())) {
                throw new IllegalArgumentException("Đơn xin vắng ngày này đã được duyệt.");
            }
            if ("PENDING".equals(existing.getStatus())) {
                throw new IllegalArgumentException("Bạn đã gửi đơn xin vắng cho ngày này, đang chờ duyệt.");
            }
            // Đơn cũ bị từ chối → cho gửi lại bằng cách ghi đè
            existing.setReason(dto.getReason());
            existing.setStatus("PENDING");
            existing.setSubmittedAt(ZonedDateTime.now());
            existing.setReviewedBy(null);
            existing.setReviewedAt(null);
            existing.setReviewNote(null);
            notifyAdmin();
            return leaveRequestRepository.save(existing);
        }

        LeaveRequest req = new LeaveRequest();
        req.setUserId(userId);
        req.setLeaveDate(date);
        req.setReason(dto.getReason());
        req.setStatus("PENDING");
        req.setKpiApplied(false);
        LeaveRequest saved = leaveRequestRepository.save(req);
        notifyAdmin();
        return saved;
    }

    /** Nhân sự tự hủy đơn khi còn PENDING. */
    @Transactional
    public void cancel(Long userId, Long requestId) {
        LeaveRequest req = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn xin vắng."));
        if (!req.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Bạn không có quyền hủy đơn này.");
        }
        if (!"PENDING".equals(req.getStatus())) {
            throw new IllegalArgumentException("Chỉ hủy được đơn đang chờ duyệt.");
        }
        leaveRequestRepository.delete(req);
    }

    public List<LeaveRequest> getMyRequests(Long userId) {
        return leaveRequestRepository.findByUserIdOrderByLeaveDateDesc(userId);
    }

    // -------------------------------------------------------------------- Admin

    public List<LeaveRequest> getPending() {
        return leaveRequestRepository.findByStatusOrderByLeaveDateAsc("PENDING");
    }

    public List<LeaveRequest> getAll(LocalDate from, LocalDate to) {
        if (from != null && to != null) {
            return leaveRequestRepository.findByLeaveDateBetweenOrderByLeaveDateDesc(from, to);
        }
        return leaveRequestRepository.findAll().stream()
                .sorted((a, b) -> b.getLeaveDate().compareTo(a.getLeaveDate()))
                .toList();
    }

    // ── Danh sách phân trang cho WebAdmin ──────────────────────────────────
    //
    // Bảng này trộn hai loại dữ liệu khác bản chất: ĐƠN do người gửi (vài cái
    // một ngày, cần Admin duyệt) và BẢN GHI máy quét đêm sinh ra (hàng trăm một
    // ngày với công ty trăm người, không cần ai làm gì). Trả toàn bộ về trình
    // duyệt là vài tuần đã hàng nghìn dòng. Nên: phân trang ở máy chủ, và cho
    // lọc riêng "đơn người gửi" / "máy tự ghi".

    /** Kết quả một trang: bản ghi + tổng số dòng khớp bộ lọc. */
    public record TrangDon(List<LeaveRequestResponseDTO> banGhi, long tong) {}

    /**
     * @param loai      NGUOI_GUI = đơn người gửi (khác UNEXCUSED) · TU_DONG = máy tự ghi (UNEXCUSED) · null/ALL = tất cả
     * @param trangThai PENDING / APPROVED / REJECTED / UNEXCUSED, null = mọi trạng thái
     * @param search    tìm theo tên, không phân biệt dấu/hoa thường
     */
    public TrangDon timKiem(String loai, String trangThai, LocalDate tu, LocalDate den,
                            String search, Long departmentId, int page, int size) {
        // Tên tiếng Việt: DB không bỏ dấu được, nhân sự chỉ vài trăm người → lọc
        // id ở Java rồi đưa vào IN. Không ai khớp thì trả trang rỗng luôn.
        Set<Long> userIds = null;
        boolean coDkNguoi = (search != null && !search.isBlank()) || departmentId != null;
        if (coDkNguoi) {
            String k = boDau(search);
            userIds = new HashSet<>();
            for (User u : userRepository.findAll()) {
                if (departmentId != null && (u.getDepartment() == null || !departmentId.equals(u.getDepartment().getId()))) continue;
                if (!k.isEmpty() && !boDau(u.getFullName()).contains(k)) continue;
                userIds.add(u.getId());
            }
            if (userIds.isEmpty()) return new TrangDon(List.of(), 0);
        }
        final Set<Long> ids = userIds;

        org.springframework.data.jpa.domain.Specification<LeaveRequest> spec = (root, q, cb) -> {
            List<jakarta.persistence.criteria.Predicate> ds = new java.util.ArrayList<>();
            if ("NGUOI_GUI".equals(loai)) ds.add(cb.notEqual(root.get("status"), "UNEXCUSED"));
            else if ("TU_DONG".equals(loai)) ds.add(cb.equal(root.get("status"), "UNEXCUSED"));
            if (trangThai != null && !trangThai.isBlank()) ds.add(cb.equal(root.get("status"), trangThai));
            if (tu != null) ds.add(cb.greaterThanOrEqualTo(root.get("leaveDate"), tu));
            if (den != null) ds.add(cb.lessThanOrEqualTo(root.get("leaveDate"), den));
            if (ids != null) ds.add(root.get("userId").in(ids));
            return cb.and(ds.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        var pageable = org.springframework.data.domain.PageRequest.of(
                Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Order.desc("leaveDate"),
                        org.springframework.data.domain.Sort.Order.desc("id")));
        var trang = leaveRequestRepository.findAll(spec, pageable);
        return new TrangDon(trang.getContent().stream().map(this::toDTO).toList(), trang.getTotalElements());
    }

    /**
     * Bảng vắng mặt gộp theo người trong một tháng: mỗi người một dòng — bao
     * nhiêu ngày không phép, bao nhiêu ngày có phép, và các ngày cụ thể.
     * Admin quét 100 dòng thay vì 2.500 dòng máy tự ghi.
     */
    public List<Map<String, Object>> tongHopVangMat(String thang) {
        java.time.YearMonth ym = java.time.YearMonth.parse(thang);
        List<LeaveRequest> rows = leaveRequestRepository.findByLeaveDateBetweenAndStatusIn(
                ym.atDay(1), ym.atEndOfMonth(), List.of("UNEXCUSED", "APPROVED"));

        Map<Long, List<LeaveRequest>> theoNguoi = new java.util.LinkedHashMap<>();
        for (LeaveRequest r : rows) theoNguoi.computeIfAbsent(r.getUserId(), x -> new java.util.ArrayList<>()).add(r);
        if (theoNguoi.isEmpty()) return List.of();

        Map<Long, User> nguoi = new java.util.HashMap<>();
        for (User u : userRepository.findAllById(theoNguoi.keySet())) nguoi.put(u.getId(), u);

        List<Map<String, Object>> kq = new java.util.ArrayList<>();
        for (var e : theoNguoi.entrySet()) {
            User u = nguoi.get(e.getKey());
            List<String> khongPhep = e.getValue().stream().filter(r -> "UNEXCUSED".equals(r.getStatus()))
                    .map(r -> r.getLeaveDate().toString()).sorted().toList();
            List<String> coPhep = e.getValue().stream().filter(r -> "APPROVED".equals(r.getStatus()))
                    .map(r -> r.getLeaveDate().toString()).sorted().toList();
            Map<String, Object> d = new java.util.LinkedHashMap<>();
            d.put("userId", e.getKey());
            d.put("fullName", u != null ? u.getFullName() : "#" + e.getKey());
            d.put("departmentName", u != null && u.getDepartment() != null ? u.getDepartment().getName() : null);
            d.put("role", u != null ? u.getRole() : null);
            d.put("status", u != null ? u.getStatus() : null);
            d.put("soKhongPhep", khongPhep.size());
            d.put("soCoPhep", coPhep.size());
            d.put("ngayKhongPhep", khongPhep);
            d.put("ngayCoPhep", coPhep);
            kq.add(d);
        }
        kq.sort((a, b) -> {
            int c = Integer.compare((int) b.get("soKhongPhep"), (int) a.get("soKhongPhep"));
            return c != 0 ? c : String.valueOf(a.get("fullName")).compareTo(String.valueOf(b.get("fullName")));
        });
        return kq;
    }

    /** Bỏ dấu tiếng Việt, hạ chữ thường — để so tên không phụ thuộc cách gõ. */
    private static String boDau(String s) {
        if (s == null) return "";
        String t = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replace('đ', 'd').replace('Đ', 'D');
        return t.toLowerCase().trim();
    }

    /** Admin duyệt đơn → ghi nhận vắng có phép, trừ 10đ. */
    @Transactional
    public LeaveRequest approve(Long requestId, Long adminId, String note) {
        LeaveRequest req = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn xin vắng."));

        if ("APPROVED".equals(req.getStatus())) {
            return req;
        }

        req.setStatus("APPROVED");
        req.setReviewedBy(adminId);
        req.setReviewedAt(ZonedDateTime.now());
        req.setReviewNote(note);

        if (!Boolean.TRUE.equals(req.getKpiApplied())) {
            kpiCalculationService.updateKpiPoints(req.getUserId(), "attendance",
                    KPI_LEAVE_APPROVED, atNoon(req.getLeaveDate()),
                    "Nghỉ có phép ngày " + ngay(req.getLeaveDate()) + " — Admin đã duyệt đơn");
            req.setKpiApplied(true);
            log.info("[Leave] Duyệt đơn vắng userId={} ngày={} → {} KPI (vắng có phép)",
                    req.getUserId(), req.getLeaveDate(), KPI_LEAVE_APPROVED);
        }

        return leaveRequestRepository.save(req);
    }

    /** Admin từ chối đơn. Nếu trước đó đã duyệt thì hoàn lại điểm đã trừ. */
    @Transactional
    public LeaveRequest reject(Long requestId, Long adminId, String note) {
        LeaveRequest req = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn xin vắng."));

        if (Boolean.TRUE.equals(req.getKpiApplied())) {
            kpiCalculationService.updateKpiPoints(req.getUserId(), "attendance",
                    -KPI_LEAVE_APPROVED, atNoon(req.getLeaveDate()),
                    "Hoàn lại điểm đơn nghỉ ngày " + ngay(req.getLeaveDate()) + " — Admin đổi sang từ chối");
            req.setKpiApplied(false);
        }

        req.setStatus("REJECTED");
        req.setReviewedBy(adminId);
        req.setReviewedAt(ZonedDateTime.now());
        req.setReviewNote(note);
        return leaveRequestRepository.save(req);
    }

    // --------------------------------------------------------------- Cuối ngày

    /**
     * Chốt vắng mặt của một ngày làm việc: ai không có bản ghi chấm công và
     * cũng không có đơn xin vắng được duyệt thì bị trừ 15đ (vắng không phép).
     *
     * <p>Chạy được nhiều lần cho cùng một ngày mà không trừ trùng — mỗi lần trừ
     * đều ghi lại thành một bản ghi {@code LeaveRequest} trạng thái
     * {@code UNEXCUSED} với {@code kpiApplied = true}.
     *
     * @return số nhân sự bị chấm vắng không phép
     */
    @Transactional
    public int closeDay(LocalDate date) {
        if (date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            log.info("[Leave] {} là Chủ nhật — bỏ qua chốt vắng mặt.", date);
            return 0;
        }

        ZonedDateTime start = date.atStartOfDay(VN_ZONE);
        ZonedDateTime end = date.plusDays(1).atStartOfDay(VN_ZONE);

        Set<Long> checkedIn = new HashSet<>();
        for (var logRow : checkinLogRepository.findByCheckinTimeBetween(start, end)) {
            if (!"REJECTED".equals(logRow.getStatus())) {
                checkedIn.add(logRow.getUserId());
            }
        }

        Set<Long> hasRecord = new HashSet<>();
        for (LeaveRequest r : leaveRequestRepository.findByLeaveDate(date)) {
            // Đơn được duyệt hoặc đã chấm vắng không phép rồi → không xử lý lại
            if ("APPROVED".equals(r.getStatus()) || "UNEXCUSED".equals(r.getStatus())) {
                hasRecord.add(r.getUserId());
            }
        }

        int count = 0;
        for (User user : userRepository.findAll()) {
            if (!"ACTIVE".equals(user.getStatus())) continue;
            // Tài khoản quản trị không thuộc diện chấm công nên không chấm vắng
            if ("ADMIN".equals(user.getRole())) continue;
            if (checkedIn.contains(user.getId())) continue;
            if (hasRecord.contains(user.getId())) continue;
            if (user.getCreatedAt() != null
                    && user.getCreatedAt().withZoneSameInstant(VN_ZONE).toLocalDate().isAfter(date)) {
                continue; // chưa vào công ty ngày đó
            }

            LeaveRequest mark = leaveRequestRepository.findByUserIdAndLeaveDate(user.getId(), date)
                    .orElseGet(LeaveRequest::new);
            mark.setUserId(user.getId());
            mark.setLeaveDate(date);
            mark.setReason(mark.getReason() != null ? mark.getReason()
                    : "Hệ thống tự ghi nhận: không chấm công và không có đơn xin vắng được duyệt");
            mark.setStatus("UNEXCUSED");
            mark.setReviewedAt(ZonedDateTime.now());
            mark.setKpiApplied(true);
            leaveRequestRepository.save(mark);

            kpiCalculationService.updateKpiPoints(user.getId(), "attendance",
                    KPI_LEAVE_UNEXCUSED, atNoon(date),
                    "Vắng không phép ngày " + ngay(date) + " — không chấm công và không có đơn xin nghỉ");
            count++;
        }

        log.info("[Leave] Chốt ngày {}: {} nhân sự vắng không phép ({} KPI mỗi người).",
                date, count, KPI_LEAVE_UNEXCUSED);
        return count;
    }

    /** Ngày dạng dd/MM để ghép vào câu diễn giải điểm. */
    private String ngay(LocalDate d) {
        return d == null ? "?" : d.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM"));
    }

    // ------------------------------------------------------------------ Tiện ích

    /** Quy về 12:00 giờ VN để {@code getWeekString} luôn rơi đúng ngày. */
    private ZonedDateTime atNoon(LocalDate date) {
        return date.atTime(12, 0).atZone(VN_ZONE);
    }

    public LeaveRequestResponseDTO toDTO(LeaveRequest r) {
        User u = userRepository.findById(r.getUserId()).orElse(null);
        String dept = (u != null && u.getDepartment() != null) ? u.getDepartment().getName() : null;
        String reviewer = r.getReviewedBy() == null ? null
                : userRepository.findById(r.getReviewedBy()).map(User::getFullName).orElse(null);
        int penalty = "UNEXCUSED".equals(r.getStatus()) ? KPI_LEAVE_UNEXCUSED : KPI_LEAVE_APPROVED;
        return LeaveRequestResponseDTO.from(r, u != null ? u.getFullName() : null, dept, reviewer, penalty);
    }

    private void notifyAdmin() {
        if (messagingTemplate == null) return;
        try {
            messagingTemplate.convertAndSend("/topic/admin/requests",
                    (Object) Map.of("type", "LEAVE", "message", "Có đơn xin vắng mới!"));
        } catch (Exception ignored) {
        }
    }
}
