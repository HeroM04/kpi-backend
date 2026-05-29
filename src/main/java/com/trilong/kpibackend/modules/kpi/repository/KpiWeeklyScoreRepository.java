package com.trilong.kpibackend.modules.kpi.repository;

import com.trilong.kpibackend.modules.kpi.entity.KpiWeeklyScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KpiWeeklyScoreRepository extends JpaRepository<KpiWeeklyScore, Long> {
    Optional<KpiWeeklyScore> findByUserIdAndWeek(Long userId, String week);
    List<KpiWeeklyScore> findByUserIdAndMonth(Long userId, String month);

    @org.springframework.data.jpa.repository.Query("SELECT k FROM KpiWeeklyScore k WHERE " +
           "k.week = :week AND " +
           "(:departmentId IS NULL OR k.user.department.id = :departmentId)")
    List<KpiWeeklyScore> findByWeekAndDepartment(
        @org.springframework.data.repository.query.Param("week") String week,
        @org.springframework.data.repository.query.Param("departmentId") Long departmentId
    );
}
