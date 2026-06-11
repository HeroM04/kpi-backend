package com.trilong.kpibackend.modules.salepro.service;

import com.trilong.kpibackend.modules.salepro.dto.ApartmentDTO;
import com.trilong.kpibackend.modules.salepro.dto.ApartmentQuestionDTO;
import com.trilong.kpibackend.modules.salepro.dto.BuildingDTO;
import com.trilong.kpibackend.modules.salepro.dto.BuildingFloorPlanDTO;
import com.trilong.kpibackend.modules.salepro.dto.ProjectDTO;
import com.trilong.kpibackend.modules.salepro.dto.ProjectDocumentDTO;
import com.trilong.kpibackend.modules.salepro.dto.ProjectProgressDTO;
import com.trilong.kpibackend.modules.salepro.dto.SalesAgentDTO;
import com.trilong.kpibackend.modules.salepro.entity.Apartment;
import com.trilong.kpibackend.modules.salepro.entity.ApartmentQuestion;
import com.trilong.kpibackend.modules.salepro.entity.Building;
import com.trilong.kpibackend.modules.salepro.entity.BuildingFloorPlan;
import com.trilong.kpibackend.modules.salepro.entity.Project;
import com.trilong.kpibackend.modules.salepro.entity.ProjectDocument;
import com.trilong.kpibackend.modules.salepro.entity.ProjectProgress;
import com.trilong.kpibackend.modules.salepro.entity.SalesAgent;
import com.trilong.kpibackend.modules.salepro.repository.ApartmentQuestionRepository;
import com.trilong.kpibackend.modules.salepro.repository.ApartmentRepository;
import com.trilong.kpibackend.modules.salepro.repository.BuildingFloorPlanRepository;
import com.trilong.kpibackend.modules.salepro.repository.BuildingRepository;
import com.trilong.kpibackend.modules.salepro.repository.ProjectDocumentRepository;
import com.trilong.kpibackend.modules.salepro.repository.ProjectProgressRepository;
import com.trilong.kpibackend.modules.salepro.repository.ProjectRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SaleProService {

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private BuildingRepository buildingRepository;

    @Autowired
    private ApartmentRepository apartmentRepository;

    @Autowired
    private BuildingFloorPlanRepository buildingFloorPlanRepository;

    @Autowired
    private ApartmentQuestionRepository apartmentQuestionRepository;

    @Autowired
    private ProjectProgressRepository projectProgressRepository;

    @Autowired
    private ProjectDocumentRepository projectDocumentRepository;

    // ====================== PROJECTS ======================

    @Transactional(readOnly = true)
    public List<ProjectDTO> getAllProjects() {
        return projectRepository.findAll().stream()
                .map(this::toProjectDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectDTO getProjectById(Long id) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Project not found with ID: " + id));
        return toProjectDTO(project);
    }

    // ====================== BUILDINGS ======================

    // Map sang DTO trong transaction vì các quan hệ LAZY không serialize được khi open-in-view=false
    @Transactional(readOnly = true)
    public List<BuildingDTO> getBuildingsByProjectId(Long projectId) {
        return buildingRepository.findByProjectId(projectId).stream()
                .map(this::toBuildingDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public BuildingDTO getBuildingById(Long buildingId) {
        Building building = buildingRepository.findById(buildingId)
                .orElseThrow(() -> new IllegalArgumentException("Building not found with ID: " + buildingId));
        return toBuildingDTO(building);
    }

    @Transactional(readOnly = true)
    public List<BuildingFloorPlanDTO> getFloorPlansByBuildingId(Long buildingId) {
        return buildingFloorPlanRepository.findByBuildingIdOrderBySortOrderAsc(buildingId).stream()
                .map(this::toFloorPlanDTO)
                .toList();
    }

    // ====================== APARTMENTS ======================

    @Transactional(readOnly = true)
    public List<ApartmentDTO> getApartmentsByBuildingId(Long buildingId) {
        return apartmentRepository.findByBuildingId(buildingId).stream()
                .map(this::toApartmentDTO)
                .toList();
    }

    // Gộp toàn bộ căn của 1 dự án trong 1 call (FE không cần loop từng tòa) — dùng cho ma trận/so sánh
    @Transactional(readOnly = true)
    public List<ApartmentDTO> getApartmentsByProjectId(Long projectId) {
        return apartmentRepository.findAllByProjectId(projectId).stream()
                .map(this::toApartmentDTO)
                .toList();
    }

    // Phân trang + lọc + tìm kiếm quỹ căn (bảng hàng)
    @Transactional(readOnly = true)
    public Page<ApartmentDTO> searchApartments(Long projectId, String status, Long buildingId,
                                               String type, String direction, String q, Pageable pageable) {
        return apartmentRepository.searchByProject(
                projectId, norm(status), buildingId, norm(type), norm(direction), norm(q), pageable
        ).map(this::toApartmentDTO);
    }

    // "ALL"/rỗng -> null để bỏ qua điều kiện lọc
    private String norm(String v) {
        if (v == null) return null;
        String t = v.trim();
        if (t.isEmpty() || t.equalsIgnoreCase("ALL")) return null;
        return t;
    }

    @Transactional
    public ApartmentDTO updateApartmentStatus(Long apartmentId, String status) {
        Apartment apartment = apartmentRepository.findById(apartmentId)
                .orElseThrow(() -> new IllegalArgumentException("Apartment not found with ID: " + apartmentId));
        apartment.setStatus(status);
        return toApartmentDTO(apartmentRepository.save(apartment));
    }

    // ====================== HỎI ĐÁP (Q&A) ======================

    @Transactional(readOnly = true)
    public List<ApartmentQuestionDTO> getQuestionsByApartmentId(Long apartmentId) {
        return apartmentQuestionRepository.findByApartmentIdOrderByCreatedAtDesc(apartmentId).stream()
                .map(this::toQuestionDTO)
                .toList();
    }

    @Transactional
    public ApartmentQuestionDTO createQuestion(Long apartmentId, ApartmentQuestionDTO body) {
        Apartment apartment = apartmentRepository.findById(apartmentId)
                .orElseThrow(() -> new IllegalArgumentException("Apartment not found with ID: " + apartmentId));
        ApartmentQuestion q = ApartmentQuestion.builder()
                .apartment(apartment)
                .fullName(body.getFullName())
                .phone(body.getPhone())
                .content(body.getContent())
                .status("PENDING")
                .build();
        return toQuestionDTO(apartmentQuestionRepository.save(q));
    }

    // ====================== TIẾN ĐỘ / TÀI LIỆU ======================

    @Transactional(readOnly = true)
    public List<ProjectProgressDTO> getProgressByProjectId(Long projectId) {
        return projectProgressRepository.findByProjectIdOrderBySortOrderAscProgressDateDesc(projectId).stream()
                .map(this::toProgressDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProjectDocumentDTO> getDocumentsByProjectId(Long projectId) {
        return projectDocumentRepository.findByProjectIdOrderBySortOrderAsc(projectId).stream()
                .map(this::toDocumentDTO)
                .toList();
    }

    // ====================== MAPPERS ======================

    private ProjectDTO toProjectDTO(Project project) {
        ProjectDTO dto = new ProjectDTO();
        dto.setId(project.getId());
        dto.setName(project.getName());
        dto.setProjectType(project.getProjectType());
        dto.setStatus(project.getStatus());
        dto.setDetails(project.getDetails());
        dto.setManagingAgent(toAgentDTO(project.getManagingAgent()));
        return dto;
    }

    private SalesAgentDTO toAgentDTO(SalesAgent agent) {
        if (agent == null) return null;
        SalesAgentDTO dto = new SalesAgentDTO();
        dto.setId(agent.getId());
        dto.setFullName(agent.getFullName());
        dto.setTitle(agent.getTitle());
        dto.setPhone(agent.getPhone());
        dto.setEmail(agent.getEmail());
        dto.setAvatarUrl(agent.getAvatarUrl());
        dto.setZaloLink(agent.getZaloLink());
        return dto;
    }

    private BuildingDTO toBuildingDTO(Building building) {
        BuildingDTO dto = new BuildingDTO();
        dto.setId(building.getId());
        dto.setProjectId(building.getProject() != null ? building.getProject().getId() : null);
        dto.setBuildingName(building.getBuildingName());
        dto.setSubdivisionName(building.getSubdivisionName());
        dto.setTotalFloors(building.getTotalFloors());
        dto.setApartmentCount(apartmentRepository.countByBuildingId(building.getId()));
        dto.setAvailableCount(apartmentRepository.countByBuildingIdAndStatus(building.getId(), "CON_HANG"));
        dto.setOwnershipType(building.getOwnershipType());
        dto.setBuildingHandoverStandard(building.getBuildingHandoverStandard());
        dto.setTotalArea(building.getTotalArea());
        dto.setTotalApartments(building.getTotalApartments());
        dto.setElevatorCount(building.getElevatorCount());
        dto.setDescription(building.getDescription());
        dto.setImageUrl(building.getImageUrl());
        dto.setConstructionProgress(building.getConstructionProgress());
        dto.setSalesPolicy(building.getSalesPolicy());
        dto.setMarkerLat(building.getMarkerLat());
        dto.setMarkerLng(building.getMarkerLng());
        dto.setFloorPlans(getFloorPlansByBuildingId(building.getId()));
        return dto;
    }

    private BuildingFloorPlanDTO toFloorPlanDTO(BuildingFloorPlan fp) {
        BuildingFloorPlanDTO dto = new BuildingFloorPlanDTO();
        dto.setId(fp.getId());
        dto.setBuildingId(fp.getBuilding() != null ? fp.getBuilding().getId() : null);
        dto.setFloorLabel(fp.getFloorLabel());
        dto.setImageUrl(fp.getImageUrl());
        dto.setNote(fp.getNote());
        dto.setSortOrder(fp.getSortOrder());
        return dto;
    }

    private ApartmentDTO toApartmentDTO(Apartment apartment) {
        ApartmentDTO dto = new ApartmentDTO();
        dto.setId(apartment.getId());
        Building building = apartment.getBuilding();
        if (building != null) {
            dto.setBuildingId(building.getId());
            dto.setBuildingName(building.getBuildingName());
            dto.setSubdivisionName(building.getSubdivisionName());
            Project project = building.getProject();
            if (project != null) {
                dto.setProjectId(project.getId());
                dto.setProjectName(project.getName());
                SalesAgent agent = project.getManagingAgent();
                if (agent != null) {
                    dto.setAgentName(agent.getFullName());
                    dto.setAgentTitle(agent.getTitle());
                    dto.setAgentPhone(agent.getPhone());
                    dto.setAgentAvatarUrl(agent.getAvatarUrl());
                }
            }
        }
        dto.setApartmentCode(apartment.getApartmentCode());
        dto.setThumbnailUrl(apartment.getThumbnailUrl());
        dto.setApartmentType(apartment.getApartmentType());
        dto.setDirection(apartment.getDirection());
        dto.setFloor(apartment.getFloor());
        dto.setAxis(apartment.getAxis());
        dto.setViewDescription(apartment.getViewDescription());
        dto.setStatus(apartment.getStatus());
        dto.setClearanceArea(apartment.getClearanceArea());
        dto.setBuiltUpArea(apartment.getBuiltUpArea());
        dto.setLandArea(apartment.getLandArea());
        dto.setConstructionArea(apartment.getConstructionArea());
        dto.setListedPrice(apartment.getListedPrice());
        dto.setLoanPrice(apartment.getLoanPrice());
        dto.setEarlyPaymentPrice(apartment.getEarlyPaymentPrice());
        dto.setProgressPaymentPrice(apartment.getProgressPaymentPrice());
        dto.setSupportedBanks(apartment.getSupportedBanks());
        dto.setSalesPolicyApplied(apartment.getSalesPolicyApplied());
        dto.setSalesPolicyDate(apartment.getSalesPolicyDate());
        dto.setGiftsPromotions(apartment.getGiftsPromotions());
        dto.setHandoverStandard(apartment.getHandoverStandard());
        dto.setFundType(apartment.getFundType());
        dto.setUpdatedAt(apartment.getUpdatedAt());
        dto.setQuestionCount(apartmentQuestionRepository.countByApartmentId(apartment.getId()));
        return dto;
    }

    private ApartmentQuestionDTO toQuestionDTO(ApartmentQuestion q) {
        ApartmentQuestionDTO dto = new ApartmentQuestionDTO();
        dto.setId(q.getId());
        dto.setApartmentId(q.getApartment() != null ? q.getApartment().getId() : null);
        dto.setFullName(q.getFullName());
        dto.setPhone(q.getPhone());
        dto.setContent(q.getContent());
        dto.setAnswer(q.getAnswer());
        dto.setAnsweredBy(q.getAnsweredBy());
        dto.setStatus(q.getStatus());
        dto.setCreatedAt(q.getCreatedAt());
        return dto;
    }

    private ProjectProgressDTO toProgressDTO(ProjectProgress p) {
        ProjectProgressDTO dto = new ProjectProgressDTO();
        dto.setId(p.getId());
        dto.setProjectId(p.getProject() != null ? p.getProject().getId() : null);
        dto.setTitle(p.getTitle());
        dto.setProgressDate(p.getProgressDate());
        dto.setExternalUrl(p.getExternalUrl());
        dto.setImages(p.getImages());
        dto.setSortOrder(p.getSortOrder());
        return dto;
    }

    private ProjectDocumentDTO toDocumentDTO(ProjectDocument d) {
        ProjectDocumentDTO dto = new ProjectDocumentDTO();
        dto.setId(d.getId());
        dto.setProjectId(d.getProject() != null ? d.getProject().getId() : null);
        dto.setLabel(d.getLabel());
        dto.setDriveUrl(d.getDriveUrl());
        dto.setDocType(d.getDocType());
        dto.setSortOrder(d.getSortOrder());
        return dto;
    }
}
