package com.trilong.kpibackend.modules.user.service;

import com.trilong.kpibackend.modules.user.dto.DepartmentDTO;
import com.trilong.kpibackend.modules.user.entity.Department;
import com.trilong.kpibackend.modules.user.repository.DepartmentRepository;
import com.trilong.kpibackend.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;

    public List<DepartmentDTO> getAllDepartments() {
        return departmentRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public DepartmentDTO getDepartmentById(Long id) {
        Department dept = departmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phòng ban với ID: " + id));
        return convertToDTO(dept);
    }

    @Transactional
    public DepartmentDTO createDepartment(DepartmentDTO dto) {
        Department dept = Department.builder()
                .name(dto.getName())
                .officeLat(dto.getOfficeLat())
                .officeLng(dto.getOfficeLng())
                .allowedRadius(dto.getAllowedRadius() != null ? dto.getAllowedRadius() : 50)
                .build();
        Department saved = departmentRepository.save(dept);
        return convertToDTO(saved);
    }

    @Transactional
    public DepartmentDTO updateDepartment(Long id, DepartmentDTO dto) {
        Department dept = departmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phòng ban với ID: " + id));

        if (dto.getName() != null) {
            dept.setName(dto.getName());
        }
        if (dto.getOfficeLat() != null) {
            dept.setOfficeLat(dto.getOfficeLat());
        }
        if (dto.getOfficeLng() != null) {
            dept.setOfficeLng(dto.getOfficeLng());
        }
        if (dto.getAllowedRadius() != null) {
            dept.setAllowedRadius(dto.getAllowedRadius());
        }

        Department saved = departmentRepository.save(dept);
        return convertToDTO(saved);
    }

    @Transactional
    public void deleteDepartment(Long id) {
        if (!departmentRepository.existsById(id)) {
            throw new IllegalArgumentException("Không tìm thấy phòng ban với ID: " + id);
        }

        // Unassign users from this department to avoid foreign key constraint violation
        userRepository.findByFilters(id, null, null).forEach(user -> {
            user.setDepartment(null);
            userRepository.save(user);
        });

        departmentRepository.deleteById(id);
    }

    public DepartmentDTO convertToDTO(Department dept) {
        if (dept == null) return null;
        return DepartmentDTO.builder()
                .id(dept.getId())
                .name(dept.getName())
                .officeLat(dept.getOfficeLat())
                .officeLng(dept.getOfficeLng())
                .allowedRadius(dept.getAllowedRadius())
                .build();
    }
}
