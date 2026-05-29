package com.trilong.kpibackend.modules.attendance.repository;

import com.trilong.kpibackend.modules.attendance.entity.CheckinLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;

@Repository
public interface CheckinLogRepository extends JpaRepository<CheckinLog, Long> {

    // Tìm log chấm công theo status (APPROVED/PENDING) và sắp xếp theo thời gian mới nhất
    List<CheckinLog> findByStatusOrderByCheckinTimeDesc(String status);

    // Tìm lịch sử chấm công của 1 user và sắp xếp mới nhất
    List<CheckinLog> findByUserIdOrderByCheckinTimeDesc(Long userId);

    // Tìm log chấm công của 1 user trong khoảng thời gian
    List<CheckinLog> findByUserIdAndCheckinTimeBetween(Long userId, ZonedDateTime start, ZonedDateTime end);

    // Đếm số lượng log chấm công theo trạng thái (chờ duyệt)
    long countByStatus(String status);
}