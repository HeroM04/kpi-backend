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
     * Điểm danh do hệ thống tự ghi, không phải người này thật sự ngồi học buổi đó.
     *
     * <p>Xảy ra với đào tạo kỹ năng: dự một buổi trong nhóm là coi như xong cả
     * nhóm, nên các buổi còn lại cùng nhóm được đánh dấu tự động. Những dòng
     * này KHÔNG cộng thêm điểm — điểm đã tính ở buổi học thật.
     *
     * <p>Tách riêng để danh sách lớp phân biệt được ai có mặt thật, ai được
     * miễn nhờ đã học nhóm kỹ năng đó rồi.
     */
    @Column(name = "auto_marked", columnDefinition = "boolean default false")
    @Builder.Default
    private Boolean autoMarked = false;
}
