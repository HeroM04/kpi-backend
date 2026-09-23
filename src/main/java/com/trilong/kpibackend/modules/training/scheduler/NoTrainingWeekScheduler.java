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
 * nhiên đủ điều kiện — không ai có cơ hội học nên không ai bị mất điểm; khoản
 * mặc định này chỉ cộng khi TUẦN ĐÃ KHÉP, không cộng trước từ đầu tuần.
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
