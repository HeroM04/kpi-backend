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
}
