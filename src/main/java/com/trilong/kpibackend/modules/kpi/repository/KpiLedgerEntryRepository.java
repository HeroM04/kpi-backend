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

    /**
     * Nhật ký một kỳ, khoản ghi sau đứng trên — đúng thứ tự thông báo đến tay
     * nhân sự.
     *
     * <p>Xếp theo lúc GHI, không theo lúc phát sinh: Admin thu hồi hay thêm chấm
     * công tay mang mốc phát sinh của lượt chấm công cũ, xếp theo mốc ấy thì
     * khoản sửa sai nằm dưới khoản bị sửa, còn cùng phút thì thứ tự tùy CSDL.
     * Ghi cùng một lúc thì id lớn (ghi sau) đứng trên.
     */
    List<KpiLedgerEntry> findByUserIdAndWeekOrderByCreatedAtDescIdDesc(Long userId, String week);

    List<KpiLedgerEntry> findByUserIdAndMonthOrderByCreatedAtDescIdDesc(Long userId, String month);

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

    /**
     * Mọi khoản điểm một nhóm trong một tuần, theo đúng thứ tự đã ghi (id tăng
     * dần) — để chấm lại tuần từ nhật ký, kẹp trần đúng như lúc cộng thật.
     */
    List<KpiLedgerEntry> findByCategoryAndWeekOrderByIdAsc(String category, String week);

    /** Như trên nhưng của riêng một người. */
    List<KpiLedgerEntry> findByUserIdAndCategoryAndWeekOrderByIdAsc(Long userId, String category, String week);

    /** Mọi khoản điểm cùng một diễn giải của một người — để tìm lại khoản đã cộng mà hoàn đúng số. */
    List<KpiLedgerEntry> findByUserIdAndCategoryAndReason(Long userId, String category, String reason);

    /** Như trên nhưng khớp đầu câu — diễn giải có nối thêm nội dung phía sau. */
    List<KpiLedgerEntry> findByUserIdAndCategoryAndReasonStartingWithOrderByIdAsc(Long userId, String category, String dauCau);
}
