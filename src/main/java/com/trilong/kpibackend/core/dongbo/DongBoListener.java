package com.trilong.kpibackend.core.dongbo;

import com.trilong.kpibackend.modules.attendance.entity.CheckinLog;
import com.trilong.kpibackend.modules.attendance.entity.LeaveRequest;
import com.trilong.kpibackend.modules.battle.entity.FieldBattle;
import com.trilong.kpibackend.modules.deal.entity.Deal;
import com.trilong.kpibackend.modules.feedback.entity.Feedback;
import com.trilong.kpibackend.modules.post.entity.SocialPost;
import com.trilong.kpibackend.modules.training.entity.OneOnOneTraining;
import com.trilong.kpibackend.modules.training.entity.TrainingAttendee;
import com.trilong.kpibackend.modules.training.entity.TrainingRsvp;
import com.trilong.kpibackend.modules.training.entity.TrainingSession;
import com.trilong.kpibackend.modules.user.entity.Department;
import com.trilong.kpibackend.modules.user.entity.ReferralSubmission;
import com.trilong.kpibackend.modules.user.entity.User;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.*;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.proxy.HibernateProxy;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * ĐỒNG BỘ WEB → APP: dữ liệu vừa đổi ở máy chủ thì báo ngay cho app đang mở.
 *
 * <p><b>Vì sao cần:</b> web và app dùng chung một DB, nhưng app chỉ tải danh
 * sách khi mở màn hình. Admin duyệt bài đăng trên web thì nhân sự vẫn thấy "chờ
 * duyệt" tới khi tự thoát ra vào lại; Admin tạo buổi đào tạo thì app không biết.
 *
 * <p><b>Cách làm:</b> móc vào sự kiện "sau khi commit" của Hibernate cho MỌI
 * thao tác thêm/sửa/xóa, rồi nhắn một tin ngắn qua WebSocket:
 * <ul>
 *   <li>{@code /topic/dong-bo/{userId}} — dữ liệu của riêng một người (chấm
 *       công, bài đăng, thực chiến, chốt căn, đơn vắng, phản hồi, gieo hạt…)</li>
 *   <li>{@code /topic/dong-bo/all} — dữ liệu chung (buổi đào tạo, phòng ban)</li>
 * </ul>
 * App nhận tin thì tải lại đúng loại dữ liệu đó. Một chỗ này bắt được mọi
 * đường sửa dữ liệu — web, app, tác vụ hằng đêm, và cả tính năng thêm sau này —
 * thay vì phải nhớ gọi tay ở từng hàm duyệt/sửa/xóa.
 *
 * <p><b>Chỉ nhắn sau khi commit:</b> nhắn trước commit thì app tải lại mà DB
 * chưa có dữ liệu mới, hoặc giao dịch bị hủy mà app đã tưởng là xong.
 *
 * <p><b>Tin nhắn không chứa dữ liệu</b>, chỉ có loại + id + thao tác: ai lỡ
 * nghe trộm kênh của người khác cũng chỉ biết "có gì đó vừa đổi". App tự gọi
 * API (có kiểm quyền) để lấy dữ liệu thật.
 *
 * <p>Điểm KPI và hồ sơ nhân sự đã có kênh riêng ({@code /topic/kpi/…},
 * {@code /topic/ho-so/…}) nên không đi qua đây.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DongBoListener implements PostCommitInsertEventListener,
        PostCommitUpdateEventListener, PostCommitDeleteEventListener {

    /** Người nhận tin: {@code userId == null} nghĩa là gửi cho mọi người. */
    public record DiaChi(String loai, Long userId) {}

    private final EntityManagerFactory entityManagerFactory;
    private final SimpMessagingTemplate messagingTemplate;

    @PostConstruct
    void dangKy() {
        EventListenerRegistry dangKy = entityManagerFactory.unwrap(SessionFactoryImplementor.class)
                .getServiceRegistry().getService(EventListenerRegistry.class);
        dangKy.appendListeners(EventType.POST_COMMIT_INSERT, this);
        dangKy.appendListeners(EventType.POST_COMMIT_UPDATE, this);
        dangKy.appendListeners(EventType.POST_COMMIT_DELETE, this);
        log.info("[Đồng bộ] Đã móc vào sự kiện sau commit — app sẽ được báo khi dữ liệu đổi.");
    }

    /**
     * Thực thể nào báo cho ai, dưới tên loại gì. Trả null với thực thể không cần
     * đồng bộ (nhật ký điểm, phiên đăng nhập, điểm tuần… đã có kênh riêng hoặc
     * app không hiển thị).
     *
     * <p>Tên loại phải khớp {@code DongBo.cacLoai} bên app.
     */
    public static DiaChi phanLoai(Object e) {
        if (e instanceof CheckinLog c)         return new DiaChi("CHAM_CONG", c.getUserId());
        if (e instanceof LeaveRequest l)       return new DiaChi("DON_VANG", l.getUserId());
        if (e instanceof FieldBattle f)        return new DiaChi("THUC_CHIEN", idCua(f.getUser()));
        if (e instanceof Deal d)               return new DiaChi("CHOT_CAN", idCua(d.getUser()));
        if (e instanceof SocialPost p)         return new DiaChi("BAI_DANG", idCua(p.getUser()));
        if (e instanceof Feedback f)           return new DiaChi("PHAN_HOI", f.getSenderId());
        if (e instanceof ReferralSubmission r) return new DiaChi("GIEO_HAT", r.getReferrerId());
        if (e instanceof OneOnOneTraining o)   return new DiaChi("DAO_TAO", o.getUserId());
        if (e instanceof TrainingAttendee a)   return new DiaChi("DAO_TAO", a.getUserId());
        if (e instanceof TrainingRsvp r)       return new DiaChi("DAO_TAO", r.getUserId());
        // Dữ liệu chung: cả công ty cùng thấy
        if (e instanceof TrainingSession)      return new DiaChi("DAO_TAO", null);
        // Phòng ban đổi tọa độ/bán kính → mọi người tải lại hồ sơ để chấm công đúng
        if (e instanceof Department)           return new DiaChi("HO_SO", null);
        return null;
    }

    /**
     * Id của người dùng mà không nạp cả bản ghi. Quan hệ LAZY là proxy: sau
     * commit phiên làm việc đã đóng, gọi getter thường có thể ném lỗi; đọc id từ
     * proxy thì luôn có sẵn.
     */
    static Long idCua(User u) {
        if (u == null) return null;
        if (u instanceof HibernateProxy hp) return (Long) hp.getHibernateLazyInitializer().getIdentifier();
        return u.getId();
    }

    @Override public void onPostInsert(PostInsertEvent e) { gui(e.getEntity(), e.getId(), "THEM"); }
    @Override public void onPostUpdate(PostUpdateEvent e) { gui(e.getEntity(), e.getId(), "SUA"); }
    @Override public void onPostDelete(PostDeleteEvent e) { gui(e.getEntity(), e.getId(), "XOA"); }

    // Giao dịch hủy thì không có gì để báo
    @Override public void onPostInsertCommitFailed(PostInsertEvent e) {}
    @Override public void onPostUpdateCommitFailed(PostUpdateEvent e) {}
    @Override public void onPostDeleteCommitFailed(PostDeleteEvent e) {}

    @Override public boolean requiresPostCommitHandling(EntityPersister persister) { return true; }

    private void gui(Object entity, Object id, String thaoTac) {
        DiaChi dc = phanLoai(entity);
        if (dc == null) return;
        // Không để việc báo tin làm hỏng thao tác chính — dữ liệu đã lưu xong rồi
        try {
            Map<String, Object> tin = Map.of("loai", dc.loai(), "thaoTac", thaoTac,
                    "id", id == null ? "" : String.valueOf(id));
            String kenh = "/topic/dong-bo/" + (dc.userId() == null ? "all" : dc.userId());
            messagingTemplate.convertAndSend(kenh, (Object) tin);
        } catch (Exception ex) {
            log.debug("[Đồng bộ] Không nhắn được {} {}: {}", dc.loai(), id, ex.getMessage());
        }
    }
}
