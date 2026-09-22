package com.trilong.kpibackend.core.security;

import com.trilong.kpibackend.modules.user.entity.User;
import com.trilong.kpibackend.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bản chụp "nóng" của nhân sự — vai trò, phòng ban, trạng thái — đọc từ DB
 * thay vì tin vào JWT.
 *
 * <p>JWT ghi vai trò và phòng ban tại lúc đăng nhập và sống một giờ. Admin
 * chuyển trưởng phòng sang phòng khác, đổi vai trò hay khóa tài khoản trên web
 * thì suốt một giờ sau máy chủ vẫn xử lý theo dữ liệu cũ trong token. Lớp này
 * cho {@link JwtAuthFilter} nhìn thấy DB ở mỗi yêu cầu mà không phải hỏi DB ở
 * mỗi yêu cầu: giữ bản chụp 60 giây, và quên ngay khi có chỗ nào sửa nhân sự
 * ({@link #quen}) — nên thay đổi trên web có hiệu lực ở đúng yêu cầu kế tiếp.
 *
 * <p>Bộ nhớ này nằm trong tiến trình; Render chạy một bản nên đủ. Lỡ có nơi
 * sửa nhân sự mà quên gọi {@link #quen} thì 60 giây sau cũng tự đúng.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HoSoNongService {

    /** Những gì JwtAuthFilter cần để dựng principal đúng với DB. */
    public record BanChup(String role, Long departmentId, String status,
                          String fullName, String avatarUrl, long hetHanLuc) {
        public boolean dangHoatDong() { return "ACTIVE".equals(status); }
    }

    private static final long SONG_MS = 60_000;

    private final UserRepository userRepository;
    private final Map<Long, BanChup> bo = new ConcurrentHashMap<>();

    /**
     * Bản chụp hiện tại của nhân sự. Rỗng khi không có người này trong DB —
     * hoặc DB không trả lời được, khi đó nơi gọi nên rơi về dữ liệu trong token
     * chứ không chặn người dùng vì DB chậm.
     */
    public Optional<BanChup> lay(Long userId) {
        if (userId == null) return Optional.empty();
        long bayGio = System.currentTimeMillis();
        BanChup cu = bo.get(userId);
        if (cu != null && cu.hetHanLuc() > bayGio) return Optional.of(cu);

        try {
            Optional<BanChup> moi = userRepository.findById(userId).map(u -> chup(u, bayGio));
            moi.ifPresent(b -> bo.put(userId, b));
            if (moi.isEmpty()) bo.remove(userId);
            return moi;
        } catch (Exception e) {
            log.warn("[HoSoNong] Không đọc được nhân sự {} từ DB: {}", userId, e.getMessage());
            // Bản cũ đã hết hạn vẫn tốt hơn là không có gì
            return Optional.ofNullable(cu);
        }
    }

    /** Gọi ngay sau khi sửa một nhân sự (vai trò, phòng ban, trạng thái, tên, ảnh). */
    public void quen(Long userId) {
        if (userId != null) bo.remove(userId);
    }

    /** Gọi khi một thao tác chạm nhiều người cùng lúc (xóa phòng ban, gieo dữ liệu). */
    public void quenTatCa() {
        bo.clear();
    }

    private static BanChup chup(User u, long bayGio) {
        return new BanChup(
                u.getRole(),
                u.getDepartment() != null ? u.getDepartment().getId() : null,
                u.getStatus(),
                u.getFullName(),
                u.getAvatarUrl(),
                bayGio + SONG_MS);
    }
}
