package com.trilong.kpibackend.modules.kpi.repository;

import com.trilong.kpibackend.modules.kpi.entity.KpiAutoGrant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KpiAutoGrantRepository extends JpaRepository<KpiAutoGrant, Long> {

    boolean existsByUserIdAndPeriodAndGrantType(Long userId, String period, String grantType);

    java.util.Optional<KpiAutoGrant> findByUserIdAndPeriodAndGrantType(
            Long userId, String period, String grantType);

    List<KpiAutoGrant> findByPeriodAndGrantType(String period, String grantType);

    List<KpiAutoGrant> findByUserIdOrderByGrantedAtDesc(Long userId);
}
