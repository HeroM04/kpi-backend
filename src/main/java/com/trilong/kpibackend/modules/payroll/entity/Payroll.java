package com.trilong.kpibackend.modules.payroll.entity;

import com.trilong.kpibackend.modules.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.ZonedDateTime;

@Entity
@Table(name = "payrolls", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "month"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payroll {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 7)
    private String month; // Định dạng 'YYYY-MM'

    @Column(name = "basic_salary", nullable = false)
    private Double basicSalary;

    @Column(name = "kpi_bonus", nullable = false)
    private Double kpiBonus;

    @Column(name = "late_penalty", nullable = false)
    private Double latePenalty;

    @Column(name = "net_salary", nullable = false)
    private Double netSalary;

    @Column(length = 20, nullable = false)
    @Builder.Default
    private String status = "PENDING"; // PENDING | APPROVED

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (this.status == null) this.status = "PENDING";
        if (this.basicSalary == null) this.basicSalary = 0.0;
        if (this.kpiBonus == null) this.kpiBonus = 0.0;
        if (this.latePenalty == null) this.latePenalty = 0.0;
        if (this.netSalary == null) this.netSalary = 0.0;
    }
}
