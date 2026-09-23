package com.trilong.kpibackend.modules.training.scheduler;

import com.trilong.kpibackend.modules.training.service.TrainingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.WeekFields;

/**
 * Chốt điểm đào tạo của tuần cho toàn bộ nhân sự.
 *
 * <p>Quy định: dự đủ mọi buổi đào tạo nhóm bắt buộc trong tuần thì được 15đ,
 * thiếu một buổi là không được gì. Tuần công ty không tổ chức buổi nào thì mặc
 * nhiên đủ điều kiện — không ai có cơ hội học nên không ai bị mất điểm. Mọi
 * khoản điểm đào tạo tuần chỉ chốt khi TUẦN ĐÃ KHÉP; giữa tuần không cộng gì.
 *
 * <p>Việc chấm điểm nằm hết trong {@link TrainingService#chamDiemDaoTaoTuan},
 * và nó chạy lại bao nhiêu lần cũng ra cùng kết quả. Lớp này chỉ có nhiệm vụ
 * gọi lại vào hai thời điểm mà không có sự kiện nào khác kích hoạt:
 *
 * <ul>
 *   <li><b>Cuối mỗi ngày</b> — buổi học vừa kết thúc trong ngày thì đến lúc này
 *       mới tính là đã bỏ lỡ. Không có mốc này thì ai vắng buổi chiều nay vẫn
 *       giữ nguyên điểm cho tới cuối tuần.</li>
 *   <li><b>Tối Chủ nhật</b> — chốt lại lần cuối cho tuần vừa khép.</li>
 * </ul>
 *
 * <p>Lưu ý vận hành: máy chủ gói Render miễn phí tự ngủ khi vắng người dùng, tác
 * vụ hẹn giờ lúc nửa đêm có thể không chạy. Vì vậy lượt chạy hằng đêm chấm lại
 * <b>cả tuần trước</b>: lỡ đêm Chủ nhật máy chủ ngủ thì tuần ấy vẫn được chốt ở
 * đêm sau, thay vì treo mãi không ai được khoản mặc định.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NoTrainingWeekScheduler {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final TrainingService trainingService;

    /** Cuối mỗi ngày: buổi học vừa xong hôm nay giờ mới tính là đã bỏ lỡ. */
    @Scheduled(cron = "0 55 23 * * *", zone = "Asia/Ho_Chi_Minh")
    public void chamLaiCuoiNgay() {
        ZonedDateTime bayGio = ZonedDateTime.now(VN_ZONE);
        chamLai("cuối ngày", bayGio);
        // Chốt bù tuần trước nếu đêm Chủ nhật máy chủ đang ngủ. Không có gì đổi
        // thì hàm chấm không ghi dòng nào, nên chạy thừa cũng vô hại.
        chamLai("chốt bù tuần trước", bayGio.minusDays(7));
    }

    /**
     * Ngay khi máy chủ khởi động: gỡ sạch điểm đào tạo tuần đã cộng sớm của tuần
     * đang chạy, theo quy định "cuối tuần mới chốt".
     *
     * <p>Tuần 39/2026 có khoản +15 "công ty không tổ chức đào tạo" cộng từ đầu
     * tuần dù thứ Tư có buổi học. Chạy lúc khởi động để deploy xong là gỡ luôn,
     * không phải chờ tới 23:55. Chỉ đụng tuần ĐANG CHẠY và chỉ dòng của cơ chế
     * đào tạo tuần — điểm đào tạo 1-1 (nhóm Thực chiến) không liên quan. Lần
     * khởi động sau không làm gì vì điểm đã khớp. Chạy nền để không làm chậm
     * lúc máy chủ thức dậy.
     */
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    @org.springframework.scheduling.annotation.Async
    public void goDiemCongSomKhiKhoiDong() {
        try {
            var kq = trainingService.goDiemDaoTaoTuanDangChay();
            if (kq.soNguoi() > 0) {
                log.info("[Đào tạo] Khởi động: gỡ điểm đào tạo tuần {} cộng sớm cho {} người (thu hồi {}đ, trả lại {}đ).",
                        kq.tuan(), kq.soNguoi(), kq.tongDiemThuHoi(), kq.tongDiemTraLai());
            }
        } catch (Exception e) {
            log.error("[Đào tạo] Lỗi khi gỡ điểm đào tạo tuần cộng sớm: {}", e.getMessage(), e);
        }
    }

    /** Tối Chủ nhật: chốt lần cuối cho tuần vừa khép lại. */
    @Scheduled(cron = "0 45 23 * * SUN", zone = "Asia/Ho_Chi_Minh")
    public void chotTuan() {
        chamLai("chốt tuần", ZonedDateTime.now(VN_ZONE));
    }

    private void chamLai(String moc, ZonedDateTime trongTuan) {
        try {
            int n = trainingService.chamDiemDaoTaoTuanChoTatCa(trongTuan);
            log.info("[Đào tạo] Chấm lại điểm tuần ({}) cho {} nhân sự.", moc, n);
        } catch (Exception e) {
            log.error("[Đào tạo] Lỗi khi chấm điểm đào tạo tuần ({}): {}", moc, e.getMessage(), e);
        }
    }

    /**
     * Chấm lại điểm đào tạo cho tuần chứa một ngày bất kỳ.
     * Giữ lại để gọi tay khi cần chấm bù một tuần cũ.
     */
    public int chamLaiTuanChua(LocalDate ngayBatKy) {
        LocalDate thuHai = ngayBatKy.with(WeekFields.ISO.dayOfWeek(), 1);
        return trainingService.chamDiemDaoTaoTuanChoTatCa(
                thuHai.plusDays(2).atTime(12, 0).atZone(VN_ZONE));
    }
}
