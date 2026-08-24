package com.trilong.kpibackend.modules.training.entity;

import com.trilong.kpibackend.modules.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;

@Entity
@Table(name = "training_attendees")
@IdClass(TrainingAttendeeId.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainingAttendee {

    @Id
    @Column(name = "session_id")
    private Long sessionId;

    @Id
    @Column(name = "user_id")
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", insertable = false, updatable = false)
    private TrainingSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @CreationTimestamp
    @Column(name = "attended_at", updatable = false)
    private ZonedDateTime attendedAt;

    /**
     * Dòng điểm danh này từ đâu ra:
     * <ul>
     *   <li>{@code REAL} — người này thật sự có mặt (quét mã hoặc Admin điểm
     *       danh tay). Được cộng điểm học tập.</li>
     *   <li>{@code SKILL_GROUP} — hệ thống tự đánh dấu vì đã học một buổi khác
     *       cùng nhóm kỹ năng. KHÔNG cộng điểm, vì điểm đã tính ở buổi học thật;
     *       cộng nữa là một buổi học ăn điểm nhiều lần.</li>
     *   <li>{@code EXEMPT} — Admin duyệt lý do xin không tham gia buổi đào tạo
     *       dự án. Vẫn tính là có điểm danh và vẫn được điểm, vì người này không
     *       thuộc diện phải học chứ không phải trốn học.</li>
     * </ul>
     */
    @Column(name = "source", length = 20)
    @Builder.Default
    private String source = "REAL";

    /** Có phải dòng do hệ thống tự ghi thay vì người ta ngồi học thật không. */
    public boolean laTuDong() {
        return source != null && !"REAL".equals(source);
    }

    /** Dòng này có được tính vào điểm học tập của tuần không. */
    public boolean tinhDiem() {
        return !"SKILL_GROUP".equals(source);
    }
}
