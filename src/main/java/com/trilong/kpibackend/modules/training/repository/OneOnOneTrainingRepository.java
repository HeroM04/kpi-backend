package com.trilong.kpibackend.modules.training.repository;

import com.trilong.kpibackend.modules.training.entity.OneOnOneTraining;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OneOnOneTrainingRepository extends JpaRepository<OneOnOneTraining, Long> {
    
    @Query("SELECT o FROM OneOnOneTraining o JOIN FETCH o.user ORDER BY o.submittedAt DESC")
    List<OneOnOneTraining> findAllWithUser();

    /**
     * Báo cáo được máy chủ TỰ DUYỆT theo cách cũ: trạng thái APPROVED mà không
     * có người duyệt. Báo cáo Admin duyệt tay luôn có reviewed_by nên không lẫn vào.
     */
    List<OneOnOneTraining> findByStatusAndReviewedByIsNullOrderBySubmittedAtAsc(String status);
}
