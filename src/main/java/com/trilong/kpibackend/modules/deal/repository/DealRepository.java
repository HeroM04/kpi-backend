package com.trilong.kpibackend.modules.deal.repository;

import com.trilong.kpibackend.modules.deal.entity.Deal;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DealRepository extends JpaRepository<Deal, Long> {
    @EntityGraph(attributePaths = {"user", "approvedBy"})
    Optional<Deal> findById(Long id);

    @EntityGraph(attributePaths = {"user", "approvedBy"})
    List<Deal> findByUserIdOrderBySubmittedAtDesc(Long userId);

    @EntityGraph(attributePaths = {"user", "approvedBy"})
    List<Deal> findByStatusOrderBySubmittedAtDesc(String status);

    @EntityGraph(attributePaths = {"user", "approvedBy"})
    List<Deal> findAllByOrderBySubmittedAtDesc();

    long countByStatus(String status);
}

