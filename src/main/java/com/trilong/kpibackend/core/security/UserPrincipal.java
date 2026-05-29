package com.trilong.kpibackend.core.security;

import com.trilong.kpibackend.modules.user.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * UserPrincipal — đối tượng đại diện user đang đăng nhập.
 *
 * Implement UserDetails để tích hợp với Spring Security:
 * - Được set vào SecurityContextHolder bởi JwtAuthFilter
 * - Truy cập trong Controller qua @AuthenticationPrincipal UserPrincipal
 * - Authorities được map từ role: "SALE" → "ROLE_SALE"
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPrincipal implements UserDetails {

    private Long userId;
    private String phoneNumber;
    private String fullName;
    private String role;          // SALE | ADMIN | TRUONG_PHONG | VAN_PHONG
    private Long departmentId;
    private String password;      // BCrypt hash — cần cho UserDetails
    private boolean active;
    private String avatarUrl;     // Ảnh chân dung gốc xác thực khuôn mặt

    private List<String> permissions; // Thêm trường danh sách permissions để serialize cho client

    // ── Factory ─────────────────────────────────────────────────────────────

    /** Tạo từ User entity (dùng khi login) */
    public static UserPrincipal from(User user) {
        java.util.Set<AppPermission> permSet = AppPermission.getPermissionsByRole(user.getRole());
        List<String> permStrings = permSet.stream().map(AppPermission::getValue).toList();

        return UserPrincipal.builder()
                .userId(user.getId())
                .phoneNumber(user.getPhoneNumber())
                .fullName(user.getFullName())
                .role(user.getRole())
                .departmentId(user.getDepartment() != null ? user.getDepartment().getId() : null)
                .password(user.getPasswordHash())
                .active("ACTIVE".equals(user.getStatus()))
                .avatarUrl(user.getAvatarUrl())
                .permissions(permStrings)
                .build();
    }

    /** Tạo từ JWT claims (dùng ở JwtAuthFilter — không query DB) */
    public static UserPrincipal fromClaims(Long userId, String phone, String name,
                                           String role, Long departmentId, String avatarUrl) {
        java.util.Set<AppPermission> permSet = AppPermission.getPermissionsByRole(role);
        List<String> permStrings = permSet.stream().map(AppPermission::getValue).toList();

        return UserPrincipal.builder()
                .userId(userId)
                .phoneNumber(phone)
                .fullName(name)
                .role(role)
                .departmentId(departmentId)
                .active(true)
                .avatarUrl(avatarUrl)
                .permissions(permStrings)
                .build();
    }

    // ── UserDetails interface ────────────────────────────────────────────────

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<SimpleGrantedAuthority> authorities = new java.util.ArrayList<>();
        
        // 1. Thêm Role với prefix "ROLE_" cho Spring Security hasRole()
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        
        // 2. Thêm các Permission thô cho hasAuthority()
        AppPermission.getPermissionsByRole(role).forEach(permission -> {
            authorities.add(new SimpleGrantedAuthority(permission.getValue()));
        });
        
        return authorities;
    }

    @Override
    public String getPassword() { return password; }

    @Override
    public String getUsername() { return phoneNumber; }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return active; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return active; }

    // ── Role helpers ─────────────────────────────────────────────────────────

    public boolean isAdmin()        { return "ADMIN".equals(role); }
    public boolean isSale()         { return "SALE".equals(role); }
    public boolean isTruongPhong()  { return "TRUONG_PHONG".equals(role); }
    public boolean isVanPhong()     { return "VAN_PHONG".equals(role); }

    /** Duyệt check-in / báo cáo KPI */
    public boolean canApprove() { return isAdmin() || isTruongPhong(); }

    /** Xem dashboard toàn công ty */
    public boolean canViewAllDepartments() { return isAdmin() || isVanPhong(); }
}
