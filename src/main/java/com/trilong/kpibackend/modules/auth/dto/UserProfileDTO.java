package com.trilong.kpibackend.modules.auth.dto;

import com.trilong.kpibackend.core.security.UserPrincipal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Profile của user đang đăng nhập — lấy từ token, không query DB. */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class UserProfileDTO {
    private Long   userId;
    private String fullName;
    private String phoneNumber;
    private String role;
    private Long   departmentId;
    private String avatarUrl;
    private java.util.List<String> permissions;

    public static UserProfileDTO from(UserPrincipal principal) {
        return UserProfileDTO.builder()
                .userId(principal.getUserId())
                .fullName(principal.getFullName())
                .phoneNumber(principal.getPhoneNumber())
                .role(principal.getRole())
                .departmentId(principal.getDepartmentId())
                .avatarUrl(principal.getAvatarUrl())
                .permissions(principal.getPermissions())
                .build();
    }
}
