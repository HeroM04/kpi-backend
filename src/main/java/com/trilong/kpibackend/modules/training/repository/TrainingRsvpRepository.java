package com.trilong.kpibackend.modules.training.repository;

import com.trilong.kpibackend.modules.training.entity.TrainingRsvp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TrainingRsvpRepository extends JpaRepository<TrainingRsvp, Long> {

    Optional<TrainingRsvp> findBySessionIdAndUserId(Long sessionId, Long userId);

    List<TrainingRsvp> findBySessionId(Long sessionId);

    List<TrainingRsvp> findByUserId(Long userId);

    /** Đơn xin không tham gia đang chờ Admin duyệt, mới nhất trước. */
    @Query("SELECT r FROM TrainingRsvp r WHERE r.choice = 'DECLINE' AND r.status = 'PENDING' "
            + "ORDER BY r.createdAt DESC")
    List<TrainingRsvp> donChoDuyet();

    long countByChoiceAndStatus(String choice, String status);
}
