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
 *
 * <p>Hay gặp: phòng nhân sự mở tài khoản cho người mới trước, người giới thiệu
 * gửi đơn sau. Khi đó số điện thoại đã có tài khoản là chuyện bình thường — đơn
 * vẫn nhận, và duyệt thì gắn người giới thiệu vào tài khoản sẵn có thay vì tạo
 * tài khoản mới. Chỉ chặn khi tài khoản đó đã ghi nhận người giới thiệu rồi.
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
        // Đã có tài khoản (nhân sự mở trước) thì vẫn nhận đơn — chỉ chặn khi đã có người giới thiệu
        userRepository.findByPhoneNumber(phone).ifPresent(account -> chanNeuDaCoNguoiGioiThieu(account, referrerId));
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
     * Số điện thoại đã có tài khoản thì không tạo mới mà gắn người giới thiệu
     * vào tài khoản đó.
     *
     * @return tài khoản nhân sự vừa tạo, hoặc tài khoản sẵn có vừa được gắn người giới thiệu
     */
    @Transactional
    public UserDTO approve(Long id, Long adminId, ApproveReferralDTO dto) {
        ReferralSubmission req = referralSubmissionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn giới thiệu."));

        if ("APPROVED".equals(req.getStatus())) {
            throw new IllegalArgumentException("Đơn này đã được duyệt.");
        }

        User account = userRepository.findByPhoneNumber(req.getCandidatePhone()).orElse(null);
        if (account != null) {
            return ganVaoTaiKhoanSanCo(req, account, adminId, dto);
        }

        if (dto.getDepartmentId() == null) {
            throw new IllegalArgumentException("Vui lòng chọn phòng ban cho nhân sự mới.");
        }

        LocalDate joined = dto.getJoinedDate() != null ? dto.getJoinedDate() : LocalDate.now(VN_ZONE);

        CreateUserDTO createDto = new CreateUserDTO();
        createDto.setFullName(req.getCandidateName());
        createDto.setPhoneNumber(req.getCandidatePhone());
        // Không tự gán 123456 nữa: mật khẩu chung ai cũng đoán được, và con số này
        // từng hiện công khai trên màn hình đăng nhập app. Admin phải đặt riêng.
        if (dto.getPassword() == null || dto.getPassword().trim().length() < 6) {
            throw new IllegalArgumentException("Cần đặt mật khẩu ban đầu (ít nhất 6 ký tự) cho tài khoản mới.");
        }
        createDto.setPassword(dto.getPassword().trim());
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

    /**
     * Số điện thoại đã có tài khoản (nhân sự mở trước): không tạo mới, chỉ ghi
     * người giới thiệu vào tài khoản đó. Không động vào phòng ban, vai trò, mật
     * khẩu — những thứ nhân sự đã đặt. Ngày vào làm giữ theo tài khoản.
     */
    private UserDTO ganVaoTaiKhoanSanCo(ReferralSubmission req, User account, Long adminId, ApproveReferralDTO dto) {
        if (account.getId().equals(req.getReferrerId())) {
            throw new IllegalArgumentException("Không thể tự giới thiệu chính mình.");
        }
        Long daCo = account.getReferrerId();
        if (daCo != null && !daCo.equals(req.getReferrerId())) {
            String ten = userRepository.findById(daCo).map(User::getFullName).orElse("người khác");
            throw new IllegalArgumentException(account.getFullName() + " đã được ghi nhận người giới thiệu là "
                    + ten + ". Muốn đổi thì sửa ở trang Nhân sự, rồi từ chối đơn này.");
        }

        LocalDate joined = UserService.joinedDateOf(account);
        if (joined == null) {
            joined = dto.getJoinedDate() != null ? dto.getJoinedDate() : LocalDate.now(VN_ZONE);
            account.setJoinedDate(joined);
        }
        account.setReferrerId(req.getReferrerId());
        userRepository.save(account);

        req.setStatus("APPROVED");
        req.setReviewedBy(adminId);
        req.setReviewedAt(ZonedDateTime.now());
        req.setReviewNote(dto.getNote());
        req.setCreatedUserId(account.getId()); // tài khoản được gắn — để tra đã cộng điểm hay chưa
        req.setJoinedDate(joined);
        referralSubmissionRepository.save(req);

        log.info("[Gieo hạt] Duyệt đơn giới thiệu #{} — gắn người giới thiệu id={} vào tài khoản sẵn có {} (id={}), "
                        + "sẽ cộng 15đ từ ngày {}",
                req.getId(), req.getReferrerId(), account.getFullName(), account.getId(), joined.plusMonths(1));

        return userService.convertToDTO(account);
    }

    /** Tài khoản đã ghi nhận người giới thiệu thì không nhận thêm đơn cho người đó. */
    private void chanNeuDaCoNguoiGioiThieu(User account, Long referrerId) {
        Long daCo = account.getReferrerId();
        if (daCo == null) return;
        if (daCo.equals(referrerId)) {
            throw new IllegalArgumentException("Bạn đã được ghi nhận là người giới thiệu "
                    + account.getFullName() + " rồi, không cần gửi đơn nữa.");
        }
        throw new IllegalArgumentException(account.getFullName() + " đã được ghi nhận người giới thiệu khác.");
    }

    @Transactional
    public ReferralSubmission reject(Long id, Long adminId, String note) {
        note = com.trilong.kpibackend.core.utils.LyDoTuChoi.batBuoc(note);
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

        ReferralSubmissionResponseDTO out = ReferralSubmissionResponseDTO.from(r,
                referrer != null ? referrer.getFullName() : null, dept, reviewer, granted);

        // Đơn đang chờ mà số đã có tài khoản: cho Admin thấy để biết duyệt là gắn, không tạo mới
        if ("PENDING".equals(r.getStatus())) {
            userRepository.findByPhoneNumber(r.getCandidatePhone()).ifPresent(acc -> {
                out.setExistingAccountId(acc.getId());
                out.setExistingAccountName(acc.getFullName());
                out.setExistingAccountJoinedDate(UserService.joinedDateOf(acc));
                out.setExistingAccountStatus(acc.getStatus());
            });
        }
        return out;
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
