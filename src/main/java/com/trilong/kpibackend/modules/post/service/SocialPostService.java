package com.trilong.kpibackend.modules.post.service;

import com.trilong.kpibackend.modules.kpi.service.KpiCalculationService;
import com.trilong.kpibackend.modules.post.dto.SubmitPostDTO;
import com.trilong.kpibackend.modules.post.entity.SocialPost;
import com.trilong.kpibackend.modules.post.repository.SocialPostRepository;
import com.trilong.kpibackend.modules.user.entity.User;
import com.trilong.kpibackend.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SocialPostService {

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;

    private final SocialPostRepository socialPostRepository;
    private final UserRepository userRepository;
    private final KpiCalculationService kpiCalculationService;

    private static final int KPI_POINTS_POST = 5; // +5 Ä‘iá»ƒm má»—i bÃ i viáº¿t MXH Ä‘Æ°á»£c duyá»‡t

    @Transactional
    public SocialPost submitPost(Long userId, SubmitPostDTO dto) {
        try { messagingTemplate.convertAndSend("/topic/admin/requests", (Object) java.util.Map.of("type", "POST", "message", "Co yeu cau bai post moi!")); } catch(Exception e){}
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("KhÃ´ng tÃ¬m tháº¥y ngÆ°á»i dÃ¹ng"));

        SocialPost post = SocialPost.builder()
                .user(user)
                .platform(dto.getPlatform())
                .link(dto.getLink())
                .caption(dto.getCaption())
                .screenshotUrl(dto.getScreenshotUrl())
                .status("PENDING")
                .build();

        return socialPostRepository.save(post);
    }

    public List<SocialPost> getMyPosts(Long userId) {
        return socialPostRepository.findByUserIdOrderBySubmittedAtDesc(userId);
    }

    public List<SocialPost> getPostsByStatus(String status) {
        return socialPostRepository.findByStatusOrderBySubmittedAtDesc(status);
    }

    public List<SocialPost> getAllPosts() {
        return socialPostRepository.findAllByOrderBySubmittedAtDesc();
    }

    public SocialPost getPostById(Long postId) {
        return socialPostRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("KhÃ´ng tÃ¬m tháº¥y bÃ i viáº¿t MXH cÃ³ ID: " + postId));
    }


    @Transactional
    public SocialPost approvePost(Long postId, Long approvedById) {
        SocialPost post = socialPostRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("KhÃ´ng tÃ¬m tháº¥y bÃ i viáº¿t MXH cÃ³ ID: " + postId));

        User approver = userRepository.findById(approvedById)
                .orElseThrow(() -> new IllegalArgumentException("KhÃ´ng tÃ¬m tháº¥y ngÆ°á»i duyá»‡t"));

        if ("APPROVED".equals(post.getStatus())) {
            return post;
        }

        post.setStatus("APPROVED");
        post.setApprovedBy(approver);
        post.setApprovedAt(ZonedDateTime.now());
        SocialPost savedPost = socialPostRepository.save(post);

        // Cá»™ng Ä‘iá»ƒm KPI tuáº§n gá»­i yÃªu cáº§u
        kpiCalculationService.updateKpiPoints(post.getUser().getId(), "post", KPI_POINTS_POST, post.getSubmittedAt());

        return savedPost;
    }

    @Transactional
    public SocialPost rejectPost(Long postId, Long approvedById) {
        SocialPost post = socialPostRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("KhÃ´ng tÃ¬m tháº¥y bÃ i viáº¿t MXH cÃ³ ID: " + postId));

        User approver = userRepository.findById(approvedById)
                .orElseThrow(() -> new IllegalArgumentException("KhÃ´ng tÃ¬m tháº¥y ngÆ°á»i duyá»‡t"));

        // Náº¿u Ä‘ang á»Ÿ tráº¡ng thÃ¡i APPROVED, pháº£i trá»« Ä‘iá»ƒm KPI trÆ°á»›c khi reject
        if ("APPROVED".equals(post.getStatus())) {
            kpiCalculationService.updateKpiPoints(post.getUser().getId(), "post", -KPI_POINTS_POST, post.getSubmittedAt());
        }

        post.setStatus("REJECTED");
        post.setApprovedBy(approver);
        post.setApprovedAt(ZonedDateTime.now());

        return socialPostRepository.save(post);
    }

    @Transactional
    public void deletePost(Long postId) {
        SocialPost post = socialPostRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("KhÃ´ng tÃ¬m tháº¥y bÃ i viáº¿t MXH cÃ³ ID: " + postId));

        // Náº¿u Ä‘ang á»Ÿ tráº¡ng thÃ¡i APPROVED, pháº£i trá»« Ä‘iá»ƒm KPI trÆ°á»›c khi xÃ³a
        if ("APPROVED".equals(post.getStatus())) {
            kpiCalculationService.updateKpiPoints(post.getUser().getId(), "post", -KPI_POINTS_POST, post.getSubmittedAt());
        }

        socialPostRepository.delete(post);
    }
}
