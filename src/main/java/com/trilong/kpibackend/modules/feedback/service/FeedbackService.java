package com.trilong.kpibackend.modules.feedback.service;

import com.trilong.kpibackend.core.service.CloudinaryService;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackService {

    /** Số ảnh đính kèm tối đa cho một góp ý. */
    public static final int TOI_DA_ANH = 5;

    private static final String KHONG_TIM_THAY = "Không tìm thấy góp ý có mã ";

    private final FeedbackRepository feedbackRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final CloudinaryService cloudinary;

    /** Góp ý không kèm ảnh (app cũ gửi JSON). */
    @Transactional
    public FeedbackResponseDTO createAndBroadcastFeedback(Long senderId, Map<String, Object> request) {
        return luuVaPhat(senderId, request, List.of());
    }

    /**
     * Góp ý kèm ảnh chụp màn hình, gửi một lượt cùng nội dung.
     *
     * <p>Ảnh lên Cloudinary như ảnh chấm công, thực chiến; DB chỉ giữ link. Ảnh
     * mang tên ngẫu nhiên nên góp ý ẩn danh không lộ người gửi qua link ảnh.
     * Lưu góp ý hỏng thì xóa những ảnh vừa đưa lên, không để ảnh mồ côi.
     */
    public FeedbackResponseDTO createWithImages(Long senderId, Map<String, Object> request,
                                                List<MultipartFile> anh) throws java.io.IOException {
        List<MultipartFile> coNoiDung = anh == null ? List.of()
                : anh.stream().filter(f -> f != null && !f.isEmpty()).toList();
        if (coNoiDung.size() > TOI_DA_ANH) {
            throw new IllegalArgumentException("Tối đa " + TOI_DA_ANH + " ảnh cho một góp ý.");
        }

        List<String> link = new ArrayList<>();
        try {
            for (MultipartFile f : coNoiDung) {
                link.add(cloudinary.uploadImage(f));
            }
            return luuVaPhat(senderId, request, link);
        } catch (RuntimeException | java.io.IOException e) {
            xoaAnh(link);
            throw e;
        }
    }

    /** Xóa ảnh trên Cloudinary theo link; lỗi thì bỏ qua (CloudinaryService tự ghi log). */
    private void xoaAnh(List<String> link) {
        for (String url : link) {
            String publicId = CloudinaryService.publicIdTuUrl(url);
            if (publicId != null) cloudinary.deleteImage(publicId);
        }
    }

    private FeedbackResponseDTO luuVaPhat(Long senderId, Map<String, Object> request, List<String> linkAnh) {
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
        if ((content == null || content.isBlank()) && !linkAnh.isEmpty()) {
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
                .imageUrls(ghepLink(linkAnh))
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
                .createdAt(com.trilong.kpibackend.core.utils.GioVN.coMuiGio(f.getCreatedAt()))
                .title(f.getTitle())
                .category(f.getCategory())
                .rating(f.getRating())
                .adminReply(f.getAdminReply())
                .resolvedAt(f.getResolvedAt())
                .resolvedById(f.getResolvedBy() != null ? f.getResolvedBy().getId() : null)
                .resolvedByFullName(f.getResolvedBy() != null ? f.getResolvedBy().getFullName() : null)
                .imageUrls(tachLink(f.getImageUrls()))
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

    /** Xóa góp ý (Admin/HR), ảnh đính kèm trên Cloudinary xóa theo sau khi DB đã xóa xong. */
    @Transactional
    public void deleteFeedback(Long id) {
        Feedback feedback = feedbackRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(KHONG_TIM_THAY + id));
        List<String> anh = tachLink(feedback.getImageUrls());
        feedbackRepository.delete(feedback);

        if (anh.isEmpty()) return;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() { xoaAnh(anh); }
            });
        } else {
            xoaAnh(anh);
        }
    }

    // ── Phần thuần ───────────────────────────────────────────────────────────

    static List<String> tachLink(String s) {
        if (s == null || s.isBlank()) return List.of();
        return Arrays.stream(s.split(",")).map(String::trim).filter(k -> !k.isEmpty()).toList();
    }

    static String ghepLink(List<String> link) {
        return link == null || link.isEmpty() ? null : String.join(",", link);
    }

    /** Form multipart gửi "true"/"false" dạng chữ, JSON gửi boolean. */
    static boolean laDung(Object v) {
        if (v instanceof Boolean b) return b;
        return v != null && "true".equalsIgnoreCase(v.toString().trim());
    }
}
