package com.trilong.kpibackend.modules.training.service;

import com.trilong.kpibackend.modules.kpi.entity.KpiLedgerEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Thu hồi điểm đào tạo tuần phải trừ đúng phần ĐÃ THỰC VÀO.
 *
 * <p>Tuần 46/2026 hệ thống cộng mặc định "cả tuần công ty không tổ chức buổi đào
 * tạo nào" (thực ra có học, chỉ không lên lịch trên app). 22 người nhóm Phát
 * triển cá nhân sắp đầy trần 30đ nên chỉ vào được 5đ. Khi Admin nhập bù buổi học
 * và thu hồi, những người này chỉ được trừ 5đ chứ không phải 15đ — trừ 15đ là lẹm
 * sang điểm chấm công, đào tạo 1-1 của họ.
 */
class ThuHoiDiemDaoTaoTuanTest {

    private static final String MAC_DINH = "Cả tuần công ty không tổ chức buổi đào tạo nào — cộng mặc định theo quy định";
    private static final int QUY_DINH = -15;

    /** Ghi lần lượt như updateKpiPoints: cộng rồi kẹp 0–30, nhớ điểm thực vào. */
    private static List<KpiLedgerEntry> nhatKy(Object... cap) {
        List<KpiLedgerEntry> ds = new ArrayList<>();
        int diem = 0;
        for (int i = 0; i < cap.length; i += 2) {
            int p = (Integer) cap[i + 1];
            int sau = Math.max(0, Math.min(30, diem + p));
            KpiLedgerEntry e = new KpiLedgerEntry();
            e.setReason((String) cap[i]);
            e.setPoints(p);
            e.setEffectivePoints(sau - diem);
            ds.add(e);
            diem = sau;
        }
        return ds;
    }

    private static int diemCuoi(List<KpiLedgerEntry> ds) {
        return ds.stream().mapToInt(KpiLedgerEntry::getEffectivePoints).sum();
    }

    @Test
    @DisplayName("Nhóm sắp đầy, chỉ vào được 5đ → thu hồi đúng 5đ")
    void chiVao5Diem() {
        var ds = nhatKy("Chấm công đúng giờ", 25, MAC_DINH, 15);
        assertEquals(30, diemCuoi(ds));
        assertEquals(5, ds.get(1).getEffectivePoints());
        assertEquals(-5, TrainingService.tinhMucThuHoi(ds, 30, QUY_DINH));
    }

    @Test
    @DisplayName("Được cộng đủ 15đ → thu hồi 15đ")
    void vaoDu15Diem() {
        var ds = nhatKy("Chấm công đúng giờ", 10, MAC_DINH, 15);
        assertEquals(-15, TrainingService.tinhMucThuHoi(ds, 25, QUY_DINH));
    }

    @Test
    @DisplayName("Điểm khác từng bị trần nuốt vì khoản đào tạo thì được tính lại")
    void diemBiNuotDuocTraLai() {
        // 25 + 15 (vào 5) = 30, sau đó đào tạo 1-1 +5 bị nuốt (vào 0)
        var ds = nhatKy("Chấm công", 25, MAC_DINH, 15, "Đào tạo 1-1", 5);
        assertEquals(30, diemCuoi(ds));
        // Bỏ khoản mặc định: 25 + 5 = 30 → không phải trừ gì
        assertEquals(0, TrainingService.tinhMucThuHoi(ds, 30, QUY_DINH));
    }

    @Test
    @DisplayName("Nhật ký không khớp điểm hiện tại → trừ bằng tổng điểm thực vào của dòng đào tạo")
    void nhatKyKhongKhop() {
        var ds = nhatKy("Chấm công", 25, MAC_DINH, 15);   // nhật ký ra 30
        assertEquals(-5, TrainingService.tinhMucThuHoi(ds, 28, QUY_DINH));
    }

    @Test
    @DisplayName("Không bao giờ trừ quá mức quy định")
    void khongTruQuaQuyDinh() {
        var ds = nhatKy("Chấm công", 10, MAC_DINH, 15,
                "Trả lại điểm bị trừ nhầm khi chấm đào tạo tuần sớm — điểm đào tạo chỉ chốt vào tối Chủ nhật", 5);
        assertEquals(30, diemCuoi(ds));
        assertEquals(QUY_DINH, TrainingService.tinhMucThuHoi(ds, 30, QUY_DINH));
    }

    @Test
    @DisplayName("Không có khoản đào tạo nào đã vào → không trừ")
    void khongCoGiDeTru() {
        var ds = nhatKy("Chấm công", 20);
        assertEquals(0, TrainingService.tinhMucThuHoi(ds, 20, QUY_DINH));
    }
}
