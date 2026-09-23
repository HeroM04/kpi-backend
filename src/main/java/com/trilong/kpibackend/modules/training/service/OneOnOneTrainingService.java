package com.trilong.kpibackend.modules.training.service;

import com.trilong.kpibackend.modules.kpi.entity.KpiLedgerEntry;
import com.trilong.kpibackend.modules.kpi.repository.KpiLedgerEntryRepository;
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
    private final KpiLedgerEntryRepository kpiLedgerEntryRepository;
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

    /** Kết quả chuyển báo cáo tự duyệt về chờ duyệt, để báo lại cho Admin. */
    public record KetQuaChuyenVeChoDuyet(int soBaoCao, int soNguoi, int tongDiemHoan, int khongTimThayNhatKy) {}

    /** Diễn giải mà bản tự duyệt cũ ghi vào nhật ký — dùng để tìm lại khoản đã cộng. */
    private static final String DIEN_GIAI_TU_DUYET_CU = "Báo cáo đào tạo 1-1";

    /**
     * Đưa mọi báo cáo 1-1 được TỰ DUYỆT theo cách cũ về CHỜ DUYỆT, hoàn lại điểm
     * đã cộng, để Admin xét lại từng báo cáo.
     *
     * <p><b>Hoàn đúng số điểm đã thực vào, không phải cứ 5đ.</b> Nhóm Thực chiến
     * có trần 40đ/tuần: ai đã đầy nhóm thì lần tự duyệt ấy thực ra cộng 0đ. Trừ
     * đại 5đ là ăn vào điểm họ kiếm được từ gặp khách. Nhật ký điểm có ghi số
     * thực nhận của từng khoản, nên ghép từng báo cáo với dòng nhật ký của nó
     * (cùng người, cùng diễn giải, thời điểm gần nhất trong vòng 2 phút) rồi hoàn
     * đúng con số đó. Ghép từng cặp một, nên hai báo cáo nộp cách nhau vài giây
     * vẫn không dùng chung một dòng.
     *
     * <p>Không tìm được dòng nhật ký (hiếm — nhật ký chỉ hỏng khi có lỗi lúc ghi)
     * thì hoàn 5đ theo quy định; điểm tuần không xuống dưới 0 nên không âm.
     *
     * <p>Chạy lại lần hai không làm gì: báo cáo đã về CHỜ DUYỆT không còn khớp
     * điều kiện "tự duyệt".
     */
    @Transactional
    public KetQuaChuyenVeChoDuyet chuyenBaoCaoTuDuyetVeChoDuyet() {
        List<OneOnOneTraining> tuDuyet = oneOnOneTrainingRepository
                .findByStatusAndReviewedByIsNullOrderBySubmittedAtAsc("APPROVED");

        int tongHoan = 0, khongCoNhatKy = 0;
        java.util.Set<Long> nguoi = new java.util.HashSet<>();
        Map<Long, List<KpiLedgerEntry>> nhatKyTheoNguoi = new java.util.HashMap<>();
        java.util.Set<Long> dongDaDung = new java.util.HashSet<>();

        for (OneOnOneTraining t : tuDuyet) {
            nguoi.add(t.getUserId());
            List<KpiLedgerEntry> nhatKy = nhatKyTheoNguoi.computeIfAbsent(t.getUserId(), uid ->
                    kpiLedgerEntryRepository.findByUserIdAndCategoryAndReason(uid, "meeting", DIEN_GIAI_TU_DUYET_CU));

            KpiLedgerEntry khop = ghepNhatKy(t, nhatKy, dongDaDung);
            int hoan;
            ZonedDateTime moc;
            if (khop != null) {
                dongDaDung.add(khop.getId());
                hoan = khop.getEffectivePoints() == null ? 0 : khop.getEffectivePoints();
                moc = khop.getOccurredAt();   // cùng tuần với khoản đã cộng
            } else {
                khongCoNhatKy++;
                hoan = KPI_POINTS_ONE_ON_ONE;
                moc = lucNop(t);
            }

            t.setStatus("PENDING");
            oneOnOneTrainingRepository.save(t);

            if (hoan > 0) {
                kpiCalculationService.updateKpiPoints(t.getUserId(), "meeting", -hoan, moc,
                        "Báo cáo đào tạo 1-1 “" + tomTat(t.getContent()) + "” chuyển về chờ Admin duyệt"
                                + " — hoàn lại " + hoan + "đ đã tự cộng. Duyệt xong sẽ cộng lại.");
                tongHoan += hoan;
            }
        }

        log.info("[1-1] Chuyển {} báo cáo tự duyệt về chờ duyệt ({} người), hoàn {}đ, {} báo cáo không có nhật ký",
                tuDuyet.size(), nguoi.size(), tongHoan, khongCoNhatKy);
        return new KetQuaChuyenVeChoDuyet(tuDuyet.size(), nguoi.size(), tongHoan, khongCoNhatKy);
    }

    /** Dòng nhật ký chưa dùng, gần thời điểm nộp nhất, cách không quá 2 phút. */
    static KpiLedgerEntry ghepNhatKy(OneOnOneTraining t, List<KpiLedgerEntry> nhatKy, java.util.Set<Long> daDung) {
        if (t.getSubmittedAt() == null) return null;
        long nop = t.getSubmittedAt().toInstant().toEpochMilli();
        KpiLedgerEntry tot = null;
        long lechNhoNhat = 120_000;
        for (KpiLedgerEntry e : nhatKy) {
            if (daDung.contains(e.getId()) || e.getOccurredAt() == null) continue;
            long lech = Math.abs(e.getOccurredAt().toInstant().toEpochMilli() - nop);
            if (lech <= lechNhoNhat) { lechNhoNhat = lech; tot = e; }
        }
        return tot;
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
