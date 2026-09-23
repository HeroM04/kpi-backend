package com.trilong.kpibackend.modules.training.service;

import com.trilong.kpibackend.modules.kpi.service.KpiCalculationService;
import com.trilong.kpibackend.modules.notification.service.PushNotificationService;
import com.trilong.kpibackend.modules.training.dto.OneOnOneTrainingDto;
import com.trilong.kpibackend.modules.training.entity.OneOnOneTraining;
import com.trilong.kpibackend.modules.training.repository.OneOnOneTrainingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Báo cáo đào tạo 1-1: nhân sự nộp, Admin duyệt thì mới cộng điểm.
 *
 * <p>Trước đây nộp là tự duyệt và cộng 5đ ngay — Admin chỉ thấy báo cáo khi
 * điểm đã vào, muốn gỡ thì không có nút nào. Giờ đi cùng đường với Thực chiến:
 * nộp → CHỜ DUYỆT → Admin duyệt (cộng) hoặc từ chối (không cộng; nếu trước đó
 * đã duyệt thì thu hồi).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OneOnOneTrainingService {

    private final OneOnOneTrainingRepository oneOnOneTrainingRepository;
    private final KpiCalculationService kpiCalculationService;
    private final PushNotificationService pushNotificationService;
    private final SimpMessagingTemplate messagingTemplate;

    /** Điểm nhóm Thực chiến cho một buổi đào tạo 1-1 được duyệt. */
    private static final int KPI_POINTS_ONE_ON_ONE = 5;

    @Transactional
    public OneOnOneTrainingDto submitOneOnOneTraining(Long userId, String content, String photoUrl) {
        OneOnOneTraining training = oneOnOneTrainingRepository.save(OneOnOneTraining.builder()
                .userId(userId)
                .content(content)
                .photoUrl(photoUrl)
                .status("PENDING")
                .build());

        // Báo web quản trị có đơn mới để huy hiệu "chờ duyệt" cập nhật ngay
        try {
            messagingTemplate.convertAndSend("/topic/admin/requests",
                    (Object) Map.of("type", "ONE_ON_ONE", "message", "Có báo cáo đào tạo 1-1 mới chờ duyệt"));
        } catch (Exception e) {
            log.debug("[1-1] Không báo được web quản trị: {}", e.getMessage());
        }
        return mapToDto(training);
    }

    public List<OneOnOneTrainingDto> getAllOneOnOneTrainings() {
        return oneOnOneTrainingRepository.findAllWithUser().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    /** Duyệt: cộng 5đ vào đúng tuần nhân sự nộp báo cáo. */
    @Transactional
    public OneOnOneTrainingDto duyet(Long id, Long nguoiDuyet) {
        OneOnOneTraining t = timTheoId(id);
        if ("APPROVED".equals(t.getStatus())) return mapToDto(t);

        t.setStatus("APPROVED");
        t.setReviewedBy(nguoiDuyet);
        t.setReviewedAt(ZonedDateTime.now());
        oneOnOneTrainingRepository.save(t);

        // Neo vào lúc NỘP chứ không phải lúc duyệt: nộp tối Chủ nhật, sáng thứ Hai
        // Admin mới duyệt thì điểm vẫn phải thuộc tuần cũ.
        kpiCalculationService.updateKpiPoints(t.getUserId(), "meeting", KPI_POINTS_ONE_ON_ONE,
                lucNop(t), "Admin duyệt đào tạo 1-1 — " + tomTat(t.getContent()));
        return mapToDto(t);
    }

    /**
     * Từ chối. Báo cáo đang chờ thì chỉ đổi trạng thái; báo cáo đã duyệt (kể cả
     * loại tự duyệt đời cũ) thì thu hồi 5đ đã cộng.
     */
    @Transactional
    public OneOnOneTrainingDto tuChoi(Long id, Long nguoiDuyet) {
        OneOnOneTraining t = timTheoId(id);
        if ("REJECTED".equals(t.getStatus())) return mapToDto(t);

        boolean daCongDiem = "APPROVED".equals(t.getStatus());
        t.setStatus("REJECTED");
        t.setReviewedBy(nguoiDuyet);
        t.setReviewedAt(ZonedDateTime.now());
        oneOnOneTrainingRepository.save(t);

        if (daCongDiem) {
            kpiCalculationService.updateKpiPoints(t.getUserId(), "meeting", -KPI_POINTS_ONE_ON_ONE,
                    lucNop(t), "Admin từ chối đào tạo 1-1 — " + tomTat(t.getContent()));
        } else {
            // Không có dòng điểm nào để nhân sự thấy trong nhật ký, nên phải báo
            // riêng — không thì họ chờ mãi một khoản điểm sẽ không bao giờ tới.
            pushNotificationService.guiToiNhanSu(t.getUserId(), "Báo cáo đào tạo 1-1 bị từ chối",
                    "“" + tomTat(t.getContent()) + "” không được duyệt. Liên hệ Admin nếu cần bổ sung.",
                    Map.of("type", "one_on_one"));
        }
        return mapToDto(t);
    }

    private OneOnOneTraining timTheoId(Long id) {
        return oneOnOneTrainingRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy báo cáo đào tạo 1-1 #" + id));
    }

    private static ZonedDateTime lucNop(OneOnOneTraining t) {
        return t.getSubmittedAt() != null ? t.getSubmittedAt() : ZonedDateTime.now();
    }

    /** Cắt nội dung cho vừa một dòng nhật ký / thông báo. */
    private static String tomTat(String s) {
        if (s == null) return "";
        String gon = s.trim().replaceAll("\\s+", " ");
        return gon.length() <= 60 ? gon : gon.substring(0, 57) + "…";
    }

    private OneOnOneTrainingDto mapToDto(OneOnOneTraining entity) {
        OneOnOneTrainingDto dto = new OneOnOneTrainingDto();
        dto.setId(entity.getId());
        dto.setUserId(entity.getUserId());
        if (entity.getUser() != null) {
            dto.setUserName(entity.getUser().getFullName());
            dto.setUserAvatar(entity.getUser().getAvatarUrl());
        }
        dto.setContent(entity.getContent());
        dto.setPhotoUrl(entity.getPhotoUrl());
        dto.setStatus(entity.getStatus());
        dto.setSubmittedAt(entity.getSubmittedAt() != null ? entity.getSubmittedAt() : ZonedDateTime.now());
        dto.setReviewedAt(entity.getReviewedAt());
        return dto;
    }
}
