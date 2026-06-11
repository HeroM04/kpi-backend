package com.trilong.kpibackend.modules.salepro.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Mặt bằng tầng của một tòa nhà — phục vụ sub-tab "Layout tòa nhà" (FLOOR PLANS theo từng level).
 */
@Entity
@Table(name = "building_floor_plans", schema = "salepro")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BuildingFloorPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "building_id", nullable = false)
    private Building building;

    @Column(name = "floor_label", length = 50)
    private String floorLabel; // vd "LEVEL 9", "Tầng 9"

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "sort_order")
    private Integer sortOrder;
}
