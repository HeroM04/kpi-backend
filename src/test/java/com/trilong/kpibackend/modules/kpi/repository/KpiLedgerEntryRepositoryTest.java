package com.trilong.kpibackend.modules.kpi.repository;

import com.trilong.kpibackend.modules.kpi.entity.KpiLedgerEntry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Thứ tự nhật ký điểm trên màn Thông báo — chạy truy vấn thật trên H2. Mỗi
 * test chạy trong một giao dịch, xong là cuộn lại nên không để rác cho test khác.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class KpiLedgerEntryRepositoryTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Long NGUOI = 7L;

    @Autowired
    private KpiLedgerEntryRepository repo;

    private static ZonedDateTime luc(int gio, int phut) {
        return ZonedDateTime.of(2026, 11, 4, gio, phut, 0, 0, VN);
    }

    private KpiLedgerEntry ghi(String lyDo, int diem, ZonedDateTime phatSinh, ZonedDateTime ghiLuc) {
        KpiLedgerEntry e = new KpiLedgerEntry();
        e.setUserId(NGUOI);
        e.setCategory("attendance");
        e.setPoints(diem);
        e.setEffectivePoints(diem);
        e.setReason(lyDo);
        e.setWeek("2026-W45");
        e.setMonth("2026-11");
        e.setOccurredAt(phatSinh);
        e.setCreatedAt(ghiLuc);
        return repo.save(e);
    }

    /**
     * Đúng chuyện 04/11: chấm công 08:52 bị trừ đi muộn, sau đó Admin thu hồi
     * lượt đó rồi thêm chấm công tay 08:41. Hai khoản sửa sai mang mốc phát sinh
     * của lượt chấm công cũ — xếp theo mốc ấy thì "thêm tay 08:41" tụt xuống
     * dưới cùng còn "đi muộn" với "thu hồi" cùng phút 08:52 đứng lộn xộn.
     */
    @Test
    void khoanGhiSauDungTren_duMangMocPhatSinhCu() {
        ghi("Đi muộn ca sáng — chấm công lúc 08:52 (hạn 08:45)", -5, luc(8, 52), luc(8, 52));
        ghi("Admin thu hồi duyệt chấm công lúc 08:52", 5, luc(8, 52), luc(9, 10));
        ghi("Admin thêm chấm công tay — Đi làm đúng giờ — chấm công lúc 08:41", 5, luc(8, 41), luc(9, 12));

        assertThat(repo.findByUserIdAndWeekOrderByCreatedAtDescIdDesc(NGUOI, "2026-W45"))
                .extracting(KpiLedgerEntry::getReason)
                .containsExactly(
                        "Admin thêm chấm công tay — Đi làm đúng giờ — chấm công lúc 08:41",
                        "Admin thu hồi duyệt chấm công lúc 08:52",
                        "Đi muộn ca sáng — chấm công lúc 08:52 (hạn 08:45)");
        assertThat(repo.findByUserIdAndMonthOrderByCreatedAtDescIdDesc(NGUOI, "2026-11"))
                .extracting(KpiLedgerEntry::getReason)
                .startsWith("Admin thêm chấm công tay — Đi làm đúng giờ — chấm công lúc 08:41");
    }

    @Test
    void ghiCungMotLuc_khoanGhiSauDungTren() {
        ZonedDateTime cungLuc = luc(17, 30);
        ghi("Khoản ghi trước", 5, cungLuc, cungLuc);
        ghi("Khoản ghi sau", 5, cungLuc, cungLuc);

        assertThat(repo.findByUserIdAndWeekOrderByCreatedAtDescIdDesc(NGUOI, "2026-W45"))
                .extracting(KpiLedgerEntry::getReason)
                .containsExactly("Khoản ghi sau", "Khoản ghi trước");
    }
}
