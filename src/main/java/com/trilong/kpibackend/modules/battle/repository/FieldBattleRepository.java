package com.trilong.kpibackend.modules.battle.repository;

import com.trilong.kpibackend.modules.battle.entity.FieldBattle;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FieldBattleRepository extends JpaRepository<FieldBattle, Long> {
    @EntityGraph(attributePaths = {"user", "approvedBy"})
    Optional<FieldBattle> findById(Long id);

    @EntityGraph(attributePaths = {"user", "approvedBy"})
    List<FieldBattle> findByUserIdOrderBySubmittedAtDesc(Long userId);

    @EntityGraph(attributePaths = {"user", "approvedBy"})
    List<FieldBattle> findByStatusOrderBySubmittedAtDesc(String status);

    @EntityGraph(attributePaths = {"user", "approvedBy"})
    List<FieldBattle> findAllByOrderBySubmittedAtDesc();

    long countByStatus(String status);

    /** Báo cáo một người gửi từ mốc này trở đi — để nhận ra lượt bấm Gửi lặp. */
    List<FieldBattle> findByUserIdAndSubmittedAtAfter(Long userId, java.time.ZonedDateTime moc);
}

