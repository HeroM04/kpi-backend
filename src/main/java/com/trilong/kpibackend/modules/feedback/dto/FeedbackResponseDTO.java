package com.trilong.kpibackend.modules.feedback.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackResponseDTO {
    private Long id;
    private Long senderId;
    private String senderFullName; // Ẩn nếu isAnonymous = true
    private String targetType;
    private Long targetId;
    private String content;
    private String status;
    private boolean isAnonymous;
    private LocalDateTime createdAt;
    private String title;
    private String category;
    private Integer rating;
    private String adminReply;
    private ZonedDateTime resolvedAt;
    private Long resolvedById;
    private String resolvedByFullName;
    /** Link xem ảnh đính kèm, ký tạm có hạn 24 giờ. Không có ảnh thì rỗng. */
    @Builder.Default
    private List<String> imageUrls = List.of();
}
