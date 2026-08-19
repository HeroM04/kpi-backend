package com.trilong.kpibackend.modules.attendance.service;

import com.trilong.kpibackend.core.utils.HaversineUtils;
import com.trilong.kpibackend.modules.attendance.dto.CheckinRequestDTO;
import com.trilong.kpibackend.modules.attendance.entity.CheckinLog;
import com.trilong.kpibackend.modules.attendance.repository.CheckinLogRepository;
import com.trilong.kpibackend.modules.kpi.service.KpiCalculationService;
import com.trilong.kpibackend.modules.user.entity.Department;
import com.trilong.kpibackend.modules.user.entity.User;
import com.trilong.kpibackend.modules.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.trilong.kpibackend.core.service.FaceRecognitionService;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * CheckinService — Xử lý toàn bộ logic chấm công.
 *
 * <h3>Luồng nghiệp vụ:</h3>
 * <p><b>Luồng 1 — Tại văn phòng (GPS ≤ 50m):</b>
 * <ul>
 *   <li>Status: {@code APPROVED} ngay lập tức</li>
 *   <li>Loại: {@code OFFICE}</li>
 *   <li>KPI: Tự động cộng điểm attendance</li>
 *   <li>Mốc giờ quan trọng: 08:30 (đúng giờ) | 17:30 (tan làm)</li>
 * </ul>
 *
 * <p><b>Luồng 2 — Ngoại tuyến/Thị trường (GPS > 50m):</b>
 * <ul>
 *   <li>Status: {@code PENDING} — chờ Admin/Trưởng phòng duyệt</li>
 *   <li>Loại: {@code FIELD}</li>
 *   <li>Bắt buộc: {@code note} phải có nội dung lý do</li>
 *   <li>KPI: Chỉ cộng điểm SAU KHI được duyệt</li>
 * </ul>
 *
 * <h3>Bảo mật client-side (thực hiện trên Mobile App — không xử lý ở đây):</h3>
 * <ul>
 *   <li>Anti-mock location: Block nếu isMockLocation = true</li>
 *   <li>Camera-only: Không cho chọn ảnh từ gallery</li>
 *   <li>ML Kit face detection: Phải có ≥ 1 khuôn mặt trong ảnh</li>
 *   <li>Watermark: Vẽ timestamp + địa chỉ lên ảnh trước khi upload</li>
 * </ul>
 */
@Service
@Slf4j
public class CheckinService {

    // ── Ngưỡng khoảng cách (mét) ─────────────────────────────────────────────
    /** Bán kính mặc định khi phòng ban chưa cấu hình: trong vòng 2000m → APPROVED tự động */
    private static final double OFFICE_RADIUS_METERS = 2000.0;

    // ── Tọa độ văn phòng mặc định (fallback khi phòng ban chưa set) ──────────
    private static final double DEFAULT_OFFICE_LAT = 20.999042; // Hà Nội
    private static final double DEFAULT_OFFICE_LNG = 105.806702;

    /**
     * Cấu hình văn phòng áp dụng cho một nhân viên (tọa độ + bán kính cho phép).
     * Đọc từ phòng ban của nhân viên trong DB, để đổi địa điểm văn phòng chỉ cần
     * sửa trên Web Admin, KHÔNG phải sửa code và build lại app.
     */
    private record OfficeConfig(double lat, double lng, double radiusMeters, String source) {}

    /**
     * Lấy cấu hình văn phòng cho nhân viên.
     * Ưu tiên tọa độ/bán kính của phòng ban; thiếu thì dùng giá trị mặc định.
     */
    private OfficeConfig resolveOfficeConfig(User user) {
        Department dept = (user != null) ? user.getDepartment() : null;

        if (dept != null && dept.getOfficeLat() != null && dept.getOfficeLng() != null) {
            double radius = (dept.getAllowedRadius() != null && dept.getAllowedRadius() > 0)
                    ? dept.getAllowedRadius().doubleValue()
                    : OFFICE_RADIUS_METERS;
            return new OfficeConfig(dept.getOfficeLat(), dept.getOfficeLng(), radius,
                    "phòng ban '" + dept.getName() + "'");
        }

        return new OfficeConfig(DEFAULT_OFFICE_LAT, DEFAULT_OFFICE_LNG, OFFICE_RADIUS_METERS,
                "mặc định (phòng ban chưa cấu hình tọa độ)");
    }

    // ── Mốc giờ chấm công (theo quy định công ty) ───────────────────────────
    /** Điểm danh từ 08:30; đến 08:45 vẫn tính ĐÚNG GIỜ (15 phút châm chước). */
    private static final LocalTime ON_TIME_LIMIT = LocalTime.of(8, 45);
    /** Từ sau 08:45 đến 10:00 tính là ĐI MUỘN. */
    private static final LocalTime LATE_LIMIT    = LocalTime.of(10, 0);
    /** Kết thúc giờ làm. */
    private static final LocalTime CUTOFF_CHECKOUT = LocalTime.of(17, 30);

    // ── Điểm KPI chuyên cần ─────────────────────────────────────────────────
    /** Đi làm đúng giờ (≤ 08:45) */
    private static final int KPI_ON_TIME = 5;
    /** Đi làm muộn (08:45 – 10:00) */
    private static final int KPI_LATE = -5;
    /** Check-in sau 10:00 — theo quy định tính là vắng mặt (không phép) */
    private static final int KPI_ABSENT_UNEXCUSED = -15;

    // ── Tăng ca (thuộc nhóm Thực chiến) ─────────────────────────────────────
    /** Mốc giờ về tối thiểu để được tính một khung tăng ca. */
    private static final LocalTime OVERTIME_LIMIT = LocalTime.of(20, 0);
    /** Điểm cho mỗi khung tăng ca. */
    private static final int KPI_OVERTIME = 5;

    // ── Zone ─────────────────────────────────────────────────────────────────
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Autowired
    private CheckinLogRepository checkinLogRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private KpiCalculationService kpiCalculationService;

    @Autowired
    private org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;

    @Autowired
    private FaceRecognitionService faceRecognitionService;

    @Value("${app.rekognition.enabled:false}")
    private boolean isRekognitionEnabled;

    // ─────────────────────────────────────────────────────────────────────────
    // PUBLIC API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Xử lý yêu cầu chấm công — entry point chính.
     *
     * <p>Hàm này tự quyết định luồng dựa trên khoảng cách GPS:
     * <ul>
     *   <li>≤ 50m → gọi {@link #processOfficeCheckin(Long, CheckinRequestDTO, double)}</li>
     *   <li>> 50m → gọi {@link #processFieldCheckin(Long, CheckinRequestDTO, double)}</li>
     * </ul>
     *
     * @return {@link CheckinLog} đã lưu vào DB
     * @throws IllegalArgumentException nếu FIELD checkin thiếu note
     */
    @Transactional
    public CheckinLog submitCheckin(Long userId, CheckinRequestDTO request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhân viên"));

        String actionType = resolveActionType(request.getActionType());
        // Xác thực khuôn mặt bằng AWS Rekognition (nếu được bật trong properties và không phải CHECK_OUT)
        if (isRekognitionEnabled && !"CHECK_OUT".equals(actionType)) {
            if (user.getAvatarUrl() == null || user.getAvatarUrl().isEmpty()) {
                throw new IllegalArgumentException("Bạn chưa cập nhật ảnh đại diện (Avatar). Vui lòng cập nhật để sử dụng tính năng nhận diện khuôn mặt!");
            }
            log.info("[Checkin] Đang gọi AWS Rekognition để xác thực khuôn mặt user {}...", userId);
            boolean isMatched = faceRecognitionService.compareFacesUrls(user.getAvatarUrl(), request.getPhotoUrl());
            if (!isMatched) {
                throw new IllegalArgumentException("Xác thực khuôn mặt thất bại! Người trong ảnh không khớp với ảnh đại diện của bạn.");
            }
            log.info("[Checkin] Xác thực khuôn mặt thành công cho user {}!", userId);
        }

        // Tọa độ + bán kính lấy theo phòng ban của nhân viên (sửa được trên Web Admin)
        OfficeConfig office = resolveOfficeConfig(user);

        double distance = HaversineUtils.calculateDistanceInMeters(
                request.getLatitude(), request.getLongitude(), office.lat(), office.lng()
        );

        log.info("[Checkin] userId={}, distance={}m, lat={}, lng={} | văn phòng: {} ({}, {}) bán kính {}m",
                userId, String.format("%.1f", distance), request.getLatitude(), request.getLongitude(),
                office.source(), office.lat(), office.lng(), String.format("%.0f", office.radiusMeters()));

        if (distance <= office.radiusMeters()) {
            return processOfficeCheckin(userId, request, distance);
        } else {
            return processFieldCheckin(userId, request, distance);
        }
    }

    /**
     * Kiểm tra nhanh xem tọa độ có trong phạm vi văn phòng của nhân viên không.
     * Dùng cấu hình phòng ban; phòng ban chưa set thì dùng giá trị mặc định.
     */
    public boolean isWithinOfficeRange(Long userId, double lat, double lng) {
        User user = userRepository.findById(userId).orElse(null);
        OfficeConfig office = resolveOfficeConfig(user);
        double distance = HaversineUtils.calculateDistanceInMeters(lat, lng, office.lat(), office.lng());
        return distance <= office.radiusMeters();
    }

    /** Lấy lịch sử checkin của user trong một ngày cụ thể */
    public List<CheckinLog> getCheckinsByUserIdAndDate(Long userId, LocalDate date) {
        ZonedDateTime start = date.atStartOfDay(VN_ZONE);
        ZonedDateTime end   = date.plusDays(1).atStartOfDay(VN_ZONE);
        return checkinLogRepository.findByUserIdAndCheckinTimeBetween(userId, start, end);
    }

    /** Lấy toàn bộ lịch sử checkin của user */
    public List<CheckinLog> getCheckinsByUserId(Long userId) {
        return checkinLogRepository.findByUserIdOrderByCheckinTimeDesc(userId);
    }

    /** Lấy danh sách chờ duyệt (cho Admin/Trưởng phòng) */
    public List<CheckinLog> getPendingCheckins() {
        return checkinLogRepository.findByStatusOrderByCheckinTimeDesc("PENDING");
    }

    /** Lấy toàn bộ danh sách checkin (Admin) */
    public List<CheckinLog> getAllCheckins() {
        return checkinLogRepository.findAll();
    }

    /** Lấy chi tiết bản ghi checkin theo ID */
    public CheckinLog getCheckinById(Long id) {
        return checkinLogRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy bản ghi chấm công có ID: " + id));
    }

    /**
     * Admin/Trưởng phòng duyệt hoặc từ chối yêu cầu chấm công ngoại tuyến.
     *
     * <p>Khi APPROVE: cộng điểm KPI attendance.
     * Khi REJECT: nếu trước đó đã APPROVED thì trừ điểm lại.
     */
    @Transactional
    public CheckinLog updateStatus(Long id, String newStatus, String reason) {
        CheckinLog checkinLog = checkinLogRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy bản ghi chấm công"));

        String oldStatus = checkinLog.getStatus();
        checkinLog.setStatus(newStatus.toUpperCase());

        if (reason != null && !reason.trim().isEmpty()) {
            checkinLog.setRejectReason(reason);
        }

        CheckinLog saved = checkinLogRepository.save(checkinLog);

        // Xử lý cộng/trừ KPI
        if (!"APPROVED".equals(oldStatus) && "APPROVED".equals(newStatus.toUpperCase())) {
            // Phải quy về giờ Việt Nam: giá trị đọc từ DB đang ở múi giờ UTC,
            // nếu lấy thẳng toLocalTime() thì 09:30 VN thành 02:30 và bị chấm
            // nhầm thành "đi đúng giờ".
            int kpiPoints = calculateAttendanceKpi(checkinLog.getActionType(),
                    checkinLog.getCheckinTime().withZoneSameInstant(VN_ZONE).toLocalTime());
            kpiCalculationService.updateKpiPoints(
                    checkinLog.getUserId(), "attendance", kpiPoints, checkinLog.getCheckinTime()
            );
            // Duyệt bản ghi check-out muộn cũng được tính tăng ca như chấm công tại chỗ
            awardOvertimeIfEligible(checkinLog.getUserId(), checkinLog.getActionType(),
                    checkinLog.getCheckinTime().withZoneSameInstant(VN_ZONE));
            log.info("[Checkin]  Duyệt {} #{} → {} KPI cho userId={}", checkinLog.getActionType(), id, (kpiPoints > 0 ? "+" + kpiPoints : kpiPoints), checkinLog.getUserId());
        } else if ("APPROVED".equals(oldStatus) && !"APPROVED".equals(newStatus.toUpperCase())) {
            // Phải quy về giờ Việt Nam: giá trị đọc từ DB đang ở múi giờ UTC,
            // nếu lấy thẳng toLocalTime() thì 09:30 VN thành 02:30 và bị chấm
            // nhầm thành "đi đúng giờ".
            int kpiPoints = calculateAttendanceKpi(checkinLog.getActionType(),
                    checkinLog.getCheckinTime().withZoneSameInstant(VN_ZONE).toLocalTime());
            kpiCalculationService.updateKpiPoints(
                    checkinLog.getUserId(), "attendance", -kpiPoints, checkinLog.getCheckinTime()
            );
            log.info("[Checkin]  Thu hồi {} #{} → {} KPI cho userId={}", checkinLog.getActionType(), id, (-kpiPoints > 0 ? "+" + (-kpiPoints) : (-kpiPoints)), checkinLog.getUserId());
        }

        return saved;
    }

    /**
     * Duyệt checkin theo request body dạng Map (backward compatible với API cũ).
     */
    @Transactional
    public void processApproval(Map<String, Object> request) {
        Long logId   = ((Number) request.get("logId")).longValue();
        String status  = (String) request.get("status");
        String reason  = (String) request.get("reason");
        updateStatus(logId, status, reason);
    }

    /** Xóa bản ghi checkin (Admin) */
    @Transactional
    public void deleteCheckin(Long id) {
        CheckinLog checkinLog = checkinLogRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy bản ghi chấm công có ID: " + id));
        checkinLogRepository.delete(checkinLog);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Luồng 1: GPS ≤ 50m — tự động APPROVED, cộng KPI ngay.
     */
    @Transactional
    private CheckinLog processOfficeCheckin(Long userId, CheckinRequestDTO req, double distance) {
        ZonedDateTime now = ZonedDateTime.now(VN_ZONE);

        CheckinLog checkinLog = new CheckinLog();
        checkinLog.setUserId(userId);
        checkinLog.setCheckinTime(now);
        checkinLog.setCheckinType("OFFICE");
        String finalActionType = resolveActionType(req.getActionType());
        checkinLog.setActionType(finalActionType);
        checkinLog.setLatitude(req.getLatitude());
        checkinLog.setLongitude(req.getLongitude());
        checkinLog.setDistanceToOffice(distance);
        checkinLog.setAddress(req.getAddress());
        checkinLog.setPhotoUrl(req.getPhotoUrl());
        checkinLog.setNote(req.getNote());
        checkinLog.setStatus("APPROVED");

        CheckinLog saved = checkinLogRepository.save(checkinLog);

        // Điểm chuyên cần theo giờ check-in
        int kpiPoints = calculateAttendanceKpi(finalActionType, now.toLocalTime());
        kpiCalculationService.updateKpiPoints(userId, "attendance", kpiPoints, now);

        // Điểm tăng ca theo giờ check-out (tính vào nhóm Thực chiến)
        awardOvertimeIfEligible(userId, finalActionType, now);

        log.info("[Checkin]  OFFICE {} userId={} lúc {} → {} KPI | distance={}m",
                finalActionType, userId, now.toLocalTime(), (kpiPoints > 0 ? "+" + kpiPoints : kpiPoints), String.format("%.1f", distance));

        // Notify WebSocket
        notifyAdmin("CHECKIN_OFFICE");
        return saved;
    }

    /**
     * Luồng 2: GPS > 50m — PENDING, cần Admin duyệt.
     */
    @Transactional
    private CheckinLog processFieldCheckin(Long userId, CheckinRequestDTO req, double distance) {
        // Bắt buộc phải có lý do khi ngoại tuyến
        if (req.getNote() == null || req.getNote().trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Bạn đang ở ngoài phạm vi văn phòng (" + String.format("%.0f", distance) + "m). " +
                    "Vui lòng nhập lý do chấm công ngoại tuyến."
            );
        }

        ZonedDateTime now = ZonedDateTime.now(VN_ZONE);

        CheckinLog checkinLog = new CheckinLog();
        checkinLog.setUserId(userId);
        checkinLog.setCheckinTime(now);
        checkinLog.setCheckinType("FIELD");
        checkinLog.setActionType(resolveActionType(req.getActionType()));
        checkinLog.setLatitude(req.getLatitude());
        checkinLog.setLongitude(req.getLongitude());
        checkinLog.setDistanceToOffice(distance);
        checkinLog.setAddress(req.getAddress());
        checkinLog.setPhotoUrl(req.getPhotoUrl());
        checkinLog.setNote(req.getNote());
        checkinLog.setStatus("PENDING");

        CheckinLog saved = checkinLogRepository.save(checkinLog);

        log.info("[Checkin]  FIELD checkin userId={} lúc {} → PENDING | distance={}m | note={}",
                userId, now.toLocalTime(), String.format("%.1f", distance), req.getNote());

        // Notify WebSocket cho Admin biết có yêu cầu mới
        notifyAdmin("CHECKIN_PENDING_NEW");
        return saved;
    }

    /** Chuẩn hóa actionType — mặc định CHECK_IN */
    private String resolveActionType(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "CHECK_IN";
        return raw.trim().toUpperCase();
    }

    /** Gửi thông báo WebSocket realtime cho Admin */
    private void notifyAdmin(String eventType) {
        try {
            messagingTemplate.convertAndSend("/topic/admin/attendance", eventType);
        } catch (Exception e) {
            log.warn("[Checkin] Không gửi được WebSocket notify: {}", e.getMessage());
        }
    }

    /**
     * Logic tính điểm KPI mới:
     * - CHECK_IN: Đúng giờ (<= 08:30) -> +5 điểm. Đi trễ (> 08:30) -> 0 điểm.
     * - CHECK_OUT: Không cộng/trừ điểm KPI (chỉ dùng để record thời gian).
     */
    /**
     * Tính điểm chuyên cần theo giờ check-in, đúng quy định công ty:
     * <ul>
     *   <li>đến 08:45 → đúng giờ, <b>+5đ</b> (điểm danh từ 08:30, châm chước 15 phút)</li>
     *   <li>08:45 – 10:00 → đi muộn, <b>−5đ</b></li>
     *   <li>sau 10:00 → tính là vắng mặt không phép, <b>−15đ</b></li>
     * </ul>
     * Check-out không tính điểm (chỉ dùng để ghi nhận giờ về).
     */
    /**
     * Cộng điểm tăng ca nếu ca làm kéo dài tới mốc quy định.
     *
     * <p>Quy định: khung làm thêm 18h–20h, về từ <b>20:00</b> trở đi được tính
     * một khung (+5đ), áp dụng các ngày làm việc trong tuần. Riêng thứ Năm và
     * thứ Sáu công ty kỳ vọng có một buổi kéo tới 21h — chỉ cần một trong hai
     * ngày đó đạt là đủ, nên về mặt điểm số vẫn tính từ mốc 20:00.
     *
     * <p>Điểm tăng ca thuộc nhóm <b>Thực chiến</b> (trần 40đ/tuần).
     * Chỉ tính khi CHECK_OUT, và không tính vào cuối tuần.
     */
    private void awardOvertimeIfEligible(Long userId, String actionType, ZonedDateTime time) {
        if (!"CHECK_OUT".equals(actionType)) return;

        DayOfWeek day = time.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) return;

        if (time.toLocalTime().isBefore(OVERTIME_LIMIT)) return;

        kpiCalculationService.updateKpiPoints(userId, "meeting", KPI_OVERTIME, time);
        log.info("[Checkin]  Tăng ca userId={} về lúc {} → +{} KPI (nhóm Thực chiến)",
                userId, time.toLocalTime(), KPI_OVERTIME);
    }

    private int calculateAttendanceKpi(String actionType, LocalTime time) {
        if ("CHECK_OUT".equals(actionType)) {
            return 0;
        }
        if (!time.isAfter(ON_TIME_LIMIT)) {
            return KPI_ON_TIME;
        }
        if (!time.isAfter(LATE_LIMIT)) {
            return KPI_LATE;
        }
        return KPI_ABSENT_UNEXCUSED;
    }
}
