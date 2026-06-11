package com.trilong.kpibackend.modules.salepro.repository;

import com.trilong.kpibackend.modules.salepro.entity.BuildingFloorPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BuildingFloorPlanRepository extends JpaRepository<BuildingFloorPlan, Long> {
    List<BuildingFloorPlan> findByBuildingIdOrderBySortOrderAsc(Long buildingId);
}
