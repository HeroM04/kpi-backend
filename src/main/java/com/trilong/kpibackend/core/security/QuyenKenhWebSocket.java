package com.trilong.kpibackend.core.security;

import java.util.List;
import java.util.Set;

/**
 * Ai được nghe kênh WebSocket nào.
 *
 * <p>20/11/2026: trước đó máy chủ không kiểm tra gì — không đăng nhập cũng nối
 * được và nghe được {@code /topic/thuc-chien/admin} (tên + SĐT khách, vị trí),
 * {@code /topic/kpi/<id>} của bất kỳ ai… Giờ mặc định là CẤM; chỉ mở đúng các
 * kênh dưới đây.
 *
 * <ul>
 *   <li>Kênh riêng ({@code /topic/kpi/7}…): chỉ chính người số 7.</li>
 *   <li>Kênh chung ({@code /topic/dong-bo/all}, thông báo toàn công ty): ai đã đăng nhập cũng nghe.</li>
 *   <li>Kênh quản trị: theo đúng quyền xem dữ liệu đó qua API.</li>
 * </ul>
 * Kênh có ký tự đại diện ({@code *}) bị cấm hết — trình phát tin của Spring hiểu
 * {@code /topic/**} là "mọi kênh".
 */
public final class QuyenKenhWebSocket {

    private QuyenKenhWebSocket() {}

    private static final Set<String> KENH_CHUNG = Set.of(
            "/topic/dong-bo/all",
            "/topic/notifications/broadcast");

    /** Tiền tố kênh riêng — phần sau tiền tố phải đúng mã người đang nghe. */
    private static final List<String> KENH_RIENG = List.of(
            "/topic/ho-so/",
            "/topic/dong-bo/",
            "/topic/kpi/",
            "/topic/notifications/user/",
            "/topic/feedbacks/user/",
            "/topic/feedbacks/manager/");

    public static boolean duocNghe(String kenh, Long userId, String vaiTro) {
        if (kenh == null || userId == null) return false;
        if (kenh.contains("*") || kenh.contains("{")) return false;

        if (KENH_CHUNG.contains(kenh)) return true;

        Set<AppPermission> quyen = AppPermission.getPermissionsByRole(vaiTro);
        boolean admin = "ADMIN".equals(vaiTro);
        switch (kenh) {
            // Báo có bài đăng / đơn / thực chiến mới chờ duyệt — trang Web Admin
            case "/topic/admin/requests":
                return admin || "VAN_PHONG".equals(vaiTro);
            // Trọn báo cáo thực chiến (tên, SĐT khách, vị trí) — như GET /field-battle/pending
            case "/topic/thuc-chien/admin":
                return admin || quyen.contains(AppPermission.MEETING_APPROVE)
                        || quyen.contains(AppPermission.MEETING_MANAGE);
            case "/topic/admin/attendance":
                return admin || quyen.contains(AppPermission.ATTENDANCE_VIEW_ALL);
            case "/topic/feedbacks/hr":
                return admin || quyen.contains(AppPermission.FEEDBACK_MANAGE);
            default:
                break;
        }

        for (String tienTo : KENH_RIENG) {
            if (kenh.startsWith(tienTo)) {
                return kenh.substring(tienTo.length()).equals(String.valueOf(userId));
            }
        }
        return false;
    }
}
