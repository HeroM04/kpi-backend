package com.trilong.kpibackend.modules.dashboard.service;

import com.trilong.kpibackend.modules.attendance.repository.CheckinLogRepository;
import com.trilong.kpibackend.modules.battle.repository.FieldBattleRepository;
import com.trilong.kpibackend.modules.deal.entity.Deal;
import com.trilong.kpibackend.modules.deal.repository.DealRepository;
import com.trilong.kpibackend.modules.feedback.repository.FeedbackRepository;
import com.trilong.kpibackend.modules.post.repository.SocialPostRepository;
import com.trilong.kpibackend.modules.training.repository.TrainingSessionRepository;
import com.trilong.kpibackend.modules.user.repository.DepartmentRepository;
import com.trilong.kpibackend.modules.user.repository.UserRepository;
import com.trilong.kpibackend.modules.kpi.repository.KpiScoreRepository;
import com.trilong.kpibackend.modules.kpi.entity.KpiScore;
import com.trilong.kpibackend.modules.kpi.dto.KpiScoreResponseDTO;
import com.trilong.kpibackend.modules.kpi.service.KpiCalculationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final CheckinLogRepository checkinLogRepository;
    private final FieldBattleRepository fieldBattleRepository;
    private final SocialPostRepository socialPostRepository;
    private final DealRepository dealRepository;
    private final TrainingSessionRepository trainingSessionRepository;
    private final FeedbackRepository feedbackRepository;
    private final KpiScoreRepository kpiScoreRepository;
    private final KpiCalculationService kpiCalculationService;

    /**
     * Lấy thống kê tổng quan cho Web Admin Dashboard.
     * Có thể lọc số liệu Deal theo tháng.
     */
    public Map<String, Object> getOverallStats(String month) {
        Map<String, Object> stats = new HashMap<>();

        long totalUsers = userRepository.count();
        long totalDepartments = departmentRepository.count();
        long totalSessions = trainingSessionRepository.count();
        
        List<Deal> approvedDeals = dealRepository.findByStatusOrderBySubmittedAtDesc("APPROVED");
        
        // Lọc deal theo tháng (nếu có truyền)
        if (month != null && !month.trim().isEmpty()) {
            approvedDeals = approvedDeals.stream().filter(d -> {
                String dealMonth = String.format("%04d-%02d", 
                    d.getSubmittedAt().getYear(), 
                    d.getSubmittedAt().getMonthValue());
                return month.equals(dealMonth);
            }).collect(Collectors.toList());
        }

        long approvedDealsCount = approvedDeals.size();
        double totalRevenue = approvedDeals.stream().mapToDouble(d -> d.getPrice() != null ? d.getPrice() : 0.0).sum();
        double totalCommission = approvedDeals.stream().mapToDouble(d -> d.getCommission() != null ? d.getCommission() : 0.0).sum();

        stats.put("totalUsers", totalUsers);
        stats.put("totalDepartments", totalDepartments);
        stats.put("totalTrainingSessions", totalSessions);
        stats.put("approvedDealsCount", approvedDealsCount);
        stats.put("totalRevenue", totalRevenue);
        stats.put("totalCommission", totalCommission);

        return stats;
    }

    /**
     * Lấy top 5 nhân viên điểm KPI cao nhất trong tháng.
     */
    public List<KpiScoreResponseDTO> getTopPerformers(String month) {
        if (month == null || month.trim().isEmpty()) {
            month = kpiCalculationService.extractMonth(ZonedDateTime.now());
        }
        
        List<KpiScore> topScores = kpiScoreRepository.findTopByMonth(month, PageRequest.of(0, 5));
        return topScores.stream()
                .map(KpiScoreResponseDTO::from)
                .collect(Collectors.toList());
    }

    /**
     * Lấy số lượng chờ duyệt / chưa đọc cho các menu badge thông báo.
     */
    public Map<String, Long> getMenuBadges() {
        Map<String, Long> badges = new HashMap<>();

        long attendancePending = checkinLogRepository.countByStatus("PENDING");
        long battlePending = fieldBattleRepository.countByStatus("PENDING");
        long postPending = socialPostRepository.countByStatus("PENDING");
        long dealPending = dealRepository.countByStatus("PENDING");
        long feedbackUnread = feedbackRepository.countByStatus("UNREAD");

        badges.put("attendancePending", attendancePending);
        badges.put("battlePending", battlePending);
        badges.put("postPending", postPending);
        badges.put("dealPending", dealPending);
        badges.put("feedbackUnread", feedbackUnread);

        return badges;
    }
}

