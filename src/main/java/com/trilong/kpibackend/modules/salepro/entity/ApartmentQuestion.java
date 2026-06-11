package com.trilong.kpibackend.modules.salepro.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;

/**
 * Hỏi đáp về một căn hộ — nút "Hỏi đáp" trong modal chi tiết căn. Số câu hỏi đổ vào ApartmentDTO.questionCount.
 */
@Entity
@Table(name = "apartment_questions", schema = "salepro")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApartmentQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "apartment_id", nullable = false)
    private Apartment apartment;

    @Column(name = "full_name", length = 150)
    private String fullName;

    @Column(length = 30)
    private String phone;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "TEXT")
    private String answer;

    @Column(name = "answered_by", length = 150)
    private String answeredBy;

    @Column(length = 30)
    private String status; // PENDING, ANSWERED

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;
}
