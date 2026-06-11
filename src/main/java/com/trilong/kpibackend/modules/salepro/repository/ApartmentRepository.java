package com.trilong.kpibackend.modules.salepro.repository;

import com.trilong.kpibackend.modules.salepro.entity.Apartment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ApartmentRepository extends JpaRepository<Apartment, Long> {
    List<Apartment> findByBuildingId(Long buildingId);

    long countByBuildingId(Long buildingId);

    long countByBuildingIdAndStatus(Long buildingId, String status);

    // Phân trang + lọc + tìm kiếm quỹ căn theo dự án (tham số null = bỏ qua điều kiện)
    @Query(value = "SELECT a FROM Apartment a WHERE a.building.project.id = :projectId "
            + "AND (:status IS NULL OR a.status = :status) "
            + "AND (:buildingId IS NULL OR a.building.id = :buildingId) "
            + "AND (:type IS NULL OR a.apartmentType = :type) "
            + "AND (:direction IS NULL OR a.direction = :direction) "
            + "AND (:q IS NULL OR LOWER(a.apartmentCode) LIKE LOWER(CONCAT('%', :q, '%')))",
            countQuery = "SELECT COUNT(a) FROM Apartment a WHERE a.building.project.id = :projectId "
            + "AND (:status IS NULL OR a.status = :status) "
            + "AND (:buildingId IS NULL OR a.building.id = :buildingId) "
            + "AND (:type IS NULL OR a.apartmentType = :type) "
            + "AND (:direction IS NULL OR a.direction = :direction) "
            + "AND (:q IS NULL OR LOWER(a.apartmentCode) LIKE LOWER(CONCAT('%', :q, '%')))")
    Page<Apartment> searchByProject(
            @Param("projectId") Long projectId,
            @Param("status") String status,
            @Param("buildingId") Long buildingId,
            @Param("type") String type,
            @Param("direction") String direction,
            @Param("q") String q,
            Pageable pageable);

    // Lấy toàn bộ căn của 1 dự án trong 1 truy vấn, JOIN FETCH sẵn building
    // để map DTO không bị lazy/N+1 (open-in-view=false).
    @Query("SELECT a FROM Apartment a JOIN FETCH a.building b "
            + "WHERE b.project.id = :projectId ORDER BY b.id ASC, a.apartmentCode ASC")
    List<Apartment> findAllByProjectId(@Param("projectId") Long projectId);
}
