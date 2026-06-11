package com.trilong.kpibackend.modules.salepro.repository;

import com.trilong.kpibackend.modules.salepro.entity.ApartmentQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ApartmentQuestionRepository extends JpaRepository<ApartmentQuestion, Long> {
    List<ApartmentQuestion> findByApartmentIdOrderByCreatedAtDesc(Long apartmentId);
    long countByApartmentId(Long apartmentId);
}
