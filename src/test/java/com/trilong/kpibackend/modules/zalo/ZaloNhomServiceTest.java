package com.trilong.kpibackend.modules.zalo;

import com.trilong.kpibackend.modules.battle.entity.FieldBattle;
import com.trilong.kpibackend.modules.training.entity.OneOnOneTraining;
import com.trilong.kpibackend.modules.user.entity.Department;
import com.trilong.kpibackend.modules.user.entity.User;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** Tin đăng nhóm Zalo khi duyệt Thực chiến / Đào tạo 1-1. */
class ZaloNhomServiceTest {

    private static User nguoi() {
        return User.builder().fullName("Nguyễn Văn A")
                .department(Department.builder().name("Phòng Kinh doanh 1").build()).build();
    }

    private static final ZonedDateTime LUC = ZonedDateTime.of(2026, 11, 25, 2, 5, 0, 0, ZoneId.of("UTC"));

    @Test
    void thucChien_coTenPhongDuAnNoiDung_khongCoSoDienThoaiKhach() {
        FieldBattle b = FieldBattle.builder().user(nguoi()).battleType("MEETING")
                .project("Sun Feliza").customerName("Anh Tuấn").customerPhone("0912345678")
                .location("Tố Hữu, Nam Từ Liêm").content("Dẫn khách xem căn mẫu tầng 12")
                .submittedAt(LUC).build();

        String tin = ZaloNhomService.noiDungThucChien(b);

        assertThat(tin).startsWith("THỰC CHIẾN — Gặp khách")
                .contains("Nguyễn Văn A · Phòng Kinh doanh 1")
                .contains("Dự án: Sun Feliza")
                .contains("Khách: Anh Tuấn")
                .contains("Địa điểm: Tố Hữu, Nam Từ Liêm")
                .contains("Thời gian: 09:05 25/11/2026")   // giờ Việt Nam
                .endsWith("Dẫn khách xem căn mẫu tầng 12")
                .doesNotContain("0912345678");
    }

    @Test
    void thucChien_hoTro_vaBoQuaDongTrong() {
        FieldBattle b = FieldBattle.builder().user(nguoi()).battleType("SUPPORT")
                .project("X").content("Hỗ trợ đồng nghiệp").build();

        String tin = ZaloNhomService.noiDungThucChien(b);

        assertThat(tin).startsWith("THỰC CHIẾN — Hỗ trợ khách")
                .doesNotContain("Khách:").doesNotContain("Địa điểm:").doesNotContain("Thời gian:");
    }

    @Test
    void daoTao_coTenVaNoiDung() {
        OneOnOneTraining t = OneOnOneTraining.builder().user(nguoi())
                .content("Kèm bạn B kỹ năng chốt khách").submittedAt(LUC).build();

        assertThat(ZaloNhomService.noiDungDaoTao(t))
                .startsWith("ĐÀO TẠO 1-1\nNguyễn Văn A · Phòng Kinh doanh 1")
                .endsWith("Kèm bạn B kỹ năng chốt khách");
    }

    @Test
    void noiDungDai_catVua2000KyTu() {
        String tin = ZaloNhomService.cat("a".repeat(5000));
        assertThat(tin).hasSize(ZaloNhomService.CHU_TOI_DA).endsWith("…");
    }

    @Test
    void chuaCaiBot_thiKhongGuiGiVaKhongLoi() {
        ZaloNhomService zalo = new ZaloNhomService("", "", "");
        assertThat(zalo.coBot()).isFalse();
        assertThatCode(() -> {
            zalo.thucChienDaDuyet(FieldBattle.builder().user(nguoi()).build());
            zalo.daoTaoDaDuyet(OneOnOneTraining.builder().user(nguoi()).build());
        }).doesNotThrowAnyException();
    }

    @Test
    void coBotNhungChuaChonNhom_thiKhongGui() {
        // Không có chat_id nhóm → bỏ qua ngay, không gọi Zalo
        ZaloNhomService zalo = new ZaloNhomService("tok", "", "");
        assertThatCode(() -> zalo.thucChienDaDuyet(FieldBattle.builder().user(nguoi()).build()))
                .doesNotThrowAnyException();
    }

    @Test
    void loiMang_cheToken() {
        ZaloNhomService zalo = new ZaloNhomService("123:bi-mat", "", "");
        assertThat(zalo.an("I/O error on POST request for \"https://bot-api.zaloplatforms.com/bot123:bi-mat/sendMessage\""))
                .doesNotContain("bi-mat").contains("/bot***/sendMessage");
    }
}
