package com.trilong.kpibackend.modules.attendance.repository;

import com.trilong.kpibackend.modules.attendance.entity.LeaveRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    List<LeaveRequest> findByUserIdOrderByLeaveDateDesc(Long userId);

    List<LeaveRequest> findByStatusOrderByLeaveDateAsc(String status);

    Optional<LeaveRequest> findByUserIdAndLeaveDate(Long userId, LocalDate leaveDate);

    List<LeaveRequest> findByLeaveDate(LocalDate leaveDate);

    List<LeaveRequest> findByLeaveDateBetweenOrderByLeaveDateDesc(LocalDate from, LocalDate to);

    List<LeaveRequest> findByUserIdAndLeaveDateBetween(Long userId, LocalDate from, LocalDate to);
}
