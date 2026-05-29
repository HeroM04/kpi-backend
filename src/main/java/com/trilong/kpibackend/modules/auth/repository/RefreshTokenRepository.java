package com.trilong.kpibackend.modules.auth.repository;

import com.trilong.kpibackend.modules.auth.entity.RefreshToken;
import com.trilong.kpibackend.modules.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    // Tìm token để verify khi refresh
    Optional<RefreshToken> findByToken(String token);

    // Lấy tất cả token của một user (quản lý phiên đăng nhập)
    List<RefreshToken> findByUserOrderByCreatedAtDesc(User user);

    // Revoke tất cả token của user khi đổi mật khẩu hoặc block tài khoản
    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revoked = true WHERE rt.user = :user")
    void revokeAllByUser(@Param("user") User user);

    // Dọn dẹp token hết hạn — chạy theo lịch (Scheduled job)
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :now OR rt.revoked = true")
    void deleteExpiredAndRevoked(@Param("now") ZonedDateTime now);

    // Đếm số phiên đăng nhập đang active của user (giới hạn thiết bị)
    @Query("SELECT COUNT(rt) FROM RefreshToken rt WHERE rt.user = :user AND rt.revoked = false AND rt.expiresAt > :now")
    long countActiveSessionsByUser(@Param("user") User user, @Param("now") ZonedDateTime now);
}
