package com.trilong.kpibackend.modules.user.repository;

import com.trilong.kpibackend.modules.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // Tìm user bằng số điện thoại để login
    Optional<User> findByPhoneNumber(String phoneNumber);

    // Kiểm tra số điện thoại đã tồn tại chưa (để tránh trùng)
    boolean existsByPhoneNumber(String phoneNumber);

    // Lấy danh sách nhân viên trong cùng phòng ban
    List<User> findByDepartmentIdAndStatus(Long departmentId, String status);

    // Lấy danh sách theo role
    List<User> findByRole(String role);

    // Lọc theo nhiều điều kiện (các tham số đều optional)
    @Query("SELECT u FROM User u WHERE " +
           "(:departmentId IS NULL OR u.department.id = :departmentId) AND " +
           "(:role IS NULL OR u.role = :role) AND " +
           "(:status IS NULL OR u.status = :status)")
    List<User> findByFilters(
        @Param("departmentId") Long departmentId,
        @Param("role") String role,
        @Param("status") String status
    );
}

