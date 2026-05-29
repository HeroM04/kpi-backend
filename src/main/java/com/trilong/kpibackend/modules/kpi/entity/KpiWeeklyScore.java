package com.trilong.kpibackend.modules.kpi.entity;

import com.trilong.kpibackend.modules.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "kpi_weekly_scores", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "week"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KpiWeeklyScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 7)
    private String month; // 'YYYY-MM'

    @Column(nullable = false, length = 10)
    private String week; // 'YYYY-Www'

    @Column(columnDefinition = "int default 0")
    private int attendance;

    @Column(columnDefinition = "int default 0")
    private int meeting;

    @Column(columnDefinition = "int default 0")
    private int post;

    @Column(columnDefinition = "int default 0")
    private int total;
}
