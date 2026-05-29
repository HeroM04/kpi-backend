package com.trilong.kpibackend.modules.feedback.service;

import com.trilong.kpibackend.modules.feedback.dto.FeedbackResponseDTO;
import com.trilong.kpibackend.modules.feedback.entity.Feedback;
import com.trilong.kpibackend.modules.feedback.repository.FeedbackRepository;
import com.trilong.kpibackend.modules.user.entity.User;
import com.trilong.kpibackend.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public FeedbackResponseDTO createAndBroadcastFeedback(Long senderId, Map<String, Object> request) {
        // 1. Map dá»¯ liá»‡u má»›i bá»• sung
        String title = (String) request.get("title");
        String category = (String) request.get("category");
        Integer rating = request.get("rating") != null ? Integer.valueOf(request.get("rating").toString()) : 5;

        // Default title if empty
        if (title == null || title.trim().isEmpty()) {
            title = category != null ? category : "GÃ³p Ã½ tá»« nhÃ¢n viÃªn";
        }

        // Support both "content" and "message"
        String content = (String) request.get("content");
        if (content == null) {
            content = (String) request.get("message");
        }

        // Default targetType to COMPANY if null/empty
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
                .isAnonymous(request.get("isAnonymous") != null ? (Boolean) request.get("isAnonymous") : false)
                .title(title)
                .category(category)
                .rating(rating)
                .build();

        Feedback savedFeedback = feedbackRepository.saveAndFlush(feedback);
        try { messagingTemplate.convertAndSend("/topic/admin/requests", (Object) java.util.Map.of("type", "FEEDBACK", "message", "Co y kien/gop y moi tu nhan su!")); } catch(Exception e){}
        FeedbackResponseDTO responseDTO = mapToDTO(savedFeedback);

        // 2. PHÃT SÃ“NG REAL-TIME (BROADCAST)
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

    @Transactional(readOnly = true) public List<FeedbackResponseDTO> getAllFeedbacks() {
        List<Feedback> list = feedbackRepository.findAll();
        return list.stream().map(this::mapToDTO).toList();
    }

    @Transactional
    public FeedbackResponseDTO replyFeedback(Long feedbackId, Long adminId, String replyText) {
        Feedback feedback = feedbackRepository.findById(feedbackId)
                .orElseThrow(() -> new IllegalArgumentException("KhÃ´ng tÃ¬m tháº¥y Ã½ kiáº¿n pháº£n há»“i cÃ³ ID: " + feedbackId));

        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new IllegalArgumentException("KhÃ´ng tÃ¬m tháº¥y quáº£n trá»‹ viÃªn"));

        feedback.setAdminReply(replyText);
        feedback.setStatus("RESOLVED");
        feedback.setResolvedBy(admin);
        feedback.setResolvedAt(ZonedDateTime.now());

        Feedback savedFeedback = feedbackRepository.save(feedback);
        try { messagingTemplate.convertAndSend("/topic/admin/requests", (Object) java.util.Map.of("type", "FEEDBACK", "message", "Co y kien/gop y moi tu nhan su!")); } catch(Exception e){}
        FeedbackResponseDTO responseDTO = mapToDTO(savedFeedback);

        // PhÃ¡t tÃ­n hiá»‡u qua WebSocket bÃ¡o cho user gá»­i pháº£n há»“i biáº¿t
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
                    .orElse("NhÃ¢n viÃªn áº©n danh");
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
                .build();
    }

    /**
     * Láº¥y danh sÃ¡ch gÃ³p Ã½ cá»§a chÃ­nh nhÃ¢n viÃªn gá»­i.
     */
    @Transactional(readOnly = true)
    public List<FeedbackResponseDTO> getFeedbacksBySender(Long senderId) {
        List<Feedback> list = feedbackRepository.findBySenderIdOrderByCreatedAtDesc(senderId);
        return list.stream().map(this::mapToDTO).toList();
    }

    /**
     * Láº¥y chi tiáº¿t má»™t gÃ³p Ã½.
     */
    @Transactional(readOnly = true)
    public FeedbackResponseDTO getFeedbackById(Long id) {
        Feedback feedback = feedbackRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("KhÃ´ng tÃ¬m tháº¥y Ã½ kiáº¿n pháº£n há»“i cÃ³ ID: " + id));
        return mapToDTO(feedback);
    }

    /**
     * Cáº­p nháº­t tráº¡ng thÃ¡i gÃ³p Ã½ (Admin/HR).
     */
    @Transactional
    public FeedbackResponseDTO updateStatus(Long id, String status) {
        Feedback feedback = feedbackRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("KhÃ´ng tÃ¬m tháº¥y Ã½ kiáº¿n pháº£n há»“i cÃ³ ID: " + id));
        feedback.setStatus(status);
        Feedback saved = feedbackRepository.save(feedback);
        try { messagingTemplate.convertAndSend("/topic/admin/requests", (Object) java.util.Map.of("type", "FEEDBACK", "message", "Co y kien/gop y moi tu nhan su!")); } catch(Exception e){}
        return mapToDTO(saved);
    }

    /**
     * XÃ³a gÃ³p Ã½ (Admin/HR).
     */
    @Transactional
    public void deleteFeedback(Long id) {
        Feedback feedback = feedbackRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("KhÃ´ng tÃ¬m tháº¥y Ã½ kiáº¿n pháº£n há»“i cÃ³ ID: " + id));
        feedbackRepository.delete(feedback);
    }
}
