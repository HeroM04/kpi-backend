package com.trilong.kpibackend.modules.kpi.repository;

import com.trilong.kpibackend.modules.kpi.entity.KpiScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KpiScoreRepository extends JpaRepository<KpiScore, Long> {
    Optional<KpiScore> findByUserIdAndMonth(Long userId, String month);
    List<KpiScore> findByMonth(String month);
    List<KpiScore> findByUserId(Long userId);
    List<KpiScore> findByUserIdOrderByMonthDesc(Long userId);

    // Lọc KPI theo phòng ban và tháng
    @Query("SELECT k FROM KpiScore k WHERE " +
           "k.month = :month AND " +
           "(:departmentId IS NULL OR k.user.department.id = :departmentId) " +
           "ORDER BY k.total DESC")
    List<KpiScore> findByMonthAndDepartment(
        @Param("month") String month,
        @Param("departmentId") Long departmentId
    );

    // Top performers tháng — lấy N người điểm cao nhất
    @Query("SELECT k FROM KpiScore k WHERE k.month = :month ORDER BY k.total DESC")
    List<KpiScore> findTopByMonth(@Param("month") String month, org.springframework.data.domain.Pageable pageable);
}

