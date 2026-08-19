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
     * Lấy danh sách buổi đào tạo "còn hiển thị" cho Mobile:
     * - Chưa kết thúc (status != COMPLETED và != CANCELLED)
     * - startTime nằm trong cửa sổ [dayStart, dayEnd): từ đầu ngày hôm nay
     *   đến trước mốc dayEnd (ví dụ 1 tuần tới).
     * Buổi đã qua ngày (startTime < dayStart) hoặc quá xa (>= dayEnd) sẽ bị ẩn.
     */
    @Query("SELECT s FROM TrainingSession s WHERE s.status NOT IN ('COMPLETED', 'CANCELLED') AND s.startTime >= :dayStart AND s.startTime < :dayEnd ORDER BY s.startTime ASC")
    List<TrainingSession> findActiveSessionsInWindow(@Param("dayStart") ZonedDateTime dayStart, @Param("dayEnd") ZonedDateTime dayEnd);

    /**
     * Đếm số buổi đào tạo công ty thực sự tổ chức trong khoảng thời gian
     * (bỏ qua buổi đã hủy) — dùng để biết một tuần có lịch đào tạo hay không.
     */
    @Query("SELECT COUNT(s) FROM TrainingSession s WHERE s.status <> 'CANCELLED' AND s.startTime >= :from AND s.startTime < :to")
    long countHeldSessionsBetween(@Param("from") ZonedDateTime from, @Param("to") ZonedDateTime to);
}
