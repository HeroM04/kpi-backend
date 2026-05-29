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
}
