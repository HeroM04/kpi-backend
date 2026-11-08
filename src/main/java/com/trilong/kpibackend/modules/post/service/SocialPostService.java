package com.trilong.kpibackend.modules.post.service;

import com.trilong.kpibackend.core.utils.LyDoTuChoi;
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

        // Link và caption không bắt buộc (story, video xây kênh thường không có
        // link); bằng chứng bắt buộc duy nhất là ẢNH CHỤP MÀN HÌNH bài đăng.
        // Admin nhập tay trên web (form không có ô ảnh) thì không bắt.
        boolean laAdmin = "ADMIN".equals(user.getRole());
        if (!laAdmin && (dto.getScreenshotUrl() == null || dto.getScreenshotUrl().isBlank())) {
            throw new IllegalArgumentException("Cần ảnh chụp màn hình bài đăng để Admin đối chiếu.");
        }

        // Facebook / TikTok bắt buộc dán link bài; Zalo không có link công khai
        // nên không bắt (yêu cầu 08/11/2026). Admin nhập tay trên web thì không bắt.
        String nenTang = chuanHoaNenTang(dto.getPlatform());
        String link = dto.getLink() == null ? "" : dto.getLink().trim();
        if (!laAdmin && canLink(nenTang) && link.isEmpty()) {
            throw new IllegalArgumentException("Bài trên " + tenNenTang(nenTang)
                    + " cần dán link bài đăng để Admin mở ra kiểm tra.");
        }
        if (!link.isEmpty() && !link.toLowerCase().startsWith("http")) {
            throw new IllegalArgumentException("Link phải bắt đầu bằng http hoặc https.");
        }

        SocialPost post = SocialPost.builder()
                .user(user)
                .platform(nenTang)
                .contentType(normalizeContentType(dto.getContentType()))
                // Cột link trong CSDL đang NOT NULL (ddl-auto: update không gỡ ràng
                // buộc cũ) → không có link thì lưu chuỗi rỗng
                .link(link)
                .caption(dto.getCaption())
                .screenshotUrl(dto.getScreenshotUrl())
                .status("PENDING")
                .build();

        return socialPostRepository.save(post);
    }

    /**
     * FACEBOOK / TIKTOK / ZALO; giá trị khác (app đời cũ gửi "OTHER") giữ OTHER.
     * Nhận cả cách viết thường, "Facebook", "Tiktok"...
     */
    static String chuanHoaNenTang(String raw) {
        String s = raw == null ? "" : raw.trim().toUpperCase().replace(" ", "");
        return switch (s) {
            case "FACEBOOK", "FB" -> "FACEBOOK";
            case "TIKTOK" -> "TIKTOK";
            case "ZALO" -> "ZALO";
            default -> "OTHER";
        };
    }

    /** Facebook và TikTok có link công khai từng bài nên bắt buộc dán link. */
    static boolean canLink(String nenTang) {
        return "FACEBOOK".equals(nenTang) || "TIKTOK".equals(nenTang);
    }

    private static String tenNenTang(String nenTang) {
        return switch (nenTang) {
            case "FACEBOOK" -> "Facebook";
            case "TIKTOK" -> "TikTok";
            case "ZALO" -> "Zalo";
            default -> "nền tảng khác";
        };
    }

    /** Chỉ chấp nhận VIDEO hoặc POST; giá trị lạ/rỗng quy về POST. */
    private String normalizeContentType(String raw) {
        return "VIDEO".equalsIgnoreCase(raw == null ? "" : raw.trim()) ? "VIDEO" : "POST";
    }

    /** Tên gọi ngắn của một bài để ghép vào nhật ký điểm, ví dụ "Video trên TikTok". */
    private String moTa(SocialPost post) {
        String loai = "VIDEO".equals(post.getContentType()) ? "Video xây kênh" : "Bài đăng";
        String nen = (post.getPlatform() == null || post.getPlatform().isBlank())
                ? "" : " trên " + post.getPlatform();
        return loai + nen;
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


    /**
     * Admin sửa nội dung bài đăng. Đổi trạng thái ở đây cũng cộng/trừ điểm KPI
     * đúng như khi bấm Duyệt / Từ chối, tránh lệch điểm giữa hai đường sửa.
     */
    @Transactional
    public SocialPost updatePost(Long postId, SubmitPostDTO dto, String newStatus, Long editorId) {
        return updatePost(postId, dto, newStatus, editorId, null);
    }

    /** @param lyDoTuChoi bắt buộc khi đổi sang REJECTED */
    @Transactional
    public SocialPost updatePost(Long postId, SubmitPostDTO dto, String newStatus, Long editorId, String lyDoTuChoi) {
        SocialPost post = socialPostRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bài viết MXH có ID: " + postId));

        if (dto.getPlatform() != null && !dto.getPlatform().isBlank()) post.setPlatform(chuanHoaNenTang(dto.getPlatform()));
        if (dto.getLink() != null && !dto.getLink().isBlank()) post.setLink(dto.getLink());
        if (dto.getCaption() != null) post.setCaption(dto.getCaption());
        if (dto.getScreenshotUrl() != null) post.setScreenshotUrl(dto.getScreenshotUrl());
        if (dto.getContentType() != null) post.setContentType(normalizeContentType(dto.getContentType()));

        if ("REJECTED".equals(post.getStatus()) && (newStatus == null || newStatus.isBlank() || "REJECTED".equals(newStatus))
                && lyDoTuChoi != null && !lyDoTuChoi.isBlank()) {
            post.setRejectReason(LyDoTuChoi.batBuoc(lyDoTuChoi)); // sửa lại lý do của bài đã từ chối
        }
        if (newStatus != null && !newStatus.isBlank() && !newStatus.equals(post.getStatus())) {
            String lyDo = "REJECTED".equals(newStatus) ? LyDoTuChoi.batBuoc(lyDoTuChoi) : null;
            boolean wasApproved = "APPROVED".equals(post.getStatus());
            boolean nowApproved = "APPROVED".equals(newStatus);

            if (wasApproved && !nowApproved) {
                thuHoiDiem(post, "Admin gỡ duyệt: ");
            } else if (!wasApproved && nowApproved) {
                kpiCalculationService.updateKpiPoints(post.getUser().getId(), "post", KPI_POINTS_POST,
                        post.getSubmittedAt(), "Admin duyệt: " + moTa(post));
            }

            post.setStatus(newStatus);
            post.setRejectReason(lyDo);
            if (nowApproved || "REJECTED".equals(newStatus)) {
                userRepository.findById(editorId).ifPresent(post::setApprovedBy);
                post.setApprovedAt(ZonedDateTime.now());
            } else {
                post.setApprovedBy(null);
                post.setApprovedAt(null);
            }
        }

        return socialPostRepository.save(post);
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
        post.setRejectReason(null);
        post.setApprovedBy(approver);
        post.setApprovedAt(ZonedDateTime.now());
        SocialPost savedPost = socialPostRepository.save(post);

        // Cá»™ng Ä‘iá»ƒm KPI tuáº§n gá»­i yÃªu cáº§u
        kpiCalculationService.updateKpiPoints(post.getUser().getId(), "post", KPI_POINTS_POST,
                post.getSubmittedAt(), "Admin duyệt: " + moTa(post));

        return savedPost;
    }

    @Transactional
    public SocialPost rejectPost(Long postId, Long approvedById, String lyDoTuChoi) {
        String lyDo = LyDoTuChoi.batBuoc(lyDoTuChoi);
        SocialPost post = socialPostRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("KhÃ´ng tÃ¬m tháº¥y bÃ i viáº¿t MXH cÃ³ ID: " + postId));

        User approver = userRepository.findById(approvedById)
                .orElseThrow(() -> new IllegalArgumentException("KhÃ´ng tÃ¬m tháº¥y ngÆ°á»i duyá»‡t"));

        // Đang APPROVED thì thu hồi đúng số điểm bài này thực đã cộng
        if ("APPROVED".equals(post.getStatus())) {
            thuHoiDiem(post, "Admin từ chối: ");
        }

        post.setStatus("REJECTED");
        post.setRejectReason(lyDo);
        post.setApprovedBy(approver);
        post.setApprovedAt(ZonedDateTime.now());

        return socialPostRepository.save(post);
    }

    @Transactional
    public void deletePost(Long postId) {
        SocialPost post = socialPostRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("KhÃ´ng tÃ¬m tháº¥y bÃ i viáº¿t MXH cÃ³ ID: " + postId));

        // Đang APPROVED thì thu hồi đúng số điểm bài này thực đã cộng
        if ("APPROVED".equals(post.getStatus())) {
            thuHoiDiem(post, "Admin xóa bản ghi: ");
        }

        socialPostRepository.delete(post);
    }

    /**
     * Gỡ điểm của một bài đã duyệt — đúng số THỰC đã cộng. Nhóm Lan tỏa trần
     * 30đ/tuần: bài duyệt lúc nhóm đã đầy thì vào 0đ; trừ đại 5đ là ăn vào điểm
     * của các bài khác. Tìm dòng duyệt theo đầu câu "Admin duyệt: " chứ không theo
     * nguyên câu, vì nội dung bài có thể đã được sửa sau khi duyệt.
     */
    private void thuHoiDiem(SocialPost post, String dauCau) {
        Long uid = post.getUser().getId();
        int dangGiu = kpiCalculationService.diemThucDaCong(uid, "post", "Admin duyệt: ",
                post.getSubmittedAt(), KPI_POINTS_POST);
        if (dangGiu <= 0) return;
        kpiCalculationService.updateKpiPoints(uid, "post", -dangGiu, post.getSubmittedAt(), dauCau + moTa(post));
    }
}
