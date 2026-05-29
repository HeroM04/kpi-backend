package com.trilong.kpibackend.modules.training.repository;

import com.trilong.kpibackend.modules.training.entity.TrainingSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TrainingSessionRepository extends JpaRepository<TrainingSession, Long> {
    Optional<TrainingSession> findByRoomCode(String roomCode);
    List<TrainingSession> findByStatusOrderByStartTimeDesc(String status);
    List<TrainingSession> findAllByOrderByStartTimeDesc();

    /**
     * Tìm các buổi UPCOMING có startTime trước thời điểm dayStart (đầu ngày hôm nay)
     * — dùng cho scheduler tự động kết thúc các buổi quá hạn.
     */
    @Query("SELECT s FROM TrainingSession s WHERE s.status = 'UPCOMING' AND s.startTime < :dayStart")
    List<TrainingSession> findExpiredUpcomingSessions(@Param("dayStart") ZonedDateTime dayStart);

    /**
     * Lấy danh sách buổi đào tạo "còn hiển thị":
     * - Chưa kết thúc (status != COMPLETED và != CANCELLED)
     * - HOẶC startTime >= đầu ngày hôm nay (buổi học hôm nay và tương lai)
     * Mobile app dùng endpoint này để chỉ hiện phòng đang/sắp diễn ra.
     */
    @Query("SELECT s FROM TrainingSession s WHERE s.status NOT IN ('COMPLETED', 'CANCELLED') AND s.startTime >= :dayStart ORDER BY s.startTime ASC")
    List<TrainingSession> findActiveSessionsFromToday(@Param("dayStart") ZonedDateTime dayStart);
}
