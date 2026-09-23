package com.trilong.kpibackend.modules.training.service;

import com.trilong.kpibackend.modules.kpi.entity.KpiLedgerEntry;
import com.trilong.kpibackend.modules.training.entity.OneOnOneTraining;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Ghép báo cáo 1-1 tự duyệt với đúng dòng nhật ký điểm của nó, để hoàn lại
 * đúng số điểm đã thực cộng.
 *
 * <p>Ghép sai thì hoàn sai: lấy nhầm dòng của báo cáo khác là hai báo cáo cùng
 * trỏ một dòng, một cái bị bỏ sót hoặc hoàn trùng.
 */
class OneOnOneTrainingServiceTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private OneOnOneTraining baoCao(long id, ZonedDateTime nop) {
        return OneOnOneTraining.builder().id(id).userId(7L).content("cách học dự án").submittedAt(nop).build();
    }

    private KpiLedgerEntry dong(long id, ZonedDateTime luc, int thucNhan) {
        KpiLedgerEntry e = new KpiLedgerEntry();
        e.setId(id);
        e.setUserId(7L);
        e.setCategory("meeting");
        e.setPoints(5);
        e.setEffectivePoints(thucNhan);
        e.setReason("Báo cáo đào tạo 1-1");
        e.setOccurredAt(luc);
        return e;
    }

    private ZonedDateTime luc(int gio, int phut, int giay) {
        return ZonedDateTime.of(2026, 9, 23, gio, phut, giay, 0, VN);
    }

    @Test
    @DisplayName("Hai báo cáo nộp cách nhau 4 giây — mỗi cái ghép đúng dòng của nó, không dùng chung")
    void haiBaoCaoSatNhau() {
        // Nguyễn Tiến Hợp nộp 09:00:22 và 09:00:26
        var bc1 = baoCao(1, luc(9, 0, 22));
        var bc2 = baoCao(2, luc(9, 0, 26));
        List<KpiLedgerEntry> nhatKy = List.of(dong(501, luc(9, 0, 22), 5), dong(502, luc(9, 0, 26), 5));
        Set<Long> daDung = new HashSet<>();

        var k1 = OneOnOneTrainingService.ghepNhatKy(bc1, nhatKy, daDung);
        daDung.add(k1.getId());
        var k2 = OneOnOneTrainingService.ghepNhatKy(bc2, nhatKy, daDung);

        assertEquals(501L, k1.getId());
        assertEquals(502L, k2.getId());
    }

    @Test
    @DisplayName("Khoản bị chặn trần (thực nhận 0đ) vẫn ghép được — để hoàn 0đ chứ không trừ đại 5đ")
    void khoanBiChanTran() {
        var bc = baoCao(1, luc(18, 29, 8));
        var k = OneOnOneTrainingService.ghepNhatKy(bc, List.of(dong(601, luc(18, 29, 8), 0)), new HashSet<>());
        assertNotNull(k);
        assertEquals(0, k.getEffectivePoints());
    }

    @Test
    @DisplayName("Dòng nhật ký cách quá 2 phút thì không phải của báo cáo này")
    void quaXaKhongGhep() {
        var bc = baoCao(1, luc(11, 54, 54));
        assertNull(OneOnOneTrainingService.ghepNhatKy(bc, List.of(dong(701, luc(11, 51, 12), 5)), new HashSet<>()));
    }

    @Test
    @DisplayName("Chọn dòng gần thời điểm nộp nhất khi có nhiều dòng trong khoảng")
    void chonDongGanNhat() {
        var bc = baoCao(1, luc(11, 51, 12));
        List<KpiLedgerEntry> nhatKy = List.of(dong(801, luc(11, 50, 0), 5), dong(802, luc(11, 51, 12), 5));
        assertEquals(802L, OneOnOneTrainingService.ghepNhatKy(bc, nhatKy, new HashSet<>()).getId());
    }
}
