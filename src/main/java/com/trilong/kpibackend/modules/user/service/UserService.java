package com.trilong.kpibackend.modules.user.service;

import com.trilong.kpibackend.modules.attendance.repository.CheckinLogRepository;
import com.trilong.kpibackend.modules.kpi.entity.KpiScore;
import com.trilong.kpibackend.modules.kpi.repository.KpiScoreRepository;
import com.trilong.kpibackend.modules.kpi.service.KpiCalculationService;
import com.trilong.kpibackend.modules.user.dto.CreateUserDTO;
import com.trilong.kpibackend.modules.user.dto.DepartmentDTO;
import com.trilong.kpibackend.modules.user.dto.UpdateUserDTO;
import com.trilong.kpibackend.modules.user.dto.UserDTO;
import com.trilong.kpibackend.modules.user.entity.Department;
import com.trilong.kpibackend.modules.user.entity.User;
import com.trilong.kpibackend.modules.user.repository.DepartmentRepository;
import com.trilong.kpibackend.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final KpiScoreRepository kpiScoreRepository;
    private final KpiCalculationService kpiCalculationService;
    private final CheckinLogRepository checkinLogRepository;

    public List<UserDTO> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /** Lọc nhân viên theo phòng ban, role và trạng thái — dành cho WebAdmin */
    public List<UserDTO> getUsersByFilters(Long departmentId, String role, String status) {
        return userRepository.findByFilters(departmentId, role, status).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public UserDTO getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhân viên với ID: " + id));
        return convertToDTO(user);
    }

    /**
     * Xem profile cá nhân kèm điểm KPI tháng hiện tại và số ngày đã chấm công.
     * Dùng cho màn hình Home của MobileApp.
     */
    public Map<String, Object> getMyProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng"));

        String currentMonth = kpiCalculationService.extractMonth(ZonedDateTime.now());
        Optional<KpiScore> kpiOpt = kpiScoreRepository.findByUserIdAndMonth(userId, currentMonth);

        int totalKpi = kpiOpt.map(KpiScore::getTotal).orElse(0);
        int attendanceKpi = kpiOpt.map(KpiScore::getAttendance).orElse(0);
        int meetingKpi = kpiOpt.map(KpiScore::getMeeting).orElse(0);
        int postKpi = kpiOpt.map(KpiScore::getPost).orElse(0);
        int dealKpi = kpiOpt.map(KpiScore::getDeal).orElse(0);

        // Đếm số ngày đã chấm công được APPROVED trong tháng hiện tại
        ZonedDateTime startOfMonth = ZonedDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        ZonedDateTime endOfMonth = startOfMonth.plusMonths(1).minusSeconds(1);
        long checkinDays = checkinLogRepository
                .findByUserIdAndCheckinTimeBetween(userId, startOfMonth, endOfMonth).stream()
                .filter(c -> "APPROVED".equals(c.getStatus()))
                .count();

        Map<String, Object> profile = new HashMap<>();
        profile.put("user", convertToDTO(user));
        profile.put("currentMonth", currentMonth);
        profile.put("kpi", Map.of(
            "total", totalKpi,
            "attendance", attendanceKpi,
            "meeting", meetingKpi,
            "post", postKpi,
            "deal", dealKpi
        ));
        profile.put("checkinDaysThisMonth", checkinDays);

        return profile;
    }

    @Transactional
    public UserDTO createUser(CreateUserDTO dto) {
        if (userRepository.existsByPhoneNumber(dto.getPhoneNumber())) {
            throw new IllegalArgumentException("Số điện thoại này đã được đăng ký.");
        }

        Department dept = null;
        if (dto.getDepartmentId() != null) {
            dept = departmentRepository.findById(dto.getDepartmentId())
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phòng ban với ID: " + dto.getDepartmentId()));
        }

        User user = User.builder()
                .fullName(dto.getFullName())
                .phoneNumber(dto.getPhoneNumber())
                .passwordHash(passwordEncoder.encode(dto.getPassword()))
                .role(dto.getRole().toUpperCase())
                .status("ACTIVE")
                .department(dept)
                .avatarUrl(dto.getAvatarUrl())
                .basicSalary(dto.getBasicSalary() != null ? dto.getBasicSalary() : 10000000.0)
                .build();

        User saved = userRepository.save(user);

        // Khởi tạo ngay 1 bản ghi KPI 0 điểm cho tháng hiện tại nếu là Sale hoặc Trưởng phòng
        if ("SALE".equals(saved.getRole()) || "TRUONG_PHONG".equals(saved.getRole())) {
            String currentMonth = kpiCalculationService.extractMonth(ZonedDateTime.now());
            KpiScore kpiScore = KpiScore.builder()
                    .user(saved)
                    .month(currentMonth)
                    .attendance(0)
                    .meeting(0)
                    .post(0)
                    .deal(0)
                    .total(0)
                    .isFlagged(false)
                    .build();
            kpiScoreRepository.save(kpiScore);
        }

        return convertToDTO(saved);
    }

    @Transactional
    public UserDTO updateUser(Long id, UpdateUserDTO dto) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhân viên với ID: " + id));

        if (dto.getFullName() != null) {
            user.setFullName(dto.getFullName());
        }
        if (dto.getRole() != null) {
            user.setRole(dto.getRole().toUpperCase());
        }
        if (dto.getStatus() != null) {
            user.setStatus(dto.getStatus().toUpperCase());
        }
        if (dto.getBasicSalary() != null) {
            user.setBasicSalary(dto.getBasicSalary());
        }
        if (dto.getAvatarUrl() != null) {
            user.setAvatarUrl(dto.getAvatarUrl());
        }
        if (dto.getPassword() != null && !dto.getPassword().trim().isEmpty()) {
            user.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
        }
        if (dto.getDepartmentId() != null) {
            Department dept = departmentRepository.findById(dto.getDepartmentId())
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phòng ban với ID: " + dto.getDepartmentId()));
            user.setDepartment(dept);
        }

        User saved = userRepository.save(user);
        return convertToDTO(saved);
    }

    /** Admin đặt lại mật khẩu cho nhân viên */
    @Transactional
    public void resetPassword(Long id, String newPassword) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhân viên với ID: " + id));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    /** Cập nhật trạng thái nhân viên (ACTIVE / INACTIVE / SUSPENDED) */
    @Transactional
    public void updateStatus(Long id, String status) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhân viên với ID: " + id));
        user.setStatus(status);
        userRepository.save(user);
    }

    @Transactional
    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhân viên với ID: " + id));
        user.setStatus("INACTIVE"); // Xoá mềm để giữ data chấm công, kpi, lương
        userRepository.save(user);
    }

    public UserDTO convertToDTO(User user) {
        if (user == null) return null;

        DepartmentDTO deptDTO = null;
        if (user.getDepartment() != null) {
            Department dept = user.getDepartment();
            deptDTO = DepartmentDTO.builder()
                    .id(dept.getId())
                    .name(dept.getName())
                    .officeLat(dept.getOfficeLat())
                    .officeLng(dept.getOfficeLng())
                    .allowedRadius(dept.getAllowedRadius())
                    .build();
        }

        return UserDTO.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .phoneNumber(user.getPhoneNumber())
                .role(user.getRole())
                .status(user.getStatus())
                .avatarUrl(user.getAvatarUrl())
                .basicSalary(user.getBasicSalary())
                .department(deptDTO)
                .createdAt(user.getCreatedAt())
                .build();
    }
}

