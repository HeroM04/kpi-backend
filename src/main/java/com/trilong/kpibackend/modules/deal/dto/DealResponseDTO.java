package com.trilong.kpibackend.modules.deal.dto;

import com.trilong.kpibackend.modules.deal.entity.Deal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DealResponseDTO {
    private Long id;
    private Long userId;
    private String userFullName;
    private String projectName;
    private String unit;
    private Double price;
    private Double commission;
    private String customerName;
    private String customerPhone;
    private String status;
    private ZonedDateTime submittedAt;
    private ZonedDateTime approvedAt;
    private Long approvedById;
    private String approvedByFullName;
    private Integer kpiTriggered;
    private String contractPhotoUrl;

    public static DealResponseDTO from(Deal deal) {
        if (deal == null) return null;
        return DealResponseDTO.builder()
                .id(deal.getId())
                .userId(deal.getUser().getId())
                .userFullName(deal.getUser().getFullName())
                .projectName(deal.getProjectName())
                .unit(deal.getUnit())
                .price(deal.getPrice())
                .commission(deal.getCommission())
                .customerName(deal.getCustomerName())
                .customerPhone(deal.getCustomerPhone())
                .status(deal.getStatus())
                .submittedAt(deal.getSubmittedAt())
                .approvedAt(deal.getApprovedAt())
                .approvedById(deal.getApprovedBy() != null ? deal.getApprovedBy().getId() : null)
                .approvedByFullName(deal.getApprovedBy() != null ? deal.getApprovedBy().getFullName() : null)
                .kpiTriggered(deal.getKpiTriggered())
                .contractPhotoUrl(deal.getContractPhotoUrl())
                .build();
    }
}
