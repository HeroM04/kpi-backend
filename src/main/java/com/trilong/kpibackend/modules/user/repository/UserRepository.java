package com.trilong.kpibackend.modules.user.repository;

import com.trilong.kpibackend.modules.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    /** Nhân sự do người khác giới thiệu và hiện vẫn đang làm việc. */
    List<User> findByReferrerIdIsNotNullAndStatus(String status);

    /** Những người mà một nhân sự đã giới thiệu vào công ty. */
    List<User> findByReferrerId(Long referrerId);

    /**
     * Đọc người dùng và KHÓA dòng đó tới hết giao dịch (SELECT … FOR UPDATE).
     * Dùng để xếp hàng các lượt gửi dồn dập của cùng một người — lượt sau chỉ
     * chạy khi lượt trước đã lưu xong, nên kiểm tra nộp trùng mới thấy nhau.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> khoaTheoId(@Param("id") Long id);
}

