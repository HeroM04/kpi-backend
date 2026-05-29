package com.trilong.kpibackend.modules.kpi.entity;

import com.trilong.kpibackend.modules.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "kpi_scores", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "month"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KpiScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 7)
    private String month; // Định dạng 'YYYY-MM'

    @Column(columnDefinition = "int default 0")
    private int attendance;

    @Column(columnDefinition = "int default 0")
    private int meeting;

    @Column(columnDefinition = "int default 0")
    private int post;

    @Column(columnDefinition = "int default 0")
    private int deal;

    @Column(columnDefinition = "int default 0")
    private int total;

    @Column(name = "is_flagged", columnDefinition = "boolean default false")
    private boolean isFlagged;
}
