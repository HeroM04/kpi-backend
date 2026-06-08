package com.trilong.kpibackend.modules.salepro.service;

import com.trilong.kpibackend.modules.salepro.entity.Apartment;
import com.trilong.kpibackend.modules.salepro.entity.Building;
import com.trilong.kpibackend.modules.salepro.entity.Project;
import com.trilong.kpibackend.modules.salepro.repository.ApartmentRepository;
import com.trilong.kpibackend.modules.salepro.repository.BuildingRepository;
import com.trilong.kpibackend.modules.salepro.repository.ProjectRepository;
import org.springframework.beans.factory.annotation.Autowired;
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

    public List<Project> getAllProjects() {
        return projectRepository.findAll();
    }

    public Project getProjectById(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Project not found with ID: " + id));
    }

    public List<Building> getBuildingsByProjectId(Long projectId) {
        return buildingRepository.findByProjectId(projectId);
    }

    public List<Apartment> getApartmentsByBuildingId(Long buildingId) {
        return apartmentRepository.findByBuildingId(buildingId);
    }

    @Transactional
    public Apartment updateApartmentStatus(Long apartmentId, String status) {
        Apartment apartment = apartmentRepository.findById(apartmentId)
                .orElseThrow(() -> new IllegalArgumentException("Apartment not found with ID: " + apartmentId));
        apartment.setStatus(status);
        return apartmentRepository.save(apartment);
    }
}
