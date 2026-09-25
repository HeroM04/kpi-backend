package com.trilong.kpibackend.core.dongbo;

import com.trilong.kpibackend.modules.attendance.entity.CheckinLog;
import com.trilong.kpibackend.modules.attendance.entity.LeaveRequest;
import com.trilong.kpibackend.modules.auth.entity.RefreshToken;
import com.trilong.kpibackend.modules.battle.entity.FieldBattle;
import com.trilong.kpibackend.modules.deal.entity.Deal;
import com.trilong.kpibackend.modules.feedback.entity.Feedback;
import com.trilong.kpibackend.modules.kpi.entity.KpiLedgerEntry;
import com.trilong.kpibackend.modules.post.entity.SocialPost;
import com.trilong.kpibackend.modules.training.entity.OneOnOneTraining;
import com.trilong.kpibackend.modules.training.entity.TrainingRsvp;
import com.trilong.kpibackend.modules.training.entity.TrainingSession;
import com.trilong.kpibackend.modules.user.entity.Department;
import com.trilong.kpibackend.modules.user.entity.ReferralSubmission;
import com.trilong.kpibackend.modules.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bảng "thực thể nào báo cho ai": gửi nhầm người là app người khác tải lại vô
 * cớ còn chủ nhân thì không thấy gì; sai tên loại là app coi là "loại lạ" và bỏ qua.
 * Tên loại phải khớp {@code DongBo.cacLoai} bên app (test dong_bo_test.dart kiểm phía kia).
 */
class DongBoPhanLoaiTest {

    private final User u7 = User.builder().id(7L).build();

    @Test
    @DisplayName("Dữ liệu riêng của một người → kênh của đúng người đó, đúng loại")
    void duLieuRieng() {
        CheckinLog c = new CheckinLog(); c.setUserId(7L);
        LeaveRequest l = new LeaveRequest(); l.setUserId(7L);
        Feedback f = new Feedback(); f.setSenderId(7L);
        ReferralSubmission r = new ReferralSubmission(); r.setReferrerId(7L);
        TrainingRsvp rsvp = new TrainingRsvp(); rsvp.setUserId(7L);

        assertThat(DongBoListener.phanLoai(c)).isEqualTo(new DongBoListener.DiaChi("CHAM_CONG", 7L));
        assertThat(DongBoListener.phanLoai(l)).isEqualTo(new DongBoListener.DiaChi("DON_VANG", 7L));
        assertThat(DongBoListener.phanLoai(FieldBattle.builder().user(u7).build())).isEqualTo(new DongBoListener.DiaChi("THUC_CHIEN", 7L));
        assertThat(DongBoListener.phanLoai(Deal.builder().user(u7).build())).isEqualTo(new DongBoListener.DiaChi("CHOT_CAN", 7L));
        assertThat(DongBoListener.phanLoai(SocialPost.builder().user(u7).build())).isEqualTo(new DongBoListener.DiaChi("BAI_DANG", 7L));
        assertThat(DongBoListener.phanLoai(f)).isEqualTo(new DongBoListener.DiaChi("PHAN_HOI", 7L));
        assertThat(DongBoListener.phanLoai(r)).isEqualTo(new DongBoListener.DiaChi("GIEO_HAT", 7L));
        assertThat(DongBoListener.phanLoai(OneOnOneTraining.builder().userId(7L).build())).isEqualTo(new DongBoListener.DiaChi("DAO_TAO", 7L));
        assertThat(DongBoListener.phanLoai(rsvp)).isEqualTo(new DongBoListener.DiaChi("DAO_TAO", 7L));
    }

    @Test
    @DisplayName("Dữ liệu chung → gửi cả công ty (userId null)")
    void duLieuChung() {
        assertThat(DongBoListener.phanLoai(TrainingSession.builder().build())).isEqualTo(new DongBoListener.DiaChi("DAO_TAO", null));
        assertThat(DongBoListener.phanLoai(Department.builder().build())).isEqualTo(new DongBoListener.DiaChi("HO_SO", null));
    }

    @Test
    @DisplayName("Thực thể có kênh riêng hoặc app không hiển thị → không báo")
    void khongBao() {
        assertThat(DongBoListener.phanLoai(new KpiLedgerEntry())).isNull();
        assertThat(DongBoListener.phanLoai(RefreshToken.builder().build())).isNull();
        assertThat(DongBoListener.phanLoai(u7)).isNull();   // hồ sơ đi kênh /topic/ho-so riêng
        assertThat(DongBoListener.phanLoai("bất kỳ")).isNull();
    }

    @Test
    @DisplayName("Bản ghi riêng mà thiếu chủ → không báo (không được lọt sang kênh cả công ty)")
    void nguoiRong() {
        assertThat(DongBoListener.phanLoai(FieldBattle.builder().build())).isNull();
        assertThat(DongBoListener.phanLoai(new CheckinLog())).isNull();
    }
}
