package com.trilong.kpibackend.modules.user.dto;

import lombok.Data;

import java.time.LocalDate;

/** Admin duyệt đơn giới thiệu — thông tin để mở tài khoản cho nhân sự mới. */
@Data
public class ApproveReferralDTO {

    /** Phòng ban nhận nhân sự mới (bắt buộc) */
    private Long departmentId;

    /** Vai trò, bỏ trống thì mặc định SALE */
    private String role;

    /** Ngày bắt đầu làm việc — mốc tính tròn một tháng. Bỏ trống lấy ngày duyệt. */
    private LocalDate joinedDate;

    /** Mật khẩu ban đầu, bỏ trống thì dùng 123456 */
    private String password;

    /** Ghi chú của Admin */
    private String note;
}
