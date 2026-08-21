package com.trilong.kpibackend.modules.kpi.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

/**
 * KpiLedgerEntry — Nhật ký điểm KPI: mỗi lần cộng hay trừ là một dòng.
 *
 * <p>Trước đây hệ thống chỉ giữ TỔNG điểm ({@link KpiScore} theo tháng,
 * {@link KpiWeeklyScore} theo tuần). Nhân sự nhìn thấy con số cuối cùng nhưng
 * không biết nó được ghép từ những khoản nào, mất điểm ở đâu. Bảng này lưu lại
 * từng biến động kèm một câu diễn giải ngắn để màn hình Thông báo trên ứng dụng
 * dựng lại được toàn bộ câu chuyện theo tuần và theo tháng.
 *
 * <p>Chỉ ghi, không sửa. Khi Admin thu hồi một khoản đã duyệt thì một dòng điểm
 * âm được thêm vào chứ dòng cũ vẫn nguyên — người xem thấy được cả hai vế.
 */
@Entity
@Table(name = "kpi_ledger_entries",
        indexes = {
                @Index(name = "idx_ledger_user_time", columnList = "user_id, created_at DESC"),
                @Index(name = "idx_ledger_user_week", columnList = "user_id, week"),
                @Index(name = "idx_ledger_user_month", columnList = "user_id, month")
        })
@Data
@NoArgsConstructor
public class KpiLedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Nhóm điểm: attendance / meeting / post / deal. */
    @Column(nullable = false, length = 20)
    private String category;

    /**
     * Số điểm theo quy định, có thể âm.
     *
     * <p>Đây là con số "đáng lẽ được cộng". Nó có thể khác số điểm thực nhận nếu
     * nhóm đã chạm trần tuần — xem {@link #effectivePoints}.
     */
    @Column(nullable = false)
    private Integer points;

    /**
     * Số điểm thực sự vào bảng tuần sau khi áp trần nhóm và chặn số âm.
     *
     * <p>Ví dụ nhóm Lan tỏa tối đa 30đ/tuần, đang có 28đ mà đăng thêm một bài
     * được 5đ thì {@code points = 5} nhưng {@code effectivePoints = 2}. Nhờ vậy
     * nhân sự hiểu vì sao làm thêm mà điểm không tăng thay vì nghĩ hệ thống sai.
     */
    @Column(name = "effective_points", nullable = false)
    private Integer effectivePoints;

    /** Diễn giải ngắn hiển thị cho nhân sự, ví dụ "Chấm công vào lúc 08:12". */
    @Column(nullable = false, length = 300)
    private String reason;

    /** Tuần KPI được ghi nhận, dạng ISO "yyyy-Www". */
    @Column(nullable = false, length = 10)
    private String week;

    /** Tháng KPI được ghi nhận, dạng "yyyy-MM" (theo quy tắc tuần trọn vẹn). */
    @Column(nullable = false, length = 7)
    private String month;

    /**
     * Thời điểm của HÀNH ĐỘNG sinh ra điểm — cũng là mốc dùng để xếp vào tuần
     * và tháng nào. Khác {@link #createdAt} khi Admin duyệt muộn một bản ghi cũ.
     */
    @Column(name = "occurred_at", nullable = false)
    private ZonedDateTime occurredAt;

    /** Thời điểm dòng nhật ký được ghi — dùng để sắp xếp và đếm thông báo mới. */
    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) this.createdAt = ZonedDateTime.now();
    }
}
