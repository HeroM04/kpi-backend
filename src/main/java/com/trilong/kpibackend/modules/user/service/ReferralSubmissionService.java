package com.trilong.kpibackend.modules.user.service;

import com.trilong.kpibackend.modules.kpi.repository.KpiAutoGrantRepository;
import com.trilong.kpibackend.modules.user.dto.ApproveReferralDTO;
import com.trilong.kpibackend.modules.user.dto.CreateUserDTO;
import com.trilong.kpibackend.modules.user.dto.ReferralSubmissionDTO;
import com.trilong.kpibackend.modules.user.dto.ReferralSubmissionResponseDTO;
import com.trilong.kpibackend.modules.user.dto.UserDTO;
import com.trilong.kpibackend.modules.user.entity.ReferralSubmission;
import com.trilong.kpibackend.modules.user.entity.User;
import com.trilong.kpibackend.modules.user.repository.ReferralSubmissionRepository;
import com.trilong.kpibackend.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

/**
 * Đơn giới thiệu ứng viên — nhân sự gửi trên app, Admin duyệt trên web.
 *
 * <p>Duyệt đơn là lúc tài khoản nhân sự mới được mở, với người giới thiệu gắn
 * sẵn. Từ đó điểm "gieo hạt" chạy theo quy định chung: một tháng sau, nếu người
 * mới vẫn còn làm thì người giới thiệu được +15đ.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReferralSubmissionService {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final ReferralSubmissionRepository referralSubmissionRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final KpiAutoGrantRepository kpiAutoGrantRepository;

    @Autowired(required = false)
    private SimpMessagingTemplate messagingTemplate;

    // ----------------------------------------------------------------- Nhân sự

    @Transactional
    public ReferralSubmission submit(Long referrerId, ReferralSubmissionDTO dto) {
        String phone = dto.getCandidatePhone().trim();

        User referrer = userRepository.findById(referrerId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người giới thiệu."));

        // Kiểm tra trước khi tra tài khoản trùng, nếu không thông báo sẽ chung chung
        if (phone.equals(referrer.getPhoneNumber())) {
            throw new IllegalArgumentException("Không thể tự giới thiệu chính mình.");
        }
        if (userRepository.existsByPhoneNumber(phone)) {
            throw new IllegalArgumentException("Số điện thoại này đã có tài khoản trong hệ thống.");
        }
        referralSubmissionRepository.findByCandidatePhoneAndStatus(phone, "PENDING")
                .ifPresent(existing -> {
                    if (existing.getReferrerId().equals(referrerId)) {
                        throw new IllegalArgumentException("Bạn đã giới thiệu số điện thoại này, đang chờ duyệt.");
                    }
                    throw new IllegalArgumentException("Số điện thoại này đã có người khác giới thiệu, đang chờ duyệt.");
                });

        ReferralSubmission req = new ReferralSubmission();
        req.setReferrerId(referrerId);
        req.setCandidateName(dto.getCandidateName().trim());
        req.setCandidatePhone(phone);
        req.setNote(dto.getNote());
        req.setStatus("PENDING");

        ReferralSubmission saved = referralSubmissionRepository.save(req);
        notifyAdmin();
        return saved;
    }

    /** Nhân sự tự rút đơn khi còn chờ duyệt. */
    @Transactional
    public void cancel(Long referrerId, Long id) {
        ReferralSubmission req = referralSubmissionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn giới thiệu."));
        if (!req.getReferrerId().equals(referrerId)) {
            throw new IllegalArgumentException("Bạn không có quyền rút đơn này.");
        }
        if (!"PENDING".equals(req.getStatus())) {
            throw new IllegalArgumentException("Chỉ rút được đơn đang chờ duyệt.");
        }
        referralSubmissionRepository.delete(req);
    }

    public List<ReferralSubmission> getMySubmissions(Long referrerId) {
        return referralSubmissionRepository.findByReferrerIdOrderBySubmittedAtDesc(referrerId);
    }

    // -------------------------------------------------------------------- Admin

    public List<ReferralSubmission> getPending() {
        return referralSubmissionRepository.findByStatusOrderBySubmittedAtAsc("PENDING");
    }

    public List<ReferralSubmission> getAll() {
        return referralSubmissionRepository.findAllByOrderBySubmittedAtDesc();
    }

    /**
     * Admin duyệt đơn: mở tài khoản cho ứng viên với người giới thiệu gắn sẵn.
     *
     * @return tài khoản nhân sự vừa tạo
     */
    @Transactional
    public UserDTO approve(Long id, Long adminId, ApproveReferralDTO dto) {
        ReferralSubmission req = referralSubmissionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn giới thiệu."));

        if ("APPROVED".equals(req.getStatus())) {
            throw new IllegalArgumentException("Đơn này đã được duyệt và đã tạo tài khoản.");
        }
        if (dto.getDepartmentId() == null) {
            throw new IllegalArgumentException("Vui lòng chọn phòng ban cho nhân sự mới.");
        }
        if (userRepository.existsByPhoneNumber(req.getCandidatePhone())) {
            throw new IllegalArgumentException("Số điện thoại này đã có tài khoản, không tạo thêm được.");
        }

        LocalDate joined = dto.getJoinedDate() != null ? dto.getJoinedDate() : LocalDate.now(VN_ZONE);

        CreateUserDTO createDto = new CreateUserDTO();
        createDto.setFullName(req.getCandidateName());
        createDto.setPhoneNumber(req.getCandidatePhone());
        createDto.setPassword(dto.getPassword() != null && !dto.getPassword().isBlank()
                ? dto.getPassword() : "123456");
        createDto.setRole(dto.getRole() != null && !dto.getRole().isBlank() ? dto.getRole() : "SALE");
        createDto.setDepartmentId(dto.getDepartmentId());
        createDto.setReferrerId(req.getReferrerId());
        createDto.setJoinedDate(joined);

        UserDTO created = userService.createUser(createDto);

        req.setStatus("APPROVED");
        req.setReviewedBy(adminId);
        req.setReviewedAt(ZonedDateTime.now());
        req.setReviewNote(dto.getNote());
        req.setCreatedUserId(created.getId());
        req.setJoinedDate(joined);
        referralSubmissionRepository.save(req);

        log.info("[Gieo hạt] Duyệt đơn giới thiệu #{} — tạo tài khoản {} (id={}), người giới thiệu id={}, "
                        + "sẽ cộng 15đ từ ngày {}",
                id, created.getFullName(), created.getId(), req.getReferrerId(), joined.plusMonths(1));

        return created;
    }

    @Transactional
    public ReferralSubmission reject(Long id, Long adminId, String note) {
        ReferralSubmission req = referralSubmissionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn giới thiệu."));
        if ("APPROVED".equals(req.getStatus())) {
            throw new IllegalArgumentException("Đơn đã duyệt và đã tạo tài khoản, không từ chối được. "
                    + "Nếu cần, hãy tạm khóa tài khoản đó ở trang Nhân sự.");
        }
        req.setStatus("REJECTED");
        req.setReviewedBy(adminId);
        req.setReviewedAt(ZonedDateTime.now());
        req.setReviewNote(note);
        return referralSubmissionRepository.save(req);
    }

    // ------------------------------------------------------------------ Tiện ích

    public ReferralSubmissionResponseDTO toDTO(ReferralSubmission r) {
        User referrer = userRepository.findById(r.getReferrerId()).orElse(null);
        String dept = (referrer != null && referrer.getDepartment() != null)
                ? referrer.getDepartment().getName() : null;
        String reviewer = r.getReviewedBy() == null ? null
                : userRepository.findById(r.getReviewedBy()).map(User::getFullName).orElse(null);

        boolean granted = r.getCreatedUserId() != null
                && kpiAutoGrantRepository.findByUserIdOrderByGrantedAtDesc(r.getReferrerId()).stream()
                        .anyMatch(g -> ("REFERRAL_" + r.getCreatedUserId()).equals(g.getGrantType()));

        return ReferralSubmissionResponseDTO.from(r,
                referrer != null ? referrer.getFullName() : null, dept, reviewer, granted);
    }

    private void notifyAdmin() {
        if (messagingTemplate == null) return;
        try {
            messagingTemplate.convertAndSend("/topic/admin/requests",
                    (Object) Map.of("type", "REFERRAL", "message", "Có đơn giới thiệu nhân sự mới!"));
        } catch (Exception ignored) {
        }
    }
}
