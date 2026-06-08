package com.trilong.kpibackend.modules.salepro.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "buildings", schema = "salepro")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Building {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "building_name", nullable = false, length = 100)
    private String buildingName;

    @Column(name = "subdivision_name", length = 100)
    private String subdivisionName;

    @Column(name = "total_floors")
    private Integer totalFloors;
}
