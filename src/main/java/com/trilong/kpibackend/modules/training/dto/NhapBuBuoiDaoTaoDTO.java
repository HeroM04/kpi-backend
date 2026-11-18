package com.trilong.kpibackend.modules.training.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * Nhập bù một buổi đào tạo ĐÃ DIỄN RA ngoài app (lớp học xong mới nhớ lên lịch),
 * kèm luôn danh sách người đã dự.
 *
 * <p>Tạo buổi rồi điểm danh tay từng người thì ngay lúc tạo, cả công ty bị chấm
 * "thiếu buổi" và nhận thông báo trừ điểm; người đã dự phải đợi Admin điểm danh
 * tới tên mới được trả lại. Gửi một lần cả danh sách thì chỉ người vắng bị đụng.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class NhapBuBuoiDaoTaoDTO extends CreateTrainingSessionDTO {

    /** Mã nhân sự đã dự buổi học. Rỗng = không ai dự (cả công ty thiếu buổi). */
    private List<Long> nguoiDaDu;
}
