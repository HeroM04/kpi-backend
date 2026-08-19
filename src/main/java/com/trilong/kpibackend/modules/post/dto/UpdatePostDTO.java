package com.trilong.kpibackend.modules.post.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** Admin sửa bài đăng — thêm trường trạng thái so với lúc Sales gửi lên. */
@Data
@EqualsAndHashCode(callSuper = true)
public class UpdatePostDTO extends SubmitPostDTO {

    /** PENDING | APPROVED | REJECTED. Bỏ trống thì giữ nguyên trạng thái cũ. */
    private String status;
}
