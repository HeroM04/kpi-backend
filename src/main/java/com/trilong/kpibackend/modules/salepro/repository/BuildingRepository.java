package com.trilong.kpibackend.modules.salepro.repository;

import com.trilong.kpibackend.modules.salepro.entity.Building;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BuildingRepository extends JpaRepository<Building, Long> {
    List<Building> findByProjectId(Long projectId);
}
