package com.trilong.kpibackend.modules.post.dto;

import com.trilong.kpibackend.modules.post.entity.SocialPost;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SocialPostResponseDTO {
    private Long id;
    private Long userId;
    private String userFullName;
    private String platform;
    private String link;
    private String caption;
    private String screenshotUrl;
    private String status;
    private ZonedDateTime submittedAt;
    private ZonedDateTime approvedAt;
    private Long approvedById;
    private String approvedByFullName;

    public static SocialPostResponseDTO from(SocialPost post) {
        if (post == null) return null;
        return SocialPostResponseDTO.builder()
                .id(post.getId())
                .userId(post.getUser().getId())
                .userFullName(post.getUser().getFullName())
                .platform(post.getPlatform())
                .link(post.getLink())
                .caption(post.getCaption())
                .screenshotUrl(post.getScreenshotUrl())
                .status(post.getStatus())
                .submittedAt(post.getSubmittedAt())
                .approvedAt(post.getApprovedAt())
                .approvedById(post.getApprovedBy() != null ? post.getApprovedBy().getId() : null)
                .approvedByFullName(post.getApprovedBy() != null ? post.getApprovedBy().getFullName() : null)
                .build();
    }
}
