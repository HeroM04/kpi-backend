package com.trilong.kpibackend.modules.kpi.repository;

import com.trilong.kpibackend.modules.kpi.entity.KpiLedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;

@Repository
public interface KpiLedgerEntryRepository extends JpaRepository<KpiLedgerEntry, Long> {

    List<KpiLedgerEntry> findByUserIdAndWeekOrderByOccurredAtDesc(Long userId, String week);

    List<KpiLedgerEntry> findByUserIdAndMonthOrderByOccurredAtDesc(Long userId, String month);

    /** Đếm khoản điểm phát sinh sau lần nhân sự mở màn hình Thông báo gần nhất. */
    long countByUserIdAndCreatedAtAfter(Long userId, ZonedDateTime moc);

    long countByUserId(Long userId);

    /**
     * Kỳ có dữ liệu cũ nhất của một nhân sự — để biết còn gì phía trước mà lật
     * tiếp hay đã chạm đáy lịch sử.
     */
    @Query("SELECT MIN(e.week) FROM KpiLedgerEntry e WHERE e.userId = :userId")
    String tuanCuNhat(@Param("userId") Long userId);

    @Query("SELECT MIN(e.month) FROM KpiLedgerEntry e WHERE e.userId = :userId")
    String thangCuNhat(@Param("userId") Long userId);
}
