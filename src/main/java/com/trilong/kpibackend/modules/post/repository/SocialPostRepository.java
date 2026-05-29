package com.trilong.kpibackend.modules.post.repository;

import com.trilong.kpibackend.modules.post.entity.SocialPost;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SocialPostRepository extends JpaRepository<SocialPost, Long> {
    @EntityGraph(attributePaths = {"user", "approvedBy"})
    Optional<SocialPost> findById(Long id);

    @EntityGraph(attributePaths = {"user", "approvedBy"})
    List<SocialPost> findByUserIdOrderBySubmittedAtDesc(Long userId);

    @EntityGraph(attributePaths = {"user", "approvedBy"})
    List<SocialPost> findByStatusOrderBySubmittedAtDesc(String status);

    @EntityGraph(attributePaths = {"user", "approvedBy"})
    List<SocialPost> findAllByOrderBySubmittedAtDesc();

    long countByStatus(String status);
}

