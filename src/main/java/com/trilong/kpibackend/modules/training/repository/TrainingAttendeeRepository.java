package com.trilong.kpibackend.modules.training.repository;

import com.trilong.kpibackend.modules.training.entity.TrainingAttendee;
import com.trilong.kpibackend.modules.training.entity.TrainingAttendeeId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TrainingAttendeeRepository extends JpaRepository<TrainingAttendee, TrainingAttendeeId> {
    @org.springframework.data.jpa.repository.Query("SELECT a FROM TrainingAttendee a JOIN FETCH a.user u LEFT JOIN FETCH u.department WHERE a.id.sessionId = :sessionId")
    List<TrainingAttendee> findBySessionId(@org.springframework.data.repository.query.Param("sessionId") Long sessionId);
    List<TrainingAttendee> findByUserId(Long userId);
    boolean existsBySessionIdAndUserId(Long sessionId, Long userId);
    long countBySessionId(Long sessionId);

    /**
     * Nhân sự này đã học THẬT một buổi nào trong nhóm kỹ năng chưa.
     *
     * <p>Chỉ tính buổi có mặt thật ({@code autoMarked = false}); dòng do hệ
     * thống tự đánh dấu không được dùng làm căn cứ miễn cho nhóm khác.
     */
    @org.springframework.data.jpa.repository.Query(
            "SELECT COUNT(a) > 0 FROM TrainingAttendee a JOIN TrainingSession s ON s.id = a.sessionId "
                    + "WHERE a.userId = :userId AND s.skillGroup = :nhom "
                    + "AND (a.source IS NULL OR a.source = 'REAL')")
    boolean daHocNhomKyNang(@org.springframework.data.repository.query.Param("userId") Long userId,
                            @org.springframework.data.repository.query.Param("nhom") String nhom);

    /** Những người đã học thật ít nhất một buổi của nhóm kỹ năng này. */
    @org.springframework.data.jpa.repository.Query(
            "SELECT DISTINCT a.userId FROM TrainingAttendee a JOIN TrainingSession s ON s.id = a.sessionId "
                    + "WHERE s.skillGroup = :nhom AND (a.source IS NULL OR a.source = 'REAL')")
    List<Long> aiDaHocNhom(@org.springframework.data.repository.query.Param("nhom") String nhom);
}
