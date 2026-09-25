package com.trilong.kpibackend.modules.kpi.service;

import com.trilong.kpibackend.modules.kpi.entity.KpiLedgerEntry;
import com.trilong.kpibackend.modules.kpi.entity.KpiScore;
import com.trilong.kpibackend.modules.kpi.entity.KpiWeeklyScore;
import com.trilong.kpibackend.modules.kpi.repository.KpiLedgerEntryRepository;
import com.trilong.kpibackend.modules.kpi.repository.KpiScoreRepository;
import com.trilong.kpibackend.modules.kpi.repository.KpiWeeklyScoreRepository;
import com.trilong.kpibackend.modules.notification.service.PushNotificationService;
import com.trilong.kpibackend.modules.user.entity.User;
import com.trilong.kpibackend.modules.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Lõi chấm điểm KPI: tuần/tháng nào, trần mỗi nhóm, sàn 0đ, ai được chấm,
 * và xếp loại tháng. Mọi nguồn điểm (chấm công, thực chiến, bài đăng, đào tạo,
 * vắng mặt…) đều đi qua đây, nên sai một chỗ là sai điểm cả công ty.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KpiCalculationServiceTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    @Mock KpiScoreRepository kpiScoreRepository;
    @Mock KpiWeeklyScoreRepository kpiWeeklyScoreRepository;
    @Mock KpiLedgerEntryRepository kpiLedgerEntryRepository;
    @Mock UserRepository userRepository;
    @Mock SimpMessagingTemplate messagingTemplate;
    @Mock PushNotificationService pushNotificationService;
    @InjectMocks KpiCalculationService service;

    private User nguoi(String vaiTro) {
        return User.builder().id(7L).fullName("Nhân sự thử").phoneNumber("0900000000").passwordHash("x").role(vaiTro).build();
    }

    // ── Tuần và tháng ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Tuần và tháng KPI")
    class TuanThang {

        @Test
        @DisplayName("Tuần tính theo giờ Việt Nam: 01:00 sáng thứ Hai VN (còn là Chủ nhật ở UTC) thuộc tuần MỚI")
        void tuanTheoGioVietNam() {
            // Thứ Hai 21/09/2026 01:00 giờ VN = Chủ nhật 20/09 18:00 UTC.
            // Máy chủ Render chạy UTC nên mốc thời gian đọc từ DB mang múi UTC.
            ZonedDateTime utc = ZonedDateTime.of(2026, 9, 21, 1, 0, 0, 0, VN).withZoneSameInstant(ZoneOffset.UTC);
            assertThat(utc.getDayOfWeek().name()).isEqualTo("SUNDAY");

            assertThat(service.getWeekString(utc)).isEqualTo("2026-W39");
        }

        @Test
        @DisplayName("Tuần và tháng luôn cùng một múi giờ — không có dòng điểm tuần của tháng này mà mang nhãn tháng kia")
        void tuanVaThangKhop() {
            // Thứ Hai 07/09/2026 03:00 VN — tuần đầu tháng 9, nhưng vẫn là Chủ nhật 06/09 ở UTC
            ZonedDateTime utc = ZonedDateTime.of(2026, 9, 7, 3, 0, 0, 0, VN).withZoneSameInstant(ZoneOffset.UTC);
            assertThat(service.extractMonth(utc)).isEqualTo("2026-09");
            assertThat(service.getWeeksOfMonth("2026-09")).contains(service.getWeekString(utc));
        }

        @Test
        @DisplayName("Một tuần thuộc trọn tháng chứa ngày thứ Hai của nó")
        void tuanThuocThangCuaThuHai() {
            // Tuần 31/08–06/09/2026: thứ Hai 31/08 → cả tuần tính cho tháng 8
            assertThat(service.extractMonth(ZonedDateTime.of(2026, 9, 6, 20, 0, 0, 0, VN))).isEqualTo("2026-08");
            assertThat(service.extractMonth(ZonedDateTime.of(2026, 9, 7, 8, 0, 0, 0, VN))).isEqualTo("2026-09");
        }

        @Test
        @DisplayName("Chỉ tiêu tháng = số thứ Hai × 100 (tháng 4 tuần 400đ, 5 tuần 500đ)")
        void chiTieuThang() {
            assertThat(service.getMaxKpiForMonth("2026-09")).isEqualTo(400); // 7, 14, 21, 28
            assertThat(service.getMaxKpiForMonth("2026-08")).isEqualTo(500); // 3, 10, 17, 24, 31
            assertThat(service.getMaxKpiForMonth("khong-hop-le")).isEqualTo(400);
        }

        @Test
        @DisplayName("Danh sách tuần của tháng khớp chỉ tiêu tháng")
        void tuanCuaThang() {
            assertThat(service.getWeeksOfMonth("2026-09")).containsExactly("2026-W37", "2026-W38", "2026-W39", "2026-W40");
            assertThat(service.getWeeksOfMonth("2026-08")).hasSize(5);
        }
    }

    // ── Xếp loại tháng ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("Xếp loại KPI tháng")
    class XepLoai {

        @Test
        @DisplayName("Tháng 4 tuần: 250 → 50%, 320 → 100%, dưới 250 → không đạt")
        void thang4Tuan() {
            assertThat(service.gradeMonth(249, "2026-09", false, null).percent()).isEqualTo(0);
            assertThat(service.gradeMonth(250, "2026-09", false, null).percent()).isEqualTo(50);
            assertThat(service.gradeMonth(319, "2026-09", false, null).percent()).isEqualTo(50);
            assertThat(service.gradeMonth(320, "2026-09", false, null).percent()).isEqualTo(100);
        }

        @Test
        @DisplayName("Tháng 5 tuần: 310 → 50%, 400 → 100%")
        void thang5Tuan() {
            assertThat(service.gradeMonth(309, "2026-08", false, null).percent()).isEqualTo(0);
            assertThat(service.gradeMonth(310, "2026-08", false, null).percent()).isEqualTo(50);
            assertThat(service.gradeMonth(400, "2026-08", false, null).percent()).isEqualTo(100);
        }

        @Test
        @DisplayName("Chốt căn → đạt 100% bất kể điểm")
        void chotCan() {
            assertThat(service.gradeMonth(0, "2026-09", true, null).percent()).isEqualTo(100);
        }

        @Test
        @DisplayName("Nhân sự mới (2 tháng đầu) chỉ cần 80% ngưỡng: 200 → 50%, 256 → 100%")
        void nhanSuMoi() {
            ZonedDateTime vao = ZonedDateTime.of(2026, 9, 1, 9, 0, 0, 0, VN); // thuộc tuần tháng 8
            var g = service.gradeMonth(256, "2026-09", false, vao);
            assertThat(g.isNewbie()).isTrue();
            assertThat(g.min50()).isEqualTo(200);
            assertThat(g.min100()).isEqualTo(256);
            assertThat(g.percent()).isEqualTo(100);
        }

        @Test
        @DisplayName("Từ tháng thứ 3 là nhân sự chính thức")
        void hetMoi() {
            ZonedDateTime vao = ZonedDateTime.of(2026, 7, 6, 9, 0, 0, 0, VN);
            assertThat(service.isNewStaff(vao, "2026-08")).isTrue();
            assertThat(service.isNewStaff(vao, "2026-09")).isFalse();
            assertThat(service.isNewStaff(null, "2026-09")).isFalse();
        }
    }

    // ── Chốt căn → 100% ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Chốt căn hiện 100% trên web/app (khớp báo cáo Excel)")
    class ChotCan100 {

        @Mock com.trilong.kpibackend.modules.deal.repository.DealRepository dealRepository;

        private com.trilong.kpibackend.modules.deal.entity.Deal deal(long userId, ZonedDateTime nop) {
            return com.trilong.kpibackend.modules.deal.entity.Deal.builder()
                    .user(User.builder().id(userId).build()).status("APPROVED").submittedAt(nop).build();
        }

        @BeforeEach
        void ganRepo() {
            service = new KpiCalculationService(kpiScoreRepository, kpiWeeklyScoreRepository, kpiLedgerEntryRepository,
                    userRepository, messagingTemplate, pushNotificationService, dealRepository);
        }

        @Test
        @DisplayName("Người có chốt căn đã duyệt trong tháng được nhận ra, theo ngày nộp giờ VN")
        void nhanRa() {
            when(dealRepository.findByStatusOrderBySubmittedAtDesc("APPROVED")).thenReturn(List.of(
                    deal(7L, ZonedDateTime.of(2026, 9, 15, 10, 0, 0, 0, VN)),
                    // Chủ nhật 06/09 thuộc tuần của thứ Hai 31/08 → tháng 8, không tính vào tháng 9
                    deal(8L, ZonedDateTime.of(2026, 9, 6, 20, 0, 0, 0, VN))));

            assertThat(service.nguoiCoChotCan("2026-09")).containsExactly(7L);
            assertThat(service.nguoiCoChotCan("2026-08")).containsExactly(8L);
        }

        @Test
        @DisplayName("Có chốt căn → điểm hiển thị bằng chỉ tiêu tháng và deal > 0, dù bảng điểm deal = 0")
        void hienThi100() {
            KpiScore diem = KpiScore.builder().user(nguoi("SALE")).month("2026-09")
                    .attendance(20).meeting(10).post(0).deal(0).total(30).build();

            var co = com.trilong.kpibackend.modules.kpi.dto.KpiScoreResponseDTO.from(diem, 30, 400, true);
            var khong = com.trilong.kpibackend.modules.kpi.dto.KpiScoreResponseDTO.from(diem, 30, 400, false);

            assertThat(co.getTotal()).isEqualTo(400);
            assertThat(co.getDeal()).isPositive();
            assertThat(co.getWeeklyTotal()).isEqualTo(100);
            assertThat(khong.getTotal()).isEqualTo(30);
            assertThat(khong.getDeal()).isZero();
            // Điểm ba nhóm vẫn giữ nguyên để xem chi tiết
            assertThat(co.getAttendance()).isEqualTo(20);
        }
    }

    // ── Số điểm thực đã cộng (để gỡ đúng số) ────────────────────────────────

    @Nested
    @DisplayName("Tra số điểm một khoản đã duyệt thực cộng được")
    class DiemThucDaCong {

        private final ZonedDateTime nop = ZonedDateTime.of(2026, 9, 23, 10, 0, 0, 0, VN);
        private long id = 1;

        private KpiLedgerEntry dong(ZonedDateTime luc, int thucNhan) {
            KpiLedgerEntry e = new KpiLedgerEntry();
            e.setId(id++);
            e.setOccurredAt(luc);
            e.setEffectivePoints(thucNhan);
            return e;
        }

        private void nhatKy(KpiLedgerEntry... ds) {
            when(kpiLedgerEntryRepository.findByUserIdAndCategoryAndReasonStartingWithOrderByIdAsc(7L, "meeting", "Admin duyệt thực chiến"))
                    .thenReturn(List.of(ds));
        }

        @Test
        @DisplayName("Duyệt lúc nhóm đã đầy → trả 0 (gỡ thì không trừ gì)")
        void daDay() {
            nhatKy(dong(nop, 0));
            assertThat(service.diemThucDaCong(7L, "meeting", "Admin duyệt thực chiến", nop, 10)).isZero();
        }

        @Test
        @DisplayName("Duyệt → gỡ → duyệt lại: lấy lần duyệt sau cùng")
        void layLanSauCung() {
            nhatKy(dong(nop, 10), dong(nop, 4));
            assertThat(service.diemThucDaCong(7L, "meeting", "Admin duyệt thực chiến", nop, 10)).isEqualTo(4);
        }

        @Test
        @DisplayName("Không lẫn với báo cáo nộp lúc khác")
        void khongLan() {
            nhatKy(dong(nop.plusMinutes(30), 10), dong(nop, 3));
            assertThat(service.diemThucDaCong(7L, "meeting", "Admin duyệt thực chiến", nop, 10)).isEqualTo(3);
        }

        @Test
        @DisplayName("Không có dòng nhật ký → dùng số quy định")
        void khongCoNhatKy() {
            nhatKy();
            assertThat(service.diemThucDaCong(7L, "meeting", "Admin duyệt thực chiến", nop, 10)).isEqualTo(10);
        }
    }

    // ── Cộng/trừ điểm ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Cộng / trừ điểm")
    class CongTru {

        private KpiWeeklyScore tuan;

        @BeforeEach
        void chuanBi() {
            tuan = KpiWeeklyScore.builder().attendance(0).meeting(0).post(0).total(0).week("2026-W39").month("2026-09").build();
            when(kpiScoreRepository.findByUserIdAndMonth(anyLong(), anyString())).thenReturn(Optional.empty());
            when(kpiScoreRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(kpiWeeklyScoreRepository.findByUserIdAndWeek(anyLong(), anyString())).thenReturn(Optional.of(tuan));
            when(kpiWeeklyScoreRepository.findByUserIdAndMonth(anyLong(), anyString())).thenAnswer(i -> List.of(tuan));
        }

        private ZonedDateTime trongTuan39() {
            return ZonedDateTime.of(2026, 9, 23, 12, 0, 0, 0, VN);
        }

        private KpiLedgerEntry dongNhatKyCuoi() {
            ArgumentCaptor<KpiLedgerEntry> c = ArgumentCaptor.forClass(KpiLedgerEntry.class);
            verify(kpiLedgerEntryRepository, atLeastOnce()).save(c.capture());
            return c.getValue();
        }

        @Test
        @DisplayName("Văn phòng / Admin không bị chấm: không lưu gì, không ghi nhật ký, không báo")
        void khongChamVanPhong() {
            when(userRepository.findById(7L)).thenReturn(Optional.of(nguoi("VAN_PHONG")));

            assertThat(service.updateKpiPoints(7L, "attendance", 15, trongTuan39(), "Chấm công")).isNull();

            verify(kpiScoreRepository, never()).save(any());
            verify(kpiLedgerEntryRepository, never()).save(any());
            verify(pushNotificationService, never()).guiToiNhanSu(any(), any(), any(), any());
        }

        @Test
        @DisplayName("Nhóm Phát triển cá nhân kẹp trần 30đ/tuần; nhật ký ghi đúng số thực nhận")
        void tranPhatTrienCaNhan() {
            when(userRepository.findById(7L)).thenReturn(Optional.of(nguoi("SALE")));
            tuan.setAttendance(20);

            service.updateKpiPoints(7L, "attendance", 15, trongTuan39(), "Đào tạo");

            assertThat(tuan.getAttendance()).isEqualTo(30);
            KpiLedgerEntry dong = dongNhatKyCuoi();
            assertThat(dong.getPoints()).isEqualTo(15);
            assertThat(dong.getEffectivePoints()).isEqualTo(10);
        }

        @Test
        @DisplayName("Trần mỗi nhóm: Thực chiến 40, Lan tỏa 30")
        void tranCacNhom() {
            when(userRepository.findById(7L)).thenReturn(Optional.of(nguoi("SALE")));
            service.updateKpiPoints(7L, "meeting", 100, trongTuan39(), "Gặp khách");
            service.updateKpiPoints(7L, "post", 100, trongTuan39(), "Bài đăng");
            assertThat(tuan.getMeeting()).isEqualTo(40);
            assertThat(tuan.getPost()).isEqualTo(30);
            assertThat(tuan.getTotal()).isEqualTo(70);
        }

        @Test
        @DisplayName("Sàn 0đ: đang 0 mà bị trừ thì vẫn 0, nhật ký ghi thực nhận 0")
        void sanKhong() {
            when(userRepository.findById(7L)).thenReturn(Optional.of(nguoi("SALE")));

            service.updateKpiPoints(7L, "attendance", -15, trongTuan39(), "Vắng không phép");

            assertThat(tuan.getAttendance()).isZero();
            assertThat(dongNhatKyCuoi().getEffectivePoints()).isZero();
            // Không báo "bị trừ" khi thực tế không trừ được gì
            verify(pushNotificationService, never()).guiToiNhanSu(any(), any(), any(), any());
        }

        @Test
        @DisplayName("Điểm thực đổi thì báo đẩy cho nhân sự, kèm lý do")
        void baoKhiDoi() {
            when(userRepository.findById(7L)).thenReturn(Optional.of(nguoi("TRUONG_PHONG")));
            service.updateKpiPoints(7L, "meeting", 5, trongTuan39(), "Tăng ca — về lúc 22:17");
            verify(pushNotificationService).guiToiNhanSu(eq(7L), eq("Bạn được cộng 5đ KPI"),
                    eq("Tăng ca — về lúc 22:17"), any());
        }

        @Test
        @DisplayName("Chốt căn cộng riêng, không vào điểm tuần, không xuống dưới 0")
        void chotCanRieng() {
            when(userRepository.findById(7L)).thenReturn(Optional.of(nguoi("SALE")));
            KpiScore thang = service.updateKpiPoints(7L, "deal", -3, trongTuan39(), "Admin gỡ chốt căn");
            assertThat(thang.getDeal()).isZero();
            verify(kpiWeeklyScoreRepository, never()).save(any());
        }

        @Test
        @DisplayName("Tổng tháng = tổng các tuần, không vượt chỉ tiêu tháng")
        void tongThang() {
            when(userRepository.findById(7L)).thenReturn(Optional.of(nguoi("SALE")));
            KpiWeeklyScore t1 = KpiWeeklyScore.builder().attendance(30).meeting(40).post(30).total(100).build();
            KpiWeeklyScore t2 = KpiWeeklyScore.builder().attendance(30).meeting(40).post(30).total(100).build();
            KpiWeeklyScore t3 = KpiWeeklyScore.builder().attendance(30).meeting(40).post(30).total(100).build();
            KpiWeeklyScore t4 = KpiWeeklyScore.builder().attendance(30).meeting(40).post(30).total(100).build();
            KpiWeeklyScore thua = KpiWeeklyScore.builder().attendance(30).meeting(40).post(30).total(100).build();
            when(kpiWeeklyScoreRepository.findByUserIdAndMonth(anyLong(), anyString())).thenReturn(List.of(t1, t2, t3, t4, thua));

            KpiScore thang = service.updateKpiPoints(7L, "post", 1, trongTuan39(), "Bài đăng");

            assertThat(thang.getTotal()).isEqualTo(400); // tháng 9/2026 chỉ 4 tuần
        }

        @Test
        @DisplayName("Loại điểm lạ → báo lỗi rõ ràng, không lặng lẽ bỏ qua")
        void loaiLa() {
            when(userRepository.findById(7L)).thenReturn(Optional.of(nguoi("SALE")));
            org.assertj.core.api.Assertions.assertThatThrownBy(
                    () -> service.updateKpiPoints(7L, "khong_co", 5, trongTuan39(), "x"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
