package com.trilong.kpibackend.core.utils;

import java.util.Map;

/**
 * Lý do Admin từ chối một yêu cầu nhân sự gửi lên (chấm công ngoại tuyến, xin
 * vắng, thực chiến, bài đăng / video lan tỏa, đào tạo 1-1, chốt căn, gieo hạt,
 * xin không tham gia đào tạo).
 *
 * <p>Bắt buộc ghi (yêu cầu 08/11/2026): nhân sự mở Lịch sử trên app, lọc các
 * yêu cầu bị từ chối và bấm vào là thấy vì sao — trước đây nhiều loại chỉ hiện
 * "Từ chối" trơn, không biết sai ở đâu để nộp lại cho đúng.
 */
public final class LyDoTuChoi {

    public static final int DAI_TOI_DA = 500;
    public static final String THIEU = "Nhập lý do từ chối để nhân sự biết vì sao bị từ chối.";

    private LyDoTuChoi() {
    }

    /** Lý do đã cắt khoảng trắng; trống hoặc quá dài thì báo lỗi cho Admin. */
    public static String batBuoc(String lyDo) {
        String s = lyDo == null ? "" : lyDo.trim();
        if (s.isEmpty()) throw new IllegalArgumentException(THIEU);
        if (s.length() > DAI_TOI_DA) {
            throw new IllegalArgumentException("Lý do từ chối dài quá " + DAI_TOI_DA + " ký tự, ghi ngắn lại.");
        }
        return s;
    }

    /** Lý do trong thân yêu cầu JSON: khóa "reason" (web mới), "lyDo" hoặc "note" (đời cũ). */
    public static String tuThan(Map<String, ?> than) {
        if (than == null) return null;
        for (String k : new String[]{"reason", "lyDo", "note"}) {
            Object v = than.get(k);
            if (v != null) return v.toString();
        }
        return null;
    }
}
