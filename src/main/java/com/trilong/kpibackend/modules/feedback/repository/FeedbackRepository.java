package com.trilong.kpibackend.modules.feedback.repository;

import com.trilong.kpibackend.modules.feedback.entity.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {
    List<Feedback> findBySenderIdOrderByCreatedAtDesc(Long senderId);
    long countByStatus(String status);
}