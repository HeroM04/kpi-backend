package com.trilong.kpibackend.modules.salepro.controller;

import com.trilong.kpibackend.modules.salepro.dto.ApartmentDTO;
import com.trilong.kpibackend.modules.salepro.dto.BuildingDTO;
import com.trilong.kpibackend.modules.salepro.dto.BuildingFloorPlanDTO;
import com.trilong.kpibackend.modules.salepro.dto.ProjectDTO;
import com.trilong.kpibackend.modules.salepro.dto.ProjectDocumentDTO;
import com.trilong.kpibackend.modules.salepro.dto.ProjectProgressDTO;
import com.trilong.kpibackend.modules.salepro.dto.SalesAgentDTO;
import com.trilong.kpibackend.modules.salepro.service.SaleProService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Admin CRUD cho toàn bộ dữ liệu SalePro (chỉ ADMIN). Admin setup ở đây -> hiển thị trên web sale/khách hàng.
 * SecurityConfig đã chặn /api/v1/salepro/admin/** cho ADMIN; thêm @PreAuthorize để chắc chắn.
 */
@RestController
@RequestMapping("/api/v1/salepro/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "SalePro Admin API", description = "Quản trị dữ liệu SalePro (Dự án/Tòa/Căn/Chuyên viên/Tiến độ/Tài liệu)")
public class SaleProAdminController {

    @Autowired
    private SaleProService saleProService;

    private ResponseEntity<?> ok(Object data) {
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", data));
    }

    private ResponseEntity<?> okMsg(String msg) {
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", msg));
    }

    private ResponseEntity<?> err(Exception e) {
        return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
    }

    // ===== Chuyên viên =====
    @Operation(summary = "Danh sách chuyên viên")
    @GetMapping("/agents")
    public ResponseEntity<?> listAgents() {
        return ok(saleProService.getAllAgents());
    }

    @PostMapping("/agents")
    public ResponseEntity<?> createAgent(@RequestBody SalesAgentDTO dto) {
        try { return ok(saleProService.createAgent(dto)); } catch (Exception e) { return err(e); }
    }

    @PutMapping("/agents/{id}")
    public ResponseEntity<?> updateAgent(@PathVariable Long id, @RequestBody SalesAgentDTO dto) {
        try { return ok(saleProService.updateAgent(id, dto)); } catch (Exception e) { return err(e); }
    }

    @DeleteMapping("/agents/{id}")
    public ResponseEntity<?> deleteAgent(@PathVariable Long id) {
        try { saleProService.deleteAgent(id); return okMsg("Đã xóa chuyên viên."); } catch (Exception e) { return err(e); }
    }

    // ===== Dự án =====
    @PostMapping("/projects")
    public ResponseEntity<?> createProject(@RequestBody ProjectDTO dto) {
        try { return ok(saleProService.createProject(dto)); } catch (Exception e) { return err(e); }
    }

    @PutMapping("/projects/{id}")
    public ResponseEntity<?> updateProject(@PathVariable Long id, @RequestBody ProjectDTO dto) {
        try { return ok(saleProService.updateProject(id, dto)); } catch (Exception e) { return err(e); }
    }

    @DeleteMapping("/projects/{id}")
    public ResponseEntity<?> deleteProject(@PathVariable Long id) {
        try { saleProService.deleteProject(id); return okMsg("Đã xóa dự án."); } catch (Exception e) { return err(e); }
    }

    // ===== Tòa nhà =====
    @PostMapping("/buildings")
    public ResponseEntity<?> createBuilding(@RequestBody BuildingDTO dto) {
        try { return ok(saleProService.createBuilding(dto)); } catch (Exception e) { return err(e); }
    }

    @PutMapping("/buildings/{id}")
    public ResponseEntity<?> updateBuilding(@PathVariable Long id, @RequestBody BuildingDTO dto) {
        try { return ok(saleProService.updateBuilding(id, dto)); } catch (Exception e) { return err(e); }
    }

    @DeleteMapping("/buildings/{id}")
    public ResponseEntity<?> deleteBuilding(@PathVariable Long id) {
        try { saleProService.deleteBuilding(id); return okMsg("Đã xóa tòa nhà."); } catch (Exception e) { return err(e); }
    }

    // ===== Căn hộ =====
    @PostMapping("/apartments")
    public ResponseEntity<?> createApartment(@RequestBody ApartmentDTO dto) {
        try { return ok(saleProService.createApartment(dto)); } catch (Exception e) { return err(e); }
    }

    @PutMapping("/apartments/{id}")
    public ResponseEntity<?> updateApartment(@PathVariable Long id, @RequestBody ApartmentDTO dto) {
        try { return ok(saleProService.updateApartment(id, dto)); } catch (Exception e) { return err(e); }
    }

    @DeleteMapping("/apartments/{id}")
    public ResponseEntity<?> deleteApartment(@PathVariable Long id) {
        try { saleProService.deleteApartment(id); return okMsg("Đã xóa căn hộ."); } catch (Exception e) { return err(e); }
    }

    // ===== Mặt bằng tầng =====
    @PostMapping("/floor-plans")
    public ResponseEntity<?> createFloorPlan(@RequestBody BuildingFloorPlanDTO dto) {
        try { return ok(saleProService.createFloorPlan(dto)); } catch (Exception e) { return err(e); }
    }

    @PutMapping("/floor-plans/{id}")
    public ResponseEntity<?> updateFloorPlan(@PathVariable Long id, @RequestBody BuildingFloorPlanDTO dto) {
        try { return ok(saleProService.updateFloorPlan(id, dto)); } catch (Exception e) { return err(e); }
    }

    @DeleteMapping("/floor-plans/{id}")
    public ResponseEntity<?> deleteFloorPlan(@PathVariable Long id) {
        try { saleProService.deleteFloorPlan(id); return okMsg("Đã xóa mặt bằng tầng."); } catch (Exception e) { return err(e); }
    }

    // ===== Tiến độ =====
    @PostMapping("/progress")
    public ResponseEntity<?> createProgress(@RequestBody ProjectProgressDTO dto) {
        try { return ok(saleProService.createProgress(dto)); } catch (Exception e) { return err(e); }
    }

    @PutMapping("/progress/{id}")
    public ResponseEntity<?> updateProgress(@PathVariable Long id, @RequestBody ProjectProgressDTO dto) {
        try { return ok(saleProService.updateProgress(id, dto)); } catch (Exception e) { return err(e); }
    }

    @DeleteMapping("/progress/{id}")
    public ResponseEntity<?> deleteProgress(@PathVariable Long id) {
        try { saleProService.deleteProgress(id); return okMsg("Đã xóa mốc tiến độ."); } catch (Exception e) { return err(e); }
    }

    // ===== Tài liệu =====
    @PostMapping("/documents")
    public ResponseEntity<?> createDocument(@RequestBody ProjectDocumentDTO dto) {
        try { return ok(saleProService.createDocument(dto)); } catch (Exception e) { return err(e); }
    }

    @PutMapping("/documents/{id}")
    public ResponseEntity<?> updateDocument(@PathVariable Long id, @RequestBody ProjectDocumentDTO dto) {
        try { return ok(saleProService.updateDocument(id, dto)); } catch (Exception e) { return err(e); }
    }

    @DeleteMapping("/documents/{id}")
    public ResponseEntity<?> deleteDocument(@PathVariable Long id) {
        try { saleProService.deleteDocument(id); return okMsg("Đã xóa tài liệu."); } catch (Exception e) { return err(e); }
    }
}
