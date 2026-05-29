package com.trilong.kpibackend.modules.payroll.repository;

import com.trilong.kpibackend.modules.payroll.entity.Payroll;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PayrollRepository extends JpaRepository<Payroll, Long> {

    Optional<Payroll> findByUserIdAndMonth(Long userId, String month);

    List<Payroll> findByMonth(String month);

    List<Payroll> findByUserIdOrderByMonthDesc(Long userId);
}
