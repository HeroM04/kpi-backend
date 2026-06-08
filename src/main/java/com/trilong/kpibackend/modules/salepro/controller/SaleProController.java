package com.trilong.kpibackend.modules.salepro.controller;

import com.trilong.kpibackend.modules.salepro.entity.Apartment;
import com.trilong.kpibackend.modules.salepro.entity.Building;
import com.trilong.kpibackend.modules.salepro.entity.Project;
import com.trilong.kpibackend.modules.salepro.service.SaleProService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/salepro")
@Tag(name = "SalePro API", description = "Quản lý Cổng thông tin Công nghệ Bất động sản")
public class SaleProController {

    @Autowired
    private SaleProService saleProService;

    @Operation(summary = "Lấy danh sách tất cả dự án")
    @GetMapping("/projects")
    public ResponseEntity<?> getAllProjects() {
        List<Project> projects = saleProService.getAllProjects();
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "data", projects
        ));
    }

    @Operation(summary = "Lấy chi tiết một dự án")
    @GetMapping("/projects/{id}")
    public ResponseEntity<?> getProjectById(@PathVariable Long id) {
        Project project = saleProService.getProjectById(id);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "data", project
        ));
    }

    @Operation(summary = "Lấy danh sách phân khu / tòa nhà của dự án")
    @GetMapping("/projects/{projectId}/buildings")
    public ResponseEntity<?> getBuildingsByProjectId(@PathVariable Long projectId) {
        List<Building> buildings = saleProService.getBuildingsByProjectId(projectId);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "data", buildings
        ));
    }

    @Operation(summary = "Lấy danh sách căn hộ của tòa nhà")
    @GetMapping("/buildings/{buildingId}/apartments")
    public ResponseEntity<?> getApartmentsByBuildingId(@PathVariable Long buildingId) {
        List<Apartment> apartments = saleProService.getApartmentsByBuildingId(buildingId);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "data", apartments
        ));
    }

    @Operation(summary = "Cập nhật trạng thái căn hộ (Chỉ Admin)")
    @PutMapping("/apartments/{apartmentId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateApartmentStatus(
            @PathVariable Long apartmentId,
            @RequestParam String status) {
        try {
            Apartment apartment = saleProService.updateApartmentStatus(apartmentId, status);
            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "message", "Cập nhật trạng thái căn hộ thành công!",
                    "data", apartment
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }
}
