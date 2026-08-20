package com.trilong.kpibackend.modules.attendance.repository;

import com.trilong.kpibackend.modules.attendance.entity.CheckinLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * Kế thừa thêm {@link JpaSpecificationExecutor} để dựng câu truy vấn lọc động.
 * Cách này chỉ sinh ra đúng những điều kiện thực sự được chọn, thay vì viết
 * {@code :thamSo IS NULL OR ...} — PostgreSQL không suy được kiểu của tham số
 * null nên cách viết đó chạy lỗi, mà nó cũng khiến câu lệnh không dùng được
 * chỉ mục.
 */
@Repository
public interface CheckinLogRepository extends JpaRepository<CheckinLog, Long>,
        JpaSpecificationExecutor<CheckinLog> {

    // Tìm log chấm công theo status (APPROVED/PENDING) và sắp xếp theo thời gian mới nhất
    List<CheckinLog> findByStatusOrderByCheckinTimeDesc(String status);

    // Tìm lịch sử chấm công của 1 user và sắp xếp mới nhất
    List<CheckinLog> findByUserIdOrderByCheckinTimeDesc(Long userId);

    // Tìm log chấm công của 1 user trong khoảng thời gian
    List<CheckinLog> findByUserIdAndCheckinTimeBetween(Long userId, ZonedDateTime start, ZonedDateTime end);

    // Tìm toàn bộ log chấm công trong khoảng thời gian
    List<CheckinLog> findByCheckinTimeBetween(ZonedDateTime start, ZonedDateTime end);

    // Đếm số lượng log chấm công theo trạng thái (chờ duyệt)
    long countByStatus(String status);

    /** Đếm bản ghi trong một khoảng thời gian — dùng cho số liệu so sánh theo tháng. */
    long countByCheckinTimeBetween(ZonedDateTime from, ZonedDateTime to);
}