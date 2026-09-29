package com.trilong.kpibackend.modules.feedback.service;

import com.trilong.kpibackend.core.storage.AnhRiengTuService;
import com.trilong.kpibackend.modules.feedback.dto.FeedbackResponseDTO;
import com.trilong.kpibackend.modules.feedback.entity.Feedback;
import com.trilong.kpibackend.modules.feedback.repository.FeedbackRepository;
import com.trilong.kpibackend.modules.user.entity.User;
import com.trilong.kpibackend.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackService {

    /** Số ảnh đính kèm tối đa cho một góp ý. */
    public static final int TOI_DA_ANH = 5;

    /** Thư mục trên S3 chứa ảnh góp ý. */
    static final String THU_MUC_ANH = "feedback";

    private static final String KHONG_TIM_THAY = "Không tìm thấy góp ý có mã ";

    private final FeedbackRepository feedbackRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final AnhRiengTuService anhRiengTu;

    /** Góp ý không kèm ảnh (app cũ gửi JSON). */
    @Transactional
    public FeedbackResponseDTO createAndBroadcastFeedback(Long senderId, Map<String, Object> request) {
        return luuVaPhat(senderId, request, List.of());
    }

    /**
     * Góp ý kèm ảnh chụp màn hình, gửi một lượt cùng nội dung.
     *
     * <p>Ảnh lên S3 trước, lưu góp ý sau. Lưu hỏng thì xóa những ảnh vừa đưa lên,
     * không để ảnh mồ côi trong kho.
     */
    public FeedbackResponseDTO createWithImages(Long senderId, Map<String, Object> request,
                                                List<MultipartFile> anh) throws java.io.IOException {
        List<MultipartFile> coNoiDung = anh == null ? List.of()
                : anh.stream().filter(f -> f != null && !f.isEmpty()).toList();
        if (coNoiDung.size() > TOI_DA_ANH) {
            throw new IllegalArgumentException("Tối đa " + TOI_DA_ANH + " ảnh cho một góp ý.");
        }

        List<String> khoa = new ArrayList<>();
        try {
            for (MultipartFile f : coNoiDung) {
                khoa.add(anhRiengTu.taiLen(f, THU_MUC_ANH));
            }
            return luuVaPhat(senderId, request, khoa);
        } catch (RuntimeException | java.io.IOException e) {
            khoa.forEach(anhRiengTu::xoa);
            throw e;
        }
    }

    private FeedbackResponseDTO luuVaPhat(Long senderId, Map<String, Object> request, List<String> khoaAnh) {
        String title = (String) request.get("title");
        String category = (String) request.get("category");
        Integer rating = request.get("rating") != null ? Integer.valueOf(request.get("rating").toString()) : 5;

        if (title == null || title.trim().isEmpty()) {
            title = category != null ? category : "Góp ý từ nhân viên";
        }

        // Nhận cả "content" lẫn "message"
        String content = (String) request.get("content");
        if (content == null) {
            content = (String) request.get("message");
        }
        if ((content == null || content.isBlank()) && !khoaAnh.isEmpty()) {
            content = "(Xem ảnh đính kèm)";
        }

        String fbTargetType = (String) request.get("targetType");
        if (fbTargetType == null || fbTargetType.trim().isEmpty()) {
            fbTargetType = "COMPANY";
        }

        Feedback feedback = Feedback.builder()
                .senderId(senderId)
                .targetType(fbTargetType)
                .targetId(request.get("targetId") != null ? Long.valueOf(request.get("targetId").toString()) : null)
                .content(content)
                .status("UNREAD")
                .isAnonymous(laDung(request.get("isAnonymous")))
                .title(title)
                .category(category)
                .rating(rating)
                .imageKeys(ghepKhoa(khoaAnh))
                .build();

        Feedback savedFeedback = feedbackRepository.saveAndFlush(feedback);
        try { messagingTemplate.convertAndSend("/topic/admin/requests", (Object) Map.of("type", "FEEDBACK", "message", "Co y kien/gop y moi tu nhan su!")); } catch (Exception e) {}
        FeedbackResponseDTO responseDTO = mapToDTO(savedFeedback);

        // Phát thời gian thực cho màn hình quản lý góp ý
        Map<String, Object> payload = Map.of(
                "id", responseDTO.getId(),
                "title", responseDTO.getTitle() != null ? responseDTO.getTitle() : "",
                "category", responseDTO.getCategory() != null ? responseDTO.getCategory() : "",
                "rating", responseDTO.getRating(),
                "content", responseDTO.getContent(),
                "targetType", responseDTO.getTargetType(),
                "status", responseDTO.getStatus(),
                "createdAt", responseDTO.getCreatedAt() != null ? responseDTO.getCreatedAt().toString() : ""
        );

        String targetType = savedFeedback.getTargetType();
        if ("HR".equals(targetType) || "COMPANY".equals(targetType)) {
            messagingTemplate.convertAndSend("/topic/feedbacks/hr", (Object) payload);
        } else if ("MANAGER".equals(targetType)) {
            Long managerId = savedFeedback.getTargetId();
            messagingTemplate.convertAndSend("/topic/feedbacks/manager/" + managerId, (Object) payload);
        }

        return responseDTO;
    }

    @Transactional(readOnly = true)
    public List<FeedbackResponseDTO> getAllFeedbacks() {
        List<Feedback> list = feedbackRepository.findAll();
        return list.stream().map(this::mapToDTO).toList();
    }

    @Transactional
    public FeedbackResponseDTO replyFeedback(Long feedbackId, Long adminId, String replyText) {
        Feedback feedback = feedbackRepository.findById(feedbackId)
                .orElseThrow(() -> new IllegalArgumentException(KHONG_TIM_THAY + feedbackId));

        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản quản trị"));

        feedback.setAdminReply(replyText);
        feedback.setStatus("RESOLVED");
        feedback.setResolvedBy(admin);
        feedback.setResolvedAt(ZonedDateTime.now());

        Feedback savedFeedback = feedbackRepository.save(feedback);
        try { messagingTemplate.convertAndSend("/topic/admin/requests", (Object) Map.of("type", "FEEDBACK", "message", "Co y kien/gop y moi tu nhan su!")); } catch (Exception e) {}
        FeedbackResponseDTO responseDTO = mapToDTO(savedFeedback);

        // Báo cho người gửi biết góp ý đã được trả lời
        if (feedback.getSenderId() != null) {
            messagingTemplate.convertAndSend("/topic/feedbacks/user/" + feedback.getSenderId(), (Object) Map.of(
                    "id", responseDTO.getId(),
                    "status", responseDTO.getStatus(),
                    "adminReply", responseDTO.getAdminReply(),
                    "resolvedAt", responseDTO.getResolvedAt().toString()
            ));
        }

        return responseDTO;
    }

    public FeedbackResponseDTO mapToDTO(Feedback f) {
        String senderName = null;
        if (!f.isAnonymous() && f.getSenderId() != null) {
            senderName = userRepository.findById(f.getSenderId())
                    .map(User::getFullName)
                    .orElse("Nhân viên ẩn danh");
        }

        return FeedbackResponseDTO.builder()
                .id(f.getId())
                .senderId(f.isAnonymous() ? null : f.getSenderId())
                .senderFullName(senderName)
                .targetType(f.getTargetType())
                .targetId(f.getTargetId())
                .content(f.getContent())
                .status(f.getStatus())
                .isAnonymous(f.isAnonymous())
                .createdAt(f.getCreatedAt())
                .title(f.getTitle())
                .category(f.getCategory())
                .rating(f.getRating())
                .adminReply(f.getAdminReply())
                .resolvedAt(f.getResolvedAt())
                .resolvedById(f.getResolvedBy() != null ? f.getResolvedBy().getId() : null)
                .resolvedByFullName(f.getResolvedBy() != null ? f.getResolvedBy().getFullName() : null)
                .imageUrls(tachKhoa(f.getImageKeys()).stream()
                        .map(anhRiengTu::linkXem).filter(Objects::nonNull).toList())
                .build();
    }

    /** Góp ý của chính người đang đăng nhập. */
    @Transactional(readOnly = true)
    public List<FeedbackResponseDTO> getFeedbacksBySender(Long senderId) {
        List<Feedback> list = feedbackRepository.findBySenderIdOrderByCreatedAtDesc(senderId);
        return list.stream().map(this::mapToDTO).toList();
    }

    @Transactional(readOnly = true)
    public FeedbackResponseDTO getFeedbackById(Long id) {
        Feedback feedback = feedbackRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(KHONG_TIM_THAY + id));
        return mapToDTO(feedback);
    }

    /** Đổi trạng thái góp ý (Admin/HR). */
    @Transactional
    public FeedbackResponseDTO updateStatus(Long id, String status) {
        Feedback feedback = feedbackRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(KHONG_TIM_THAY + id));
        feedback.setStatus(status);
        Feedback saved = feedbackRepository.save(feedback);
        try { messagingTemplate.convertAndSend("/topic/admin/requests", (Object) Map.of("type", "FEEDBACK", "message", "Co y kien/gop y moi tu nhan su!")); } catch (Exception e) {}
        return mapToDTO(saved);
    }

    /** Xóa góp ý (Admin/HR), ảnh đính kèm trên S3 xóa theo sau khi DB đã xóa xong. */
    @Transactional
    public void deleteFeedback(Long id) {
        Feedback feedback = feedbackRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(KHONG_TIM_THAY + id));
        List<String> anh = tachKhoa(feedback.getImageKeys());
        feedbackRepository.delete(feedback);

        if (anh.isEmpty()) return;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() { anh.forEach(anhRiengTu::xoa); }
            });
        } else {
            anh.forEach(anhRiengTu::xoa);
        }
    }

    // ── Phần thuần ───────────────────────────────────────────────────────────

    static List<String> tachKhoa(String s) {
        if (s == null || s.isBlank()) return List.of();
        return Arrays.stream(s.split(",")).map(String::trim).filter(k -> !k.isEmpty()).toList();
    }

    static String ghepKhoa(List<String> khoa) {
        return khoa == null || khoa.isEmpty() ? null : String.join(",", khoa);
    }

    /** Form multipart gửi "true"/"false" dạng chữ, JSON gửi boolean. */
    static boolean laDung(Object v) {
        if (v instanceof Boolean b) return b;
        return v != null && "true".equalsIgnoreCase(v.toString().trim());
    }
}
