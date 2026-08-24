package com.trilong.kpibackend.modules.training.service;

import com.trilong.kpibackend.modules.kpi.service.KpiCalculationService;
import com.trilong.kpibackend.modules.training.dto.CreateTrainingSessionDTO;
import com.trilong.kpibackend.modules.training.entity.TrainingAttendee;
import com.trilong.kpibackend.modules.training.entity.TrainingSession;
import com.trilong.kpibackend.modules.training.repository.TrainingAttendeeRepository;
import com.trilong.kpibackend.modules.training.repository.TrainingSessionRepository;
import com.trilong.kpibackend.modules.user.entity.User;
import com.trilong.kpibackend.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TrainingService {

    private final TrainingSessionRepository trainingSessionRepository;
    private final TrainingAttendeeRepository trainingAttendeeRepository;
    private final UserRepository userRepository;
    private final KpiCalculationService kpiCalculationService;

    private static final int KPI_POINTS_TRAINING = 5; // +5 điểm mỗi buổi đào tạo tham gia

    /**
     * Trần điểm học tập/đào tạo mỗi tuần (theo bảng tiêu chí: "Học tập, Đào tạo:
     * tối đa 15 điểm"). Điểm này nằm trong nhóm Phát triển cá nhân (trần 30đ).
     */
    public static final int CAP_TRAINING_PER_WEEK = 15;

    /**
     * Số điểm đào tạo thực sự được cộng cho lần điểm danh này.
     *
     * <p>Đếm số buổi đã điểm danh trong cùng tuần rồi áp trần 15đ, chỉ cộng phần
     * chênh lệch. Ví dụ buổi thứ 4 trong tuần sẽ cộng 0 điểm vì đã chạm trần.
     */
    private int trainingPointsToAward(Long userId, ZonedDateTime attendedAt) {
        String week = kpiCalculationService.getWeekString(attendedAt);

        long attendedThisWeek = trainingAttendeeRepository.findByUserId(userId).stream()
                .filter(a -> a.getAttendedAt() != null)
                .filter(a -> week.equals(kpiCalculationService.getWeekString(a.getAttendedAt())))
                .count();

        // Bản ghi hiện tại đã được lưu trước khi gọi hàm này
        int after = (int) Math.min(CAP_TRAINING_PER_WEEK, attendedThisWeek * KPI_POINTS_TRAINING);
        int before = (int) Math.min(CAP_TRAINING_PER_WEEK, (attendedThisWeek - 1) * KPI_POINTS_TRAINING);
        return Math.max(0, after - before);
    }

    @Transactional
    public TrainingSession createSession(CreateTrainingSessionDTO dto) {
        if (trainingSessionRepository.findByRoomCode(dto.getRoomCode()).isPresent()) {
            throw new IllegalArgumentException("Mã phòng đào tạo '" + dto.getRoomCode() + "' đã tồn tại!");
        }

        TrainingSession session = TrainingSession.builder()
                .title(dto.getTitle())
                .description(dto.getDescription())
                .presenter(dto.getPresenter())
                .roomCode(dto.getRoomCode())
                .startTime(dto.getStartTime() != null ? dto.getStartTime() : ZonedDateTime.now())
                .location(dto.getLocation())
                .maxSlots(dto.getMaxSlots() != null ? dto.getMaxSlots() : 20)
                .durationMinutes(dto.getDurationMinutes() != null ? dto.getDurationMinutes() : 120)
                .status("UPCOMING")
                .photoUrl(dto.getPhotoUrl())
                .videoUrl(dto.getVideoUrl())
                .build();

        return trainingSessionRepository.save(session);
    }

    public List<TrainingSession> getAllSessions() {
        return trainingSessionRepository.findAllByOrderByStartTimeDesc();
    }

    /**
     * Lịch đào tạo của TUẦN NÀY, dùng cho màn hình Đào tạo trên ứng dụng.
     *
     * <p>Cửa sổ là trọn tuần ISO — thứ Hai đến hết Chủ nhật — khớp với cách hệ
     * thống chấm điểm đào tạo theo tuần. Nhờ vậy nhân sự nhìn một màn hình là
     * biết tuần này còn buổi nào chưa dự.
     *
     * <p>Thứ tự: <b>đang diễn ra</b> lên trước, rồi <b>sắp diễn ra</b> theo giờ
     * gần nhất, cuối cùng là <b>đã kết thúc</b> theo giờ mới nhất. Buổi đã hủy
     * không hiện.
     */
    public List<TrainingSession> getActiveSessions() {
        ZoneId vnZone = ZoneId.of("Asia/Ho_Chi_Minh");
        java.time.LocalDate thuHai = java.time.LocalDate.now(vnZone)
                .with(java.time.temporal.WeekFields.ISO.dayOfWeek(), 1);
        ZonedDateTime tu = thuHai.atStartOfDay(vnZone);
        ZonedDateTime den = thuHai.plusDays(7).atStartOfDay(vnZone);

        List<TrainingSession> tuan = new java.util.ArrayList<>(
                trainingSessionRepository.findTrongKhoang(tu, den));
        tuan.sort(java.util.Comparator
                .comparingInt((TrainingSession s) -> thuTuHienThi(s.getDisplayStatus()))
                .thenComparing(s -> {
                    // Sắp diễn ra: gần nhất trước. Đã kết thúc: mới nhất trước.
                    boolean daXong = "COMPLETED".equals(s.getDisplayStatus());
                    long moc = s.getStartTime() == null ? 0 : s.getStartTime().toEpochSecond();
                    return daXong ? -moc : moc;
                }));
        return tuan;
    }

    /** Đang diễn ra (0) → sắp diễn ra (1) → đã kết thúc (2). */
    private int thuTuHienThi(String displayStatus) {
        if ("ONGOING".equals(displayStatus)) return 0;
        if ("UPCOMING".equals(displayStatus)) return 1;
        return 2;
    }

    public List<TrainingSession> getSessionsByStatus(String status) {
        return trainingSessionRepository.findByStatusOrderByStartTimeDesc(status);
    }

    /**
     * Lấy danh sách buổi đào tạo ĐÃ KẾT THÚC (status = COMPLETED),
     * sắp xếp theo thời gian mới nhất (gần đây trước). Dùng cho màn hình Kho Tài Liệu Đào Tạo Mobile.
     */
    public List<TrainingSession> getCompletedSessions() {
        return trainingSessionRepository.findByStatusOrderByStartTimeDesc("COMPLETED");
    }

    public TrainingSession getSessionById(Long id) {
        return trainingSessionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy buổi đào tạo có ID: " + id));
    }

    public long getAttendeeCount(Long sessionId) {
        return trainingAttendeeRepository.countBySessionId(sessionId);
    }

    public List<TrainingAttendee> getSessionAttendees(Long sessionId) {
        return trainingAttendeeRepository.findBySessionId(sessionId);
    }

    public List<TrainingAttendee> getMyTrainings(Long userId) {
        return trainingAttendeeRepository.findByUserId(userId);
    }

    @Transactional
    public TrainingAttendee attendTraining(Long userId, String qrData) {
        // Hỗ trợ cả 2 format:
        // 1. Format mới: "roomCode:token" (token xoay 10s, đồng bộ Web Admin)
        // 2. Format cũ: "roomCode" (backward compatible)
        String roomCode;
        String tokenStr = null;

        if (qrData != null && qrData.contains(":")) {
            int lastColon = qrData.lastIndexOf(":");
            roomCode = qrData.substring(0, lastColon);
            tokenStr = qrData.substring(lastColon + 1);
        } else {
            roomCode = qrData;
        }

        // Verify token nếu có (tolerant ±1 window = 30s để tránh lỗi đồng hồ lệch nhẹ)
        if (tokenStr != null && !tokenStr.isEmpty()) {
            long nowWindow = System.currentTimeMillis() / 10000;
            boolean valid = false;
            for (long w = nowWindow - 1; w <= nowWindow + 1; w++) {
                long expected = (w * 31337L) % 999999L;
                String expectedStr = String.format("%06d", expected);
                if (expectedStr.equals(tokenStr)) {
                    valid = true;
                    break;
                }
            }
            if (!valid) {
                throw new IllegalArgumentException("Mã QR đã hết hạn! Vui lòng quét lại mã QR mới nhất.");
            }
        }

        TrainingSession session = trainingSessionRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new IllegalArgumentException("Mã phòng học không hợp lệ hoặc không tồn tại!"));

        if ("CANCELLED".equals(session.getStatus())) {
            throw new IllegalStateException("Buổi đào tạo này đã bị hủy bỏ!");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng"));

        // Kiểm tra xem đã điểm danh chưa
        if (trainingAttendeeRepository.existsBySessionIdAndUserId(session.getId(), userId)) {
            throw new IllegalStateException("Bạn đã điểm danh cho buổi học này rồi!");
        }

        // Kiểm tra giới hạn số lượng tham gia
        long currentAttendees = trainingAttendeeRepository.countBySessionId(session.getId());
        if (currentAttendees >= session.getMaxSlots()) {
            throw new IllegalStateException("Phòng học đã đạt giới hạn số lượng học viên tối đa (" + session.getMaxSlots() + ")!");
        }

        TrainingAttendee attendee = TrainingAttendee.builder()
                .sessionId(session.getId())
                .userId(userId)
                .session(session)
                .user(user)
                .attendedAt(ZonedDateTime.now())
                .build();

        TrainingAttendee savedAttendee = trainingAttendeeRepository.save(attendee);

        // Cộng điểm học tập, áp trần 15đ/tuần
        int pts = trainingPointsToAward(userId, attendee.getAttendedAt());
        if (pts > 0) {
            kpiCalculationService.updateKpiPoints(userId, "attendance", pts, attendee.getAttendedAt(),
                    "Học buổi đào tạo “" + session.getTitle() + "”");
        }

        return savedAttendee;
    }

    @Transactional
    public TrainingAttendee addManualAttendee(Long sessionId, Long userId) {
        TrainingSession session = trainingSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Mã phòng học không hợp lệ hoặc không tồn tại!"));

        if ("CANCELLED".equals(session.getStatus())) {
            throw new IllegalStateException("Buổi đào tạo này đã bị hủy bỏ!");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng"));

        // Kiểm tra xem đã điểm danh chưa
        if (trainingAttendeeRepository.existsBySessionIdAndUserId(sessionId, userId)) {
            throw new IllegalStateException("Học viên này đã được điểm danh rồi!");
        }

        // Kiểm tra giới hạn số lượng tham gia
        long currentAttendees = trainingAttendeeRepository.countBySessionId(sessionId);
        if (currentAttendees >= session.getMaxSlots()) {
            throw new IllegalStateException("Phòng học đã đạt giới hạn số lượng học viên tối đa (" + session.getMaxSlots() + ")!");
        }

        TrainingAttendee attendee = TrainingAttendee.builder()
                .sessionId(sessionId)
                .userId(userId)
                .session(session)
                .user(user)
                .attendedAt(ZonedDateTime.now())
                .build();

        TrainingAttendee savedAttendee = trainingAttendeeRepository.save(attendee);
        // Điểm danh thủ công cũng áp trần 15đ/tuần như quét QR
        int pts = trainingPointsToAward(userId, attendee.getAttendedAt());
        if (pts > 0) {
            kpiCalculationService.updateKpiPoints(userId, "attendance", pts, attendee.getAttendedAt(),
                    "Được điểm danh buổi đào tạo “" + session.getTitle() + "”");
        }
        return savedAttendee;
    }

    @Transactional
    public void removeAttendee(Long sessionId, Long userId) {
        com.trilong.kpibackend.modules.training.entity.TrainingAttendeeId id = new com.trilong.kpibackend.modules.training.entity.TrainingAttendeeId(sessionId, userId);
        TrainingAttendee attendee = trainingAttendeeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Học viên chưa điểm danh buổi học này!"));

        kpiCalculationService.updateKpiPoints(userId, "attendance", -KPI_POINTS_TRAINING,
                attendee.getAttendedAt(), "Bị gỡ khỏi danh sách học buổi “" + tenBuoi(sessionId) + "”");
        trainingAttendeeRepository.delete(attendee);
    }

    /** Tên buổi đào tạo để ghép vào nhật ký điểm; không có thì gọi theo mã. */
    private String tenBuoi(Long sessionId) {
        return trainingSessionRepository.findById(sessionId)
                .map(TrainingSession::getTitle)
                .filter(t -> t != null && !t.isBlank())
                .orElse("buổi đào tạo #" + sessionId);
    }

    @Transactional
    public void deleteSession(Long sessionId) {
        TrainingSession session = trainingSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy buổi đào tạo có ID: " + sessionId));

        // Trừ điểm của tất cả học viên đã tham gia lớp này
        List<TrainingAttendee> attendees = trainingAttendeeRepository.findBySessionId(sessionId);
        for (TrainingAttendee attendee : attendees) {
            kpiCalculationService.updateKpiPoints(attendee.getUserId(), "attendance", -KPI_POINTS_TRAINING,
                    attendee.getAttendedAt(), "Buổi đào tạo “" + session.getTitle() + "” đã bị xóa");
        }

        // Xóa tất cả học viên điểm danh trước để tránh lỗi Foreign Key
        trainingAttendeeRepository.deleteAll(attendees);

        trainingSessionRepository.delete(session);
    }

    @Transactional
    public TrainingSession updateSessionStatus(Long sessionId, String status) {
        TrainingSession session = trainingSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy buổi đào tạo có ID: " + sessionId));

        String oldStatus = session.getStatus();
        if (oldStatus.equals(status)) {
            return session;
        }

        session.setStatus(status);
        TrainingSession saved = trainingSessionRepository.save(session);

        // Nếu chuyển sang CANCELLED, thu hồi điểm của tất cả mọi người
        if ("CANCELLED".equals(status)) {
            List<TrainingAttendee> attendees = trainingAttendeeRepository.findBySessionId(sessionId);
            for (TrainingAttendee attendee : attendees) {
                kpiCalculationService.updateKpiPoints(attendee.getUserId(), "attendance", -KPI_POINTS_TRAINING,
                        attendee.getAttendedAt(), "Buổi đào tạo “" + session.getTitle() + "” đã bị hủy");
            }
        }

        return saved;
    }

    /**
     * Cập nhật thông tin chi tiết buổi đào tạo (Admin/Trưởng phòng).
     */
    @Transactional
    public TrainingSession updateSessionDetails(Long sessionId, CreateTrainingSessionDTO dto) {
        TrainingSession session = trainingSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy buổi đào tạo có ID: " + sessionId));

        // Kiểm tra trùng roomCode nếu thay đổi roomCode
        if (dto.getRoomCode() != null && !dto.getRoomCode().equals(session.getRoomCode())) {
            if (trainingSessionRepository.findByRoomCode(dto.getRoomCode()).isPresent()) {
                throw new IllegalArgumentException("Mã phòng đào tạo '" + dto.getRoomCode() + "' đã tồn tại!");
            }
            session.setRoomCode(dto.getRoomCode());
        }

        if (dto.getTitle() != null) session.setTitle(dto.getTitle());
        if (dto.getDescription() != null) session.setDescription(dto.getDescription());
        if (dto.getPresenter() != null) session.setPresenter(dto.getPresenter());
        if (dto.getStartTime() != null) session.setStartTime(dto.getStartTime());
        if (dto.getLocation() != null) session.setLocation(dto.getLocation());
        if (dto.getMaxSlots() != null) session.setMaxSlots(dto.getMaxSlots());
        if (dto.getDurationMinutes() != null) session.setDurationMinutes(dto.getDurationMinutes());
        if (dto.getPhotoUrl() != null) session.setPhotoUrl(dto.getPhotoUrl());
        // Cập nhật video URL (Admin điền sau khi buổi học kết thúc hoặc xóa)
        if (dto.getVideoUrl() != null) {
            session.setVideoUrl(dto.getVideoUrl().isBlank() ? null : dto.getVideoUrl());
        }
        // Cập nhật trạng thái buổi học (Admin thay đổi từ form Edit)
        if (dto.getStatus() != null && !dto.getStatus().isBlank()) {
            String oldStatus = session.getStatus();
            String newStatus = dto.getStatus();
            session.setStatus(newStatus);
            // Nếu chuyển sang CANCELLED, thu hồi điểm KPI của học viên
            if ("CANCELLED".equals(newStatus) && !oldStatus.equals(newStatus)) {
                List<TrainingAttendee> attendees = trainingAttendeeRepository.findBySessionId(sessionId);
                for (TrainingAttendee attendee : attendees) {
                    kpiCalculationService.updateKpiPoints(attendee.getUserId(), "attendance", -KPI_POINTS_TRAINING,
                            attendee.getAttendedAt(), "Buổi đào tạo “" + session.getTitle() + "” đã bị hủy");
                }
            }
        }

        return trainingSessionRepository.save(session);
    }
}

