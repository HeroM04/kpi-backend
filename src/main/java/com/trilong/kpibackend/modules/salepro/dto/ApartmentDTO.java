package com.trilong.kpibackend.modules.salepro.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;

@Data
public class ApartmentDTO {
    private Long id;
    private Long buildingId;
    private String buildingName;
    private String subdivisionName;
    private Long projectId;        // cho So sánh "DỰ ÁN: …"
    private String projectName;
    private String apartmentCode;
    private String thumbnailUrl;
    private String apartmentType;
    private String direction;
    private String floor;
    private String axis;
    private String viewDescription;
    private String status;
    private BigDecimal clearanceArea;
    private BigDecimal builtUpArea;
    private BigDecimal landArea;
    private BigDecimal constructionArea;
    private BigDecimal listedPrice;
    private BigDecimal loanPrice;
    private BigDecimal earlyPaymentPrice;
    private BigDecimal progressPaymentPrice;
    private String supportedBanks;
    private String salesPolicyApplied;
    private LocalDate salesPolicyDate;   // "CSBH áp dụng" dạng ngày
    private String giftsPromotions;
    private String handoverStandard;
    private String fundType;
    private ZonedDateTime updatedAt;

    // Hỏi đáp
    private long questionCount;

    // Chuyên viên quản lý (lấy từ Project.managingAgent)
    private String agentName;
    private String agentTitle;
    private String agentPhone;
    private String agentAvatarUrl;
}
