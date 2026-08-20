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
    /**
     * Không cho hai phòng ban trùng tên. Admin chọn phòng ban qua ô danh sách
     * chỉ thấy mỗi cái tên, trùng tên là không phân biệt được phòng nào —
     * mà mỗi phòng lại mang toạ độ văn phòng riêng dùng để chấm công.
     *
     * @param selfId bỏ qua chính phòng đang sửa, để lưu lại tên cũ vẫn được
     */
    private void requireUniqueName(String name, Long selfId) {
        if (name == null || name.isBlank()) return;
        String wanted = name.trim();
        boolean taken = departmentRepository.findAll().stream()
                .filter(d -> selfId == null || !d.getId().equals(selfId))
                .anyMatch(d -> d.getName() != null && d.getName().trim().equalsIgnoreCase(wanted));
        if (taken) {
            throw new IllegalArgumentException("Đã có phòng ban tên '" + wanted + "'. Vui lòng đặt tên khác.");
        }
    }

    public DepartmentDTO createDepartment(DepartmentDTO dto) {
        requireUniqueName(dto.getName(), null);
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
            requireUniqueName(dto.getName(), id);
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
