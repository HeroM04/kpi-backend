package com.trilong.kpibackend.core.utils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * "Ngày" theo giờ Việt Nam.
 *
 * <p>Máy chủ Render chạy UTC, mốc thời gian đọc từ DB cũng mang múi UTC. Lấy
 * thẳng {@code toLocalDate()} hay {@code LocalDate.now()} là lấy ngày UTC — lệch
 * 7 tiếng: bài đăng lúc 06:00 sáng 23/09 giờ VN (22/09 23:00 UTC) hiện dưới ngày
 * 22/09 trong lịch sử của app, và từ 00:00 tới 07:00 sáng "hôm nay" vẫn là hôm
 * qua. Mọi chỗ cần NGÀY để lọc hay hiển thị cho người dùng đi qua lớp này.
 */
public final class GioVN {

    public static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private GioVN() {}

    /** Hôm nay theo giờ Việt Nam. */
    public static LocalDate homNay() {
        return LocalDate.now(VN);
    }

    /** Ngày (giờ VN) của một mốc thời gian có múi giờ. */
    public static LocalDate ngayCua(ZonedDateTime t) {
        return t == null ? null : t.withZoneSameInstant(VN).toLocalDate();
    }

    /**
     * Ngày (giờ VN) của một mốc KHÔNG có múi giờ. {@code @CreationTimestamp}
     * kiểu LocalDateTime ghi theo giờ của máy chạy (UTC trên Render, giờ VN trên
     * máy dev), nên quy từ giờ máy sang giờ VN — đúng ở cả hai nơi.
     */
    public static LocalDate ngayCua(LocalDateTime t) {
        return t == null ? null : t.atZone(ZoneId.systemDefault()).withZoneSameInstant(VN).toLocalDate();
    }

    /** Ngày trên tham số ?date=yyyy-MM-dd; bỏ trống thì là hôm nay (giờ VN). */
    public static LocalDate ngayLoc(String date) {
        return (date == null || date.isBlank()) ? homNay() : LocalDate.parse(date.trim());
    }
}
