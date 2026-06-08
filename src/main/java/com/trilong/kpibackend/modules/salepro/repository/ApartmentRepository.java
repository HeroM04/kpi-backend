package com.trilong.kpibackend.modules.salepro.repository;

import com.trilong.kpibackend.modules.salepro.entity.Apartment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ApartmentRepository extends JpaRepository<Apartment, Long> {
    List<Apartment> findByBuildingId(Long buildingId);
}
