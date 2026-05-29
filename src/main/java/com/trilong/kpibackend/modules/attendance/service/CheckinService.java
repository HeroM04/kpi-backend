package com.trilong.kpibackend.modules.attendance.service;

import com.trilong.kpibackend.core.utils.HaversineUtils;
import com.trilong.kpibackend.modules.attendance.dto.CheckinRequestDTO;
import com.trilong.kpibackend.modules.attendance.entity.CheckinLog;
import com.trilong.kpibackend.modules.attendance.repository.CheckinLogRepository;
import com.trilong.kpibackend.modules.user.entity.Department;
import com.trilong.kpibackend.modules.user.entity.User;
import com.trilong.kpibackend.core.utils.RekognitionService;
import com.trilong.kpibackend.modules.user.repository.UserRepository;
import com.trilong.kpibackend.modules.kpi.service.KpiCalculationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

/**
 * CheckinService â€” xá»­ lÃ½ cháº¥m cÃ´ng tá»± Ä‘á»™ng Ä‘á»‹nh vá»‹ GPS (Geofencing).
 *
 * Tá»a Ä‘á»™ vÃ  bÃ¡n kÃ­nh Ä‘Æ°á»£c tÃ­nh Ä‘á»™ng theo tá»«ng phÃ²ng ban/chi nhÃ¡nh cá»§a User trong DB.
 */
@Service
@Slf4j
public class CheckinService {

    @Autowired
    private org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;

    @Autowired
    private CheckinLogRepository checkinLogRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RekognitionService rekognitionService;

    @Value("${app.rekognition.enabled:false}")
    private boolean rekognitionEnabled;

    @Autowired
    private KpiCalculationService kpiCalculationService;

    /**
     * XÃ¡c thá»±c khuÃ´n máº·t trÆ°á»›c khi cháº¥m cÃ´ng.
     */
    private void verifyFace(User user, String photoUrl) {
        if (!rekognitionEnabled) {
            return;
        }

        if (photoUrl == null || photoUrl.isBlank()) {
            throw new RuntimeException("áº¢nh check-in khÃ´ng Ä‘Æ°á»£c Ä‘á»ƒ trá»‘ng khi báº­t tÃ­nh nÄƒng xÃ¡c thá»±c khuÃ´n máº·t.");
        }

        if (user.getAvatarUrl() == null || user.getAvatarUrl().isBlank()) {
            // Tá»± Ä‘á»™ng Ä‘Äƒng kÃ½ áº£nh Ä‘áº¡i diá»‡n gá»‘c trong láº§n check-in Ä‘áº§u tiÃªn
            user.setAvatarUrl(photoUrl);
            userRepository.save(user);
            log.info("ÄÄƒng kÃ½ áº£nh khuÃ´n máº·t gá»‘c tá»± Ä‘á»™ng cho user {} thÃ nh cÃ´ng. URL: {}", user.getId(), photoUrl);
        } else {
            // So khá»›p khuÃ´n máº·t
            boolean matched = rekognitionService.compareFaces(user.getAvatarUrl(), photoUrl);
            if (!matched) {
                throw new RuntimeException("XÃ¡c thá»±c khuÃ´n máº·t tháº¥t báº¡i. KhuÃ´n máº·t khÃ´ng trÃ¹ng khá»›p vá»›i áº£nh Ä‘áº¡i diá»‡n gá»‘c.");
            }
        }
    }

    private static final double DEFAULT_LAT = 21.028511; // Máº·c Ä‘á»‹nh VP HÃ  Ná»™i
    private static final double DEFAULT_LNG = 105.804817;
    private static final int DEFAULT_RADIUS = 500;
    private static final int KPI_POINTS_ATTENDANCE = 5; // +5 Ä‘iá»ƒm má»—i khi cháº¥m cÃ´ng thÃ nh cÃ´ng

    /**
     * Kiá»ƒm tra nhanh xem tá»a Ä‘á»™ cÃ³ náº±m trong pháº¡m vi vÄƒn phÃ²ng cá»§a user khÃ´ng.
     */
    public boolean isWithinRange(double userLat, double userLon) {
        // Fallback kiá»ƒm tra khoáº£ng cÃ¡ch vá»›i vÄƒn phÃ²ng trung tÃ¢m
        double distance = HaversineUtils.calculateDistanceInMeters(userLat, userLon, DEFAULT_LAT, DEFAULT_LNG);
        return distance <= 500; // Äá»ƒ 500m há»— trá»£ test dá»… dÃ ng
    }

    /**
     * Kiá»ƒm tra khoáº£ng cÃ¡ch chÃ­nh xÃ¡c vá»›i tá»a Ä‘á»™ chi nhÃ¡nh cá»§a nhÃ¢n viÃªn.
     */
    public boolean isWithinRangeForUser(Long userId, double userLat, double userLon) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("KhÃ´ng tÃ¬m tháº¥y nhÃ¢n viÃªn"));
        
        Department dept = user.getDepartment();
        double officeLat = dept != null && dept.getOfficeLat() != null ? dept.getOfficeLat() : DEFAULT_LAT;
        double officeLng = dept != null && dept.getOfficeLng() != null ? dept.getOfficeLng() : DEFAULT_LNG;
        int allowedRadius = dept != null && dept.getAllowedRadius() != null ? dept.getAllowedRadius() : DEFAULT_RADIUS;

        double distance = HaversineUtils.calculateDistanceInMeters(userLat, userLon, officeLat, officeLng);
        return distance <= allowedRadius;
    }

    /**
     * Xá»­ lÃ½ cháº¥m cÃ´ng vÄƒn phÃ²ng (Auto-approve náº¿u Ä‘Ãºng tá»a Ä‘á»™).
     */
    @Transactional
    public CheckinLog processCheckin(Long userId, CheckinRequestDTO request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("KhÃ´ng tÃ¬m tháº¥y nhÃ¢n viÃªn"));

        verifyFace(user, request.getPhotoUrl());

        Department dept = user.getDepartment();
        double officeLat = dept != null && dept.getOfficeLat() != null ? dept.getOfficeLat() : DEFAULT_LAT;
        double officeLng = dept != null && dept.getOfficeLng() != null ? dept.getOfficeLng() : DEFAULT_LNG;
        int allowedRadius = dept != null && dept.getAllowedRadius() != null ? dept.getAllowedRadius() : DEFAULT_RADIUS;

        double distance = HaversineUtils.calculateDistanceInMeters(
                request.getLatitude(), request.getLongitude(), officeLat, officeLng
        );

        String checkinType = (distance <= allowedRadius) ? "OFFICE" : "FIELD";
        String status = (distance <= allowedRadius) ? "APPROVED" : "PENDING";

        // XÃ¡c Ä‘á»‹nh loáº¡i hÃ nh Ä‘á»™ng: CHECK_IN hoáº·c CHECK_OUT
        String actionType = (request.getActionType() != null && !request.getActionType().isBlank())
                ? request.getActionType().toUpperCase() : "CHECK_IN";

        CheckinLog log = new CheckinLog();
        log.setUserId(userId);
        log.setLatitude(request.getLatitude());
        log.setLongitude(request.getLongitude());
        log.setDistanceToOffice(distance);
        log.setPhotoUrl(request.getPhotoUrl());
        log.setNote(request.getNote());
        log.setCheckinType(checkinType);
        log.setStatus(status);
        log.setActionType(actionType);
        log.setCheckinTime(ZonedDateTime.now());

        CheckinLog savedLog = checkinLogRepository.save(log);
        try { messagingTemplate.convertAndSend("/topic/admin/requests", (Object) java.util.Map.of("type", "CHECKIN", "message", "Co yeu cau cham cong moi!")); } catch(Exception e){}

        if ("APPROVED".equals(status)) {
            kpiCalculationService.updateKpiPoints(userId, "attendance", KPI_POINTS_ATTENDANCE, ZonedDateTime.now());
        }

        return savedLog;
    }

    /**
     * Xá»­ lÃ½ gá»­i yÃªu cáº§u cháº¥m cÃ´ng ngoÃ i vÄƒn phÃ²ng (Chá» duyá»‡t).
     */
    @Transactional
    public CheckinLog processFieldCheckin(Long userId, CheckinRequestDTO request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("KhÃ´ng tÃ¬m tháº¥y nhÃ¢n viÃªn"));

        verifyFace(user, request.getPhotoUrl());

        Department dept = user.getDepartment();
        double officeLat = dept != null && dept.getOfficeLat() != null ? dept.getOfficeLat() : DEFAULT_LAT;
        double officeLng = dept != null && dept.getOfficeLng() != null ? dept.getOfficeLng() : DEFAULT_LNG;

        double distance = HaversineUtils.calculateDistanceInMeters(
                request.getLatitude(), request.getLongitude(), officeLat, officeLng
        );

        String actionType = (request.getActionType() != null && !request.getActionType().isBlank())
                ? request.getActionType().toUpperCase() : "CHECK_IN";

        CheckinLog log = new CheckinLog();
        log.setUserId(userId);
        log.setLatitude(request.getLatitude());
        log.setLongitude(request.getLongitude());
        log.setDistanceToOffice(distance);
        log.setPhotoUrl(request.getPhotoUrl());
        log.setNote(request.getNote() != null ? request.getNote() : "Cháº¥m cÃ´ng ngoÃ i vÄƒn phÃ²ng");
        log.setCheckinType("FIELD");
        log.setStatus("PENDING");
        log.setActionType(actionType);
        log.setCheckinTime(ZonedDateTime.now());

        CheckinLog savedLog = checkinLogRepository.save(log);
        try { messagingTemplate.convertAndSend("/topic/admin/requests", (Object) java.util.Map.of("type", "CHECKIN", "message", "Co yeu cau cham cong moi!")); } catch(Exception e){}
        return savedLog;
    }

    /**
     * Láº¥y lá»‹ch sá»­ cháº¥m cÃ´ng cá»§a má»™t nhÃ¢n viÃªn.
     */
    public List<CheckinLog> getCheckinsByUserId(Long userId) {
        return checkinLogRepository.findByUserIdOrderByCheckinTimeDesc(userId);
    }

    /**
     * Láº¥y lá»‹ch sá»­ cháº¥m cÃ´ng cá»§a má»™t nhÃ¢n viÃªn theo ngÃ y cá»¥ thá»ƒ.
     */
    public List<CheckinLog> getCheckinsByUserIdAndDate(Long userId, LocalDate date) {
        return checkinLogRepository.findByUserIdOrderByCheckinTimeDesc(userId).stream()
                .filter(l -> {
                    if (l.getCheckinTime() == null) return false;
                    LocalDate local1 = l.getCheckinTime().toLocalDate();
                    LocalDate local2 = l.getCheckinTime().withZoneSameInstant(java.time.ZoneId.systemDefault()).toLocalDate();
                    LocalDate local3 = l.getCheckinTime().withZoneSameInstant(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate();
                    
                    return local1.equals(date) || local2.equals(date) || local3.equals(date);
                })
                .toList();
    }

    /**
     * Láº¥y danh sÃ¡ch cháº¥m cÃ´ng chá» duyá»‡t (Cho Admin/TrÆ°á»Ÿng phÃ²ng).
     */
    public List<CheckinLog> getPendingCheckins() {
        return checkinLogRepository.findByStatusOrderByCheckinTimeDesc("PENDING");
    }

    /**
     * Duyá»‡t Ä‘Æ¡n cháº¥m cÃ´ng dÃ£ ngoáº¡i cá»§a nhÃ¢n viÃªn.
     */
    @Transactional
    public void processApproval(Map<String, Object> request) {
        Long logId = ((Number) request.get("logId")).longValue();
        String status = (String) request.get("status"); // APPROVED hoáº·c REJECTED
        String reason = (String) request.get("reason");

        updateStatus(logId, status, reason);
    }

    /**
     * Cáº­p nháº­t tráº¡ng thÃ¡i Ä‘iá»ƒm danh (REST API)
     */
    @Transactional
    public CheckinLog updateStatus(Long id, String status, String reason) {
        CheckinLog log = checkinLogRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("KhÃ´ng tÃ¬m tháº¥y báº£n ghi cháº¥m cÃ´ng"));

        String oldStatus = log.getStatus();
        log.setStatus(status);
        if (reason != null && !reason.trim().isEmpty()) {
            log.setNote(log.getNote() + " | Pháº£n há»“i duyá»‡t: " + reason);
        }

        CheckinLog savedLog = checkinLogRepository.save(log);
        try { messagingTemplate.convertAndSend("/topic/admin/requests", (Object) java.util.Map.of("type", "CHECKIN", "message", "Co yeu cau cham cong moi!")); } catch(Exception e){}

        // Xá»­ lÃ½ cá»™ng/trá»« KPI
        if (!"APPROVED".equals(oldStatus) && "APPROVED".equals(status)) {
            kpiCalculationService.updateKpiPoints(log.getUserId(), "attendance", KPI_POINTS_ATTENDANCE, log.getCheckinTime());
        } else if ("APPROVED".equals(oldStatus) && !"APPROVED".equals(status)) {
            kpiCalculationService.updateKpiPoints(log.getUserId(), "attendance", -KPI_POINTS_ATTENDANCE, log.getCheckinTime());
        }

        return savedLog;
    }

    /**
     * Láº¥y chi tiáº¿t báº£n ghi cháº¥m cÃ´ng.
     */
    public CheckinLog getCheckinById(Long id) {
        return checkinLogRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("KhÃ´ng tÃ¬m tháº¥y báº£n ghi cháº¥m cÃ´ng cÃ³ ID: " + id));
    }

    /**
     * Láº¥y toÃ n bá»™ lá»‹ch sá»­ cháº¥m cÃ´ng trong há»‡ thá»‘ng.
     */
    public List<CheckinLog> getAllCheckins() {
        return checkinLogRepository.findAll();
    }

    /**
     * XÃ³a báº£n ghi cháº¥m cÃ´ng.
     */
    @Transactional
    public void deleteCheckin(Long id) {
        CheckinLog log = checkinLogRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("KhÃ´ng tÃ¬m tháº¥y báº£n ghi cháº¥m cÃ´ng cÃ³ ID: " + id));
        checkinLogRepository.delete(log);
    }
}
