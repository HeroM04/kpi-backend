package com.trilong.kpibackend.modules.battle.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Admin sửa báo cáo thực chiến — thêm trạng thái và lý do từ chối so với lúc
 * Sales gửi lên. Trường nào để null là giữ nguyên.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UpdateFieldBattleDTO extends SubmitFieldBattleDTO {

    /** PENDING | APPROVED | REJECTED. Bỏ trống thì giữ nguyên trạng thái cũ. */
    private String status;

    /** Bắt buộc khi đổi sang REJECTED — nhân sự xem trong Lịch sử trên app. */
    private String rejectReason;
}
