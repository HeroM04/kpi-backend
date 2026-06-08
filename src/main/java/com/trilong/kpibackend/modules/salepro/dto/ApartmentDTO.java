package com.trilong.kpibackend.modules.salepro.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class ApartmentDTO {
    private Long id;
    private Long buildingId;
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
    private String giftsPromotions;
    private String handoverStandard;
    private String fundType;
}
