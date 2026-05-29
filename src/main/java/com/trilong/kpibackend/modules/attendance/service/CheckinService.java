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
    /** Trong vòng 2000m so với văn phòng → APPROVED tự động */
    private static final double OFFICE_RADIUS_METERS = 2000.0;

    // ── Tọa độ văn phòng mặc định (fallback khi phòng ban chưa set) ──────────
    private static final double DEFAULT_OFFICE_LAT = 20.999042; // Hà Nội
    private static final double DEFAULT_OFFICE_LNG = 105.806702;

    // ── Mốc giờ KPI ─────────────────────────────────────────────────────────
    private static final LocalTime CUTOFF_CHECKIN  = LocalTime.of(8, 30);  // 08:30 — đúng giờ
    private static final LocalTime CUTOFF_CHECKOUT = LocalTime.of(17, 30); // 17:30 — tan làm

    // ── KPI Points ───────────────────────────────────────────────────────────
    private static final int KPI_POINTS_ATTENDANCE = 5;

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

        // Xác thực khuôn mặt bằng AWS Rekognition (nếu được bật trong properties)
        if (isRekognitionEnabled) {
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

        // Tính khoảng cách đến văn phòng (áp dụng chung 1 tọa độ cho TẤT CẢ phòng ban)
        double officeLat = DEFAULT_OFFICE_LAT;
        double officeLng = DEFAULT_OFFICE_LNG;

        double distance = HaversineUtils.calculateDistanceInMeters(
                request.getLatitude(), request.getLongitude(), officeLat, officeLng
        );

        log.info("[Checkin] userId={}, distance={}m, lat={}, lng={}",
                userId, String.format("%.1f", distance), request.getLatitude(), request.getLongitude());

        if (distance <= OFFICE_RADIUS_METERS) {
            return processOfficeCheckin(userId, request, distance);
        } else {
            return processFieldCheckin(userId, request, distance);
        }
    }

    /**
     * Kiểm tra nhanh xem tọa độ có trong phạm vi văn phòng không.
     * Dùng tọa độ văn phòng mặc định (trung tâm HN) — cho phép fallback.
     */
    public boolean isWithinOfficeRange(Long userId, double lat, double lng) {
        double officeLat = DEFAULT_OFFICE_LAT;
        double officeLng = DEFAULT_OFFICE_LNG;
        double distance = HaversineUtils.calculateDistanceInMeters(lat, lng, officeLat, officeLng);
        return distance <= OFFICE_RADIUS_METERS;
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
            int kpiPoints = calculateAttendanceKpi(checkinLog.getActionType(), checkinLog.getCheckinTime().toLocalTime());
            kpiCalculationService.updateKpiPoints(
                    checkinLog.getUserId(), "attendance", kpiPoints, checkinLog.getCheckinTime()
            );
            log.info("[Checkin] ✅ Duyệt {} #{} → {} KPI cho userId={}", checkinLog.getActionType(), id, (kpiPoints > 0 ? "+" + kpiPoints : kpiPoints), checkinLog.getUserId());
        } else if ("APPROVED".equals(oldStatus) && !"APPROVED".equals(newStatus.toUpperCase())) {
            int kpiPoints = calculateAttendanceKpi(checkinLog.getActionType(), checkinLog.getCheckinTime().toLocalTime());
            kpiCalculationService.updateKpiPoints(
                    checkinLog.getUserId(), "attendance", -kpiPoints, checkinLog.getCheckinTime()
            );
            log.info("[Checkin] ❌ Thu hồi {} #{} → {} KPI cho userId={}", checkinLog.getActionType(), id, (-kpiPoints > 0 ? "+" + (-kpiPoints) : (-kpiPoints)), checkinLog.getUserId());
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

        // Tính điểm KPI theo mốc thời gian Check-in (08:30) / Check-out (17:30)
        int kpiPoints = calculateAttendanceKpi(finalActionType, now.toLocalTime());
        kpiCalculationService.updateKpiPoints(userId, "attendance", kpiPoints, now);
        
        log.info("[Checkin] ✅ OFFICE {} userId={} lúc {} → {} KPI | distance={}m",
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

        log.info("[Checkin] ⏳ FIELD checkin userId={} lúc {} → PENDING | distance={}m | note={}",
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
    private int calculateAttendanceKpi(String actionType, LocalTime time) {
        if ("CHECK_OUT".equals(actionType)) {
            return 0; // Check-out không tính điểm KPI
        } else {
            // Mặc định là CHECK_IN
            if (!time.isAfter(CUTOFF_CHECKIN)) {
                return KPI_POINTS_ATTENDANCE; // Đi đúng giờ (<= 08:30) -> +5
            } else {
                return 0; // Đi trễ (> 08:30) -> 0
            }
        }
    }
}
