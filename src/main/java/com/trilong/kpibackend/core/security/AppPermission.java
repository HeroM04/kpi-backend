package com.trilong.kpibackend.core.security;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Định nghĩa các Permission chi tiết trong hệ thống (RBAC).
 * Giúp kiểm soát truy cập ở mức độ hạt mịn (Fine-grained access control).
 */
public enum AppPermission {

    // ── Quyền chấm công (Attendance) ─────────────────────────────────────────
    ATTENDANCE_CHECKIN("attendance:checkin", "Quyền chấm công hàng ngày"),
    ATTENDANCE_VIEW_MY("attendance:view-my", "Xem lịch sử chấm công cá nhân"),
    ATTENDANCE_VIEW_ALL("attendance:view-all", "Xem lịch sử chấm công toàn công ty"),
    ATTENDANCE_APPROVE("attendance:approve", "Duyệt chấm công ngoài văn phòng"),

    // ── Quyền quản lý KPI ────────────────────────────────────────────────────
    KPI_VIEW_MY("kpi:view-my", "Xem bảng KPI cá nhân"),
    KPI_VIEW_ALL("kpi:view-all", "Xem bảng KPI toàn công ty"),
    KPI_FLAG("kpi:flag", "Cắm cờ đánh dấu nghi ngờ bản ghi KPI"),
    KPI_MANAGE("kpi:manage", "Thiết lập, phân bổ chỉ tiêu KPI"),
    KPI_APPROVE("kpi:approve", "Duyệt kết quả báo cáo KPI"),

    // ── Quyền tính lương (Payroll) ───────────────────────────────────────────
    PAYROLL_VIEW_MY("payroll:view-my", "Xem phiếu lương cá nhân"),
    PAYROLL_MANAGE("payroll:manage", "Quản lý và tính toán bảng lương tháng"),

    // ── Quyền quản trị hệ thống (User/Department) ────────────────────────────
    USER_MANAGE("user:manage", "Quản lý tài khoản nhân viên"),
    DEPARTMENT_MANAGE("department:manage", "Quản lý phòng ban và tọa độ GPS"),

    // ── Quyền chốt căn (Deal) ────────────────────────────────────────────────
    DEAL_SUBMIT("deal:submit", "Gửi yêu cầu chốt căn"),
    DEAL_VIEW_MY("deal:view-my", "Xem danh sách chốt căn cá nhân"),
    DEAL_APPROVE("deal:approve", "Phê duyệt yêu cầu chốt căn"),
    DEAL_MANAGE("deal:manage", "Quản lý toàn bộ danh sách chốt căn"),

    // ── Quyền bài đăng MXH (Social Post) ─────────────────────────────────────
    POST_SUBMIT("post:submit", "Gửi link bài đăng MXH"),
    POST_VIEW_MY("post:view-my", "Xem bài đăng cá nhân"),
    POST_APPROVE("post:approve", "Duyệt bài đăng MXH"),
    POST_MANAGE("post:manage", "Quản lý toàn bộ bài đăng MXH"),

    // ── Quyền thực chiến gặp khách (Field Battle) ────────────────────────────
    MEETING_SUBMIT("meeting:submit", "Gửi báo cáo thực chiến gặp khách"),
    MEETING_VIEW_MY("meeting:view-my", "Xem báo cáo thực chiến cá nhân"),
    MEETING_APPROVE("meeting:approve", "Duyệt báo cáo thực chiến"),
    MEETING_MANAGE("meeting:manage", "Quản lý toàn bộ báo cáo thực chiến"),

    // ── Quyền đào tạo (Training) ─────────────────────────────────────────────
    TRAINING_VIEW("training:view", "Xem danh sách lớp đào tạo"),
    TRAINING_ATTEND("training:attend", "Quét mã điểm danh đào tạo"),
    TRAINING_MANAGE("training:manage", "Quản lý buổi đào tạo"),

    // ── Quyền phản hồi (Feedback) ────────────────────────────────────────────
    FEEDBACK_MANAGE("feedback:manage", "Phản hồi ý kiến và khiếu nại nhân sự");

    private final String value;
    private final String description;

    AppPermission(String value, String description) {
        this.value = value;
        this.description = description;
    }

    public String getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }

    // ── Cấu hình map tĩnh giữa Role và Permissions ──────────────────────────

    private static final Map<String, Set<AppPermission>> ROLE_PERMISSIONS = Map.of(
            "ADMIN", Set.of(AppPermission.values()), // Admin có toàn quyền

            "TRUONG_PHONG", Set.of(
                    ATTENDANCE_CHECKIN,
                    ATTENDANCE_VIEW_MY,
                    ATTENDANCE_VIEW_ALL,
                    ATTENDANCE_APPROVE,
                    KPI_VIEW_MY,
                    KPI_VIEW_ALL,
                    KPI_MANAGE,
                    KPI_APPROVE,
                    PAYROLL_VIEW_MY,
                    DEAL_SUBMIT,
                    DEAL_VIEW_MY,
                    DEAL_APPROVE,
                    DEAL_MANAGE,
                    POST_SUBMIT,
                    POST_VIEW_MY,
                    POST_APPROVE,
                    POST_MANAGE,
                    MEETING_SUBMIT,
                    MEETING_VIEW_MY,
                    MEETING_APPROVE,
                    MEETING_MANAGE,
                    TRAINING_VIEW,
                    TRAINING_ATTEND,
                    TRAINING_MANAGE
            ),

            "VAN_PHONG", Set.of(
                    ATTENDANCE_CHECKIN,
                    ATTENDANCE_VIEW_MY,
                    ATTENDANCE_VIEW_ALL,
                    KPI_VIEW_MY,
                    KPI_VIEW_ALL,
                    PAYROLL_VIEW_MY,
                    PAYROLL_MANAGE,
                    FEEDBACK_MANAGE,
                    TRAINING_VIEW
            ),

            "SALE", Set.of(
                    ATTENDANCE_CHECKIN,
                    ATTENDANCE_VIEW_MY,
                    KPI_VIEW_MY,
                    PAYROLL_VIEW_MY,
                    DEAL_SUBMIT,
                    DEAL_VIEW_MY,
                    POST_SUBMIT,
                    POST_VIEW_MY,
                    MEETING_SUBMIT,
                    MEETING_VIEW_MY,
                    TRAINING_VIEW,
                    TRAINING_ATTEND
            )
    );

    /**
     * Lấy danh sách các Permission tương ứng với một Role.
     */
    public static Set<AppPermission> getPermissionsByRole(String role) {
        if (role == null) return Set.of();
        return ROLE_PERMISSIONS.getOrDefault(role.toUpperCase(), Set.of());
    }
}

