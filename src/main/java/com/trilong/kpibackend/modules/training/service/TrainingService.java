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
                .status("UPCOMING")
                .photoUrl(dto.getPhotoUrl())
                .build();

        return trainingSessionRepository.save(session);
    }

    public List<TrainingSession> getAllSessions() {
        return trainingSessionRepository.findAllByOrderByStartTimeDesc();
    }

    /**
     * Lấy danh sách buổi học "đang hiển thị trên app":
     * Chỉ trả về buổi UPCOMING có startTime >= đầu ngày hôm nay.
     * Buổi từ hôm qua trở về trước sẽ bị ẩn.
     */
    public List<TrainingSession> getActiveSessions() {
        ZonedDateTime todayStart = ZonedDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"))
                .toLocalDate()
                .atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh"));
        return trainingSessionRepository.findActiveSessionsFromToday(todayStart);
    }

    public List<TrainingSession> getSessionsByStatus(String status) {
        return trainingSessionRepository.findByStatusOrderByStartTimeDesc(status);
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

        // Cộng điểm KPI attendance tháng hiện tại
        kpiCalculationService.updateKpiPoints(userId, "attendance", KPI_POINTS_TRAINING, attendee.getAttendedAt());

        return savedAttendee;
    }

    @Transactional
    public void deleteSession(Long sessionId) {
        TrainingSession session = trainingSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy buổi đào tạo có ID: " + sessionId));

        // Trừ điểm của tất cả học viên đã tham gia lớp này
        List<TrainingAttendee> attendees = trainingAttendeeRepository.findBySessionId(sessionId);
        for (TrainingAttendee attendee : attendees) {
            kpiCalculationService.updateKpiPoints(attendee.getUserId(), "attendance", -KPI_POINTS_TRAINING, attendee.getAttendedAt());
        }

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
                kpiCalculationService.updateKpiPoints(attendee.getUserId(), "attendance", -KPI_POINTS_TRAINING, attendee.getAttendedAt());
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
        if (dto.getPhotoUrl() != null) session.setPhotoUrl(dto.getPhotoUrl());

        return trainingSessionRepository.save(session);
    }
}

