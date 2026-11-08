package com.trilong.kpibackend.modules.deal.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Admin sửa yêu cầu chốt căn — thêm trạng thái và lý do từ chối so với lúc
 * Sales gửi lên. Trường nào để null là giữ nguyên.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UpdateDealDTO extends SubmitDealDTO {

    /** PENDING | APPROVED | REJECTED. Bỏ trống thì giữ nguyên trạng thái cũ. */
    private String status;

    /** Bắt buộc khi đổi sang REJECTED — nhân sự xem trong Lịch sử trên app. */
    private String rejectReason;
}
