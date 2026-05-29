package com.trilong.kpibackend.modules.deal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SubmitDealDTO {

    @NotBlank(message = "Tên khách hàng không được để trống")
    private String customerName;

    @NotBlank(message = "Số điện thoại khách hàng không được để trống")
    private String customerPhone;

    @NotBlank(message = "Tên dự án không được để trống")
    private String projectName;

    @NotBlank(message = "Mã căn/lô không được để trống")
    private String unit;

    @NotNull(message = "Giá bán không được để trống")
    private Double price;

    private Double commission;

    private String contractPhotoUrl;
}
