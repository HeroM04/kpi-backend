package com.trilong.kpibackend.modules.salepro.controller;

import com.trilong.kpibackend.modules.salepro.dto.ApartmentDTO;
import com.trilong.kpibackend.modules.salepro.dto.ApartmentQuestionDTO;
import com.trilong.kpibackend.modules.salepro.dto.BuildingDTO;
import com.trilong.kpibackend.modules.salepro.dto.BuildingFloorPlanDTO;
import com.trilong.kpibackend.modules.salepro.dto.ProjectDTO;
import com.trilong.kpibackend.modules.salepro.dto.ProjectDocumentDTO;
import com.trilong.kpibackend.modules.salepro.dto.ProjectProgressDTO;
import com.trilong.kpibackend.modules.salepro.service.SaleProService;
import com.trilong.kpibackend.core.utils.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
        List<ProjectDTO> projects = saleProService.getAllProjects();
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "data", projects
        ));
    }

    @Operation(summary = "Lấy chi tiết một dự án")
    @GetMapping("/projects/{id}")
    public ResponseEntity<?> getProjectById(@PathVariable Long id) {
        ProjectDTO project = saleProService.getProjectById(id);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "data", project
        ));
    }

    @Operation(summary = "Lấy danh sách phân khu / tòa nhà của dự án")
    @GetMapping("/projects/{projectId}/buildings")
    public ResponseEntity<?> getBuildingsByProjectId(@PathVariable Long projectId) {
        List<BuildingDTO> buildings = saleProService.getBuildingsByProjectId(projectId);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "data", buildings
        ));
    }

    @Operation(summary = "Lấy chi tiết một tòa nhà (kèm mặt bằng tầng)")
    @GetMapping("/buildings/{buildingId}")
    public ResponseEntity<?> getBuildingById(@PathVariable Long buildingId) {
        BuildingDTO building = saleProService.getBuildingById(buildingId);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "data", building
        ));
    }

    @Operation(summary = "Lấy mặt bằng tầng (layout) của tòa nhà")
    @GetMapping("/buildings/{buildingId}/floor-plans")
    public ResponseEntity<?> getFloorPlansByBuildingId(@PathVariable Long buildingId) {
        List<BuildingFloorPlanDTO> floorPlans = saleProService.getFloorPlansByBuildingId(buildingId);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "data", floorPlans
        ));
    }

    @Operation(summary = "Lấy danh sách căn hộ của tòa nhà")
    @GetMapping("/buildings/{buildingId}/apartments")
    public ResponseEntity<?> getApartmentsByBuildingId(@PathVariable Long buildingId) {
        List<ApartmentDTO> apartments = saleProService.getApartmentsByBuildingId(buildingId);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "data", apartments
        ));
    }

    @Operation(summary = "Lấy toàn bộ căn hộ (quỹ căn) của một dự án")
    @GetMapping("/projects/{projectId}/apartments")
    public ResponseEntity<?> getApartmentsByProjectId(@PathVariable Long projectId) {
        List<ApartmentDTO> apartments = saleProService.getApartmentsByProjectId(projectId);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "data", apartments
        ));
    }

    @Operation(summary = "Tìm kiếm + lọc + phân trang quỹ căn (bảng hàng) của dự án")
    @GetMapping("/projects/{projectId}/apartments/search")
    public ResponseEntity<?> searchApartments(
            @PathVariable Long projectId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long buildingId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "apartmentCode") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        Sort sort = "desc".equalsIgnoreCase(sortDir)
                ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Page<ApartmentDTO> result = saleProService.searchApartments(
                projectId, status, buildingId, type, direction, q, PageRequest.of(page, size, sort));
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "data", PageResponse.fromPage(result)
        ));
    }

    @Operation(summary = "Lấy danh sách Hỏi đáp của căn hộ")
    @GetMapping("/apartments/{apartmentId}/questions")
    public ResponseEntity<?> getApartmentQuestions(@PathVariable Long apartmentId) {
        List<ApartmentQuestionDTO> questions = saleProService.getQuestionsByApartmentId(apartmentId);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "data", questions
        ));
    }

    @Operation(summary = "Gửi câu hỏi (Hỏi đáp) cho căn hộ")
    @PostMapping("/apartments/{apartmentId}/questions")
    public ResponseEntity<?> createApartmentQuestion(
            @PathVariable Long apartmentId,
            @RequestBody ApartmentQuestionDTO body) {
        try {
            ApartmentQuestionDTO created = saleProService.createQuestion(apartmentId, body);
            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "message", "Đã gửi câu hỏi thành công!",
                    "data", created
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    @Operation(summary = "Lấy tiến độ của dự án")
    @GetMapping("/projects/{projectId}/progress")
    public ResponseEntity<?> getProjectProgress(@PathVariable Long projectId) {
        List<ProjectProgressDTO> progress = saleProService.getProgressByProjectId(projectId);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "data", progress
        ));
    }

    @Operation(summary = "Lấy tài liệu của dự án (link Google Drive)")
    @GetMapping("/projects/{projectId}/documents")
    public ResponseEntity<?> getProjectDocuments(@PathVariable Long projectId) {
        List<ProjectDocumentDTO> documents = saleProService.getDocumentsByProjectId(projectId);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "data", documents
        ));
    }

    @Operation(summary = "Cập nhật trạng thái căn hộ (Chỉ Admin)")
    @PutMapping("/apartments/{apartmentId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateApartmentStatus(
            @PathVariable Long apartmentId,
            @RequestParam String status) {
        try {
            ApartmentDTO apartment = saleProService.updateApartmentStatus(apartmentId, status);
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
