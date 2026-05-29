package com.trilong.kpibackend.modules.payroll.service;

import com.trilong.kpibackend.modules.attendance.entity.CheckinLog;
import com.trilong.kpibackend.modules.attendance.repository.CheckinLogRepository;
import com.trilong.kpibackend.modules.kpi.entity.KpiScore;
import com.trilong.kpibackend.modules.kpi.repository.KpiScoreRepository;
import com.trilong.kpibackend.modules.payroll.entity.Payroll;
import com.trilong.kpibackend.modules.payroll.repository.PayrollRepository;
import com.trilong.kpibackend.modules.user.entity.User;
import com.trilong.kpibackend.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayrollService {

    private final PayrollRepository payrollRepository;
    private final UserRepository userRepository;
    private final CheckinLogRepository checkinLogRepository;
    private final KpiScoreRepository kpiScoreRepository;

    private static final double PENALTY_PER_LATE = 100000.0; // 100k phạt đi muộn
    private static final double BONUS_PER_KPI_POINT = 50000.0; // 50k thưởng 1 điểm KPI
    private static final LocalTime LATE_THRESHOLD = LocalTime.of(8, 30); // Sau 8:30 sáng tính là đi muộn

    /**
     * Tính toán lương cho một nhân viên cụ thể trong tháng.
     */
    @Transactional
    public Payroll calculatePayrollForUser(Long userId, String month) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhân viên với ID: " + userId));

        // 1. Xác định khoảng thời gian đầu tháng và cuối tháng
        String[] parts = month.split("-");
        int year = Integer.parseInt(parts[0]);
        int monthVal = Integer.parseInt(parts[1]);
        LocalDate startLocalDate = LocalDate.of(year, monthVal, 1);
        LocalDate endLocalDate = startLocalDate.with(TemporalAdjusters.lastDayOfMonth());

        // Chuyển sang ZonedDateTime tại múi giờ hệ thống
        ZonedDateTime start = ZonedDateTime.of(startLocalDate.atStartOfDay(), ZoneId.systemDefault());
        ZonedDateTime end = ZonedDateTime.of(endLocalDate.atTime(23, 59, 59, 999999999), ZoneId.systemDefault());

        // 2. Tính số lần đi muộn từ CheckinLog
        List<CheckinLog> logs = checkinLogRepository.findByUserIdAndCheckinTimeBetween(userId, start, end);
        
        // Chỉ tính các log đã được approved, nhóm theo ngày (sử dụng múi giờ hệ thống)
        Map<LocalDate, List<CheckinLog>> logsByDate = logs.stream()
                .filter(logRecord -> "APPROVED".equals(logRecord.getStatus()))
                .collect(Collectors.groupingBy(logRecord -> logRecord.getCheckinTime()
                        .withZoneSameInstant(ZoneId.systemDefault())
                        .toLocalDate()));

        long lateCount = 0;
        for (Map.Entry<LocalDate, List<CheckinLog>> entry : logsByDate.entrySet()) {
            // Lấy log check-in sớm nhất trong ngày
            CheckinLog earliest = entry.getValue().stream()
                    .min(Comparator.comparing(CheckinLog::getCheckinTime))
                    .orElse(null);

            if (earliest != null) {
                LocalTime checkinTime = earliest.getCheckinTime()
                        .withZoneSameInstant(ZoneId.systemDefault())
                        .toLocalTime();
                if (checkinTime.isAfter(LATE_THRESHOLD)) {
                    lateCount++;
                }
            }
        }

        double latePenalty = lateCount * PENALTY_PER_LATE;

        // 3. Tính thưởng KPI
        KpiScore kpiScore = kpiScoreRepository.findByUserIdAndMonth(userId, month).orElse(null);
        double kpiBonus = 0.0;
        if (kpiScore != null) {
            kpiBonus = kpiScore.getTotal() * BONUS_PER_KPI_POINT;
        }

        // 4. Lấy lương cứng của user
        double basicSalary = user.getBasicSalary() != null ? user.getBasicSalary() : 10000000.0;

        // 5. Tính lương thực nhận
        double netSalary = basicSalary + kpiBonus - latePenalty;
        if (netSalary < 0.0) {
            netSalary = 0.0; // Không thể lương âm
        }

        // 6. Lưu hoặc cập nhật bảng lương
        Payroll payroll = payrollRepository.findByUserIdAndMonth(userId, month)
                .orElseGet(() -> Payroll.builder()
                        .user(user)
                        .month(month)
                        .status("PENDING")
                        .build());

        // Cập nhật các con số tài chính
        payroll.setBasicSalary(basicSalary);
        payroll.setKpiBonus(kpiBonus);
        payroll.setLatePenalty(latePenalty);
        payroll.setNetSalary(netSalary);

        log.info("Calculated payroll for user: {}, month: {}, lateCount: {}, kpiTotal: {}, netSalary: {}", 
                user.getFullName(), month, lateCount, kpiScore != null ? kpiScore.getTotal() : 0, netSalary);

        return payrollRepository.save(payroll);
    }

    /**
     * Tính toán lương cho tất cả các nhân viên ACTIVE trong tháng.
     */
    @Transactional
    public List<Payroll> calculatePayrollForAll(String month) {
        List<User> activeUsers = userRepository.findAll().stream()
                .filter(u -> "ACTIVE".equals(u.getStatus()))
                .collect(Collectors.toList());

        return activeUsers.stream()
                .map(user -> calculatePayrollForUser(user.getId(), month))
                .collect(Collectors.toList());
    }

    /**
     * Phê duyệt bảng lương (Đổi sang APPROVED).
     */
    @Transactional
    public Payroll approvePayroll(Long payrollId) {
        Payroll payroll = payrollRepository.findById(payrollId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bảng lương với ID: " + payrollId));
        payroll.setStatus("APPROVED");
        return payrollRepository.save(payroll);
    }

    /**
     * Xem bảng lương của nhân viên hiện tại.
     */
    public List<Payroll> getMyPayrolls(Long userId) {
        return payrollRepository.findByUserIdOrderByMonthDesc(userId);
    }

    /**
     * Xem bảng lương của toàn bộ nhân viên trong tháng.
     */
    public List<Payroll> getPayrollsByMonth(String month) {
        return payrollRepository.findByMonth(month);
    }
}
