package com.trilong.kpibackend.modules.training.controller;

import com.trilong.kpibackend.core.security.UserPrincipal;
import com.trilong.kpibackend.modules.training.dto.*;
import com.trilong.kpibackend.modules.training.entity.TrainingAttendee;
import com.trilong.kpibackend.modules.training.entity.TrainingSession;
import com.trilong.kpibackend.modules.training.service.TrainingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/training-sessions")
@RequiredArgsConstructor
@Tag(name = "Training Sessions", description = "Quản lý các buổi đào tạo & điểm danh QR Code")
@SecurityRequirement(name = "Bearer Authentication")
public class TrainingController {

    private final TrainingService trainingService;

    @Operation(summary = "Tạo phòng đào tạo mới", description = "Dành cho Admin/Trưởng phòng.")
    @PostMapping
    @PreAuthorize("hasAuthority('training:manage') or hasRole('ADMIN')")
    public ResponseEntity<?> createSession(@Valid @RequestBody CreateTrainingSessionDTO dto) {
        TrainingSession session = trainingService.createSession(dto);
        long count = trainingService.getAttendeeCount(session.getId());
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Tạo phòng học đào tạo thành công!",
                "data", TrainingSessionResponseDTO.from(session, count)
        ));
    }

    @Operation(summary = "Lấy danh sách buổi đào tạo đang hiển thị (Mobile)",
            description = "Chỉ trả về buổi UPCOMING từ hôm nay trở đi. Buổi quá ngày sẽ bị ẩn tự động.")
    @GetMapping("/active")
    @PreAuthorize("hasAuthority('training:view')")
    public ResponseEntity<?> getActiveSessions() {
        List<TrainingSession> sessions = trainingService.getActiveSessions();

        List<TrainingSessionResponseDTO> dtos = sessions.stream()
                .map(s -> {
                    long count = trainingService.getAttendeeCount(s.getId());
                    TrainingSessionResponseDTO dto = TrainingSessionResponseDTO.from(s, count);
                    List<com.trilong.kpibackend.modules.training.entity.TrainingAttendee> attendees = trainingService.getSessionAttendees(s.getId());
                    List<TrainingAttendeeResponseDTO> attendeeDtos = attendees.stream()
                            .map(TrainingAttendeeResponseDTO::from)
                            .toList();
                    dto.setAttendees(attendeeDtos);
                    return dto;
                }).toList();

        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", dtos));
    }

    @Operation(summary = "Lấy danh sách các buổi đào tạo", description = "Xem danh sách lớp học hiện tại và lịch sử.")
    @GetMapping
    @PreAuthorize("hasAuthority('training:view')")
    public ResponseEntity<?> getAllSessions(@RequestParam(required = false) String status) {
        List<TrainingSession> sessions;
        if (status != null && !status.trim().isEmpty()) {
            sessions = trainingService.getSessionsByStatus(status);
        } else {
            sessions = trainingService.getAllSessions();
        }

        List<TrainingSessionResponseDTO> dtos = sessions.stream()
                .map(s -> {
                    long count = trainingService.getAttendeeCount(s.getId());
                    TrainingSessionResponseDTO dto = TrainingSessionResponseDTO.from(s, count);
                    List<com.trilong.kpibackend.modules.training.entity.TrainingAttendee> attendees = trainingService.getSessionAttendees(s.getId());
                    List<TrainingAttendeeResponseDTO> attendeeDtos = attendees.stream()
                            .map(TrainingAttendeeResponseDTO::from)
                            .toList();
                    dto.setAttendees(attendeeDtos);
                    return dto;
                }).toList();

        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", dtos));
    }

    @Operation(summary = "Lấy lịch sử đào tạo cá nhân", description = "Xem danh sách các buổi đào tạo đã tham gia.")
    @GetMapping("/my-trainings")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getMyTrainings(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam(required = false) String date) {
        LocalDate filterDate = (date != null && !date.trim().isEmpty()) ? LocalDate.parse(date) : LocalDate.now();
        List<TrainingAttendee> attendees = trainingService.getMyTrainings(currentUser.getUserId()).stream()
                .filter(a -> a.getAttendedAt() != null && a.getAttendedAt().toLocalDate().equals(filterDate))
                .toList();
        List<TrainingAttendeeResponseDTO> dtos = attendees.stream()
                .map(TrainingAttendeeResponseDTO::from)
                .toList();
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", dtos));
    }

    @Operation(summary = "Xem chi tiết buổi đào tạo", description = "Lấy thông tin và danh sách nhân viên đã điểm danh.")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('training:view')")
    public ResponseEntity<?> getSessionById(@PathVariable Long id) {
        TrainingSession session = trainingService.getSessionById(id);
        long count = trainingService.getAttendeeCount(id);

        TrainingSessionResponseDTO dto = TrainingSessionResponseDTO.from(session, count);

        // Nạp danh sách học viên
        List<TrainingAttendee> attendees = trainingService.getSessionAttendees(id);
        List<TrainingAttendeeResponseDTO> attendeeDtos = attendees.stream()
                .map(TrainingAttendeeResponseDTO::from)
                .toList();
        dto.setAttendees(attendeeDtos);

        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", dto));
    }

    @Operation(summary = "Quét mã điểm danh", description = "Học viên quét mã QR (roomCode) để điểm danh lớp học.")
    @PostMapping("/attend")
    @PreAuthorize("hasAuthority('training:attend')")
    public ResponseEntity<?> attendTraining(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody AttendRequestDTO request) {
        TrainingAttendee attendee = trainingService.attendTraining(currentUser.getUserId(), request.getRoomCode());
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Điểm danh buổi học thành công!",
                "data", TrainingAttendeeResponseDTO.from(attendee)
        ));
    }

    @Operation(summary = "Cập nhật thông tin chi tiết buổi đào tạo", description = "Dành cho Admin/Trưởng phòng.")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('training:manage') or hasRole('ADMIN')")
    public ResponseEntity<?> updateSession(
            @PathVariable Long id,
            @RequestBody CreateTrainingSessionDTO dto) {
        try {
            TrainingSession session = trainingService.updateSessionDetails(id, dto);
            long count = trainingService.getAttendeeCount(id);
            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "message", "Cập nhật thông tin phòng học thành công!",
                    "data", TrainingSessionResponseDTO.from(session, count)
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    @Operation(summary = "Cập nhật trạng thái buổi đào tạo", description = "Dành cho Admin/Trưởng phòng (UPCOMING, COMPLETED, CANCELLED).")
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('training:manage') or hasRole('ADMIN')")
    public ResponseEntity<?> updateStatus(
            @PathVariable Long id,
            @RequestParam String status) {

        TrainingSession session = trainingService.updateSessionStatus(id, status);
        long count = trainingService.getAttendeeCount(id);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Cập nhật trạng thái phòng học thành công!",
                "data", TrainingSessionResponseDTO.from(session, count)
        ));
    }

    @Operation(summary = "Xóa buổi đào tạo", description = "Dành cho Admin. Xóa phòng và thu hồi điểm KPI của tất cả học viên đã điểm danh.")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteSession(@PathVariable Long id) {
        trainingService.deleteSession(id);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Đã xóa phòng đào tạo và thu hồi điểm của học viên liên quan."
        ));
    }
}
