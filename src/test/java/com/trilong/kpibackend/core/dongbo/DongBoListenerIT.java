package com.trilong.kpibackend.core.dongbo;

import com.trilong.kpibackend.modules.attendance.entity.CheckinLog;
import com.trilong.kpibackend.modules.battle.entity.FieldBattle;
import com.trilong.kpibackend.modules.deal.entity.Deal;
import com.trilong.kpibackend.modules.kpi.entity.KpiLedgerEntry;
import com.trilong.kpibackend.modules.training.entity.TrainingSession;
import com.trilong.kpibackend.modules.user.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Đồng bộ web → app, chạy trên Hibernate và CSDL thật (H2).
 *
 * <p>Không giả lập Hibernate: điều cần chứng minh chính là móc "sau commit" có
 * thật sự được gọi, gọi SAU commit (không phải trước), và không gọi khi giao
 * dịch bị hủy. Giả lập thì chỉ chứng minh được là mình gọi đúng hàm giả.
 *
 * <p>Tắt giao dịch bao ngoài của @DataJpaTest: mặc định nó bọc mỗi test trong
 * một giao dịch rồi hủy, nên không lần nào commit — móc sau commit sẽ không bao
 * giờ chạy và test nào cũng "qua" một cách vô nghĩa. Mỗi thao tác ở đây tự mở
 * giao dịch riêng bằng TransactionTemplate và commit thật.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(DongBoListener.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DongBoListenerIT {

    @MockitoBean SimpMessagingTemplate messagingTemplate;
    @Autowired EntityManager em;            // bản dùng chung, tự gắn vào giao dịch đang chạy
    @Autowired PlatformTransactionManager txManager;

    private TransactionTemplate tx;

    @BeforeEach
    void chuanBi() {
        tx = new TransactionTemplate(txManager);
    }

    private User taoNguoi() {
        User u = User.builder().fullName("Nhân sự thử")
                .phoneNumber("09" + UUID.randomUUID().toString().replace("-", "").substring(0, 8))
                .passwordHash("x").role("SALE").build();
        tx.executeWithoutResult(s -> em.persist(u));
        return u;
    }

    private void luu(Object entity) {
        tx.executeWithoutResult(s -> em.persist(entity));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> tinGuiToi(String kenh) {
        ArgumentCaptor<Object> tin = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate, atLeast(0)).convertAndSend(eq(kenh), tin.capture());
        return tin.getAllValues().stream().map(o -> (Map<String, Object>) o).toList();
    }

    @Test
    @DisplayName("Chấm công mới → báo đúng người, loại CHAM_CONG, thao tác THEM")
    void chamCongMoi() {
        User u = taoNguoi();
        reset(messagingTemplate);
        CheckinLog c = new CheckinLog();
        c.setUserId(u.getId());
        c.setCheckinTime(ZonedDateTime.now());
        c.setStatus("PENDING");
        luu(c);

        assertThat(tinGuiToi("/topic/dong-bo/" + u.getId()))
                .anySatisfy(t -> {
                    assertThat(t.get("loai")).isEqualTo("CHAM_CONG");
                    assertThat(t.get("thaoTac")).isEqualTo("THEM");
                });
    }

    @Test
    @DisplayName("Admin duyệt thực chiến (sửa) → báo người nộp, thao tác SUA")
    void duyetThucChien() {
        User u = taoNguoi();
        FieldBattle f = FieldBattle.builder().user(u).customerName("Khách A").project("Vista").content("gặp khách")
                .status("PENDING").build();
        luu(f);
        reset(messagingTemplate);

        tx.executeWithoutResult(s -> em.find(FieldBattle.class, f.getId()).setStatus("APPROVED"));

        assertThat(tinGuiToi("/topic/dong-bo/" + u.getId()))
                .anySatisfy(t -> {
                    assertThat(t.get("loai")).isEqualTo("THUC_CHIEN");
                    assertThat(t.get("thaoTac")).isEqualTo("SUA");
                });
    }

    @Test
    @DisplayName("Xóa chốt căn → báo người nộp, thao tác XOA")
    void xoaChotCan() {
        User u = taoNguoi();
        Deal d = Deal.builder().user(u).projectName("Vista").unit("A-1203").price(3.5e9)
                .customerName("Khách B").customerPhone("0900000000").build();
        luu(d);
        reset(messagingTemplate);

        tx.executeWithoutResult(s -> em.remove(em.find(Deal.class, d.getId())));

        assertThat(tinGuiToi("/topic/dong-bo/" + u.getId()))
                .anySatisfy(t -> {
                    assertThat(t.get("loai")).isEqualTo("CHOT_CAN");
                    assertThat(t.get("thaoTac")).isEqualTo("XOA");
                });
    }

    @Test
    @DisplayName("Buổi đào tạo mới là dữ liệu chung → báo kênh /all")
    void buoiDaoTaoMoi() {
        reset(messagingTemplate);
        luu(TrainingSession.builder().title("Pháp lý dự án").roomCode("R-" + UUID.randomUUID()).build());

        assertThat(tinGuiToi("/topic/dong-bo/all"))
                .anySatisfy(t -> assertThat(t.get("loai")).isEqualTo("DAO_TAO"));
    }

    @Test
    @DisplayName("Giao dịch bị hủy → KHÔNG báo gì (app không được tải dữ liệu chưa tồn tại)")
    void giaoDichHuyKhongBao() {
        User u = taoNguoi();
        reset(messagingTemplate);

        tx.executeWithoutResult(s -> {
            CheckinLog c = new CheckinLog();
            c.setUserId(u.getId());
            c.setStatus("PENDING");
            em.persist(c);
            em.flush();
            s.setRollbackOnly();
        });

        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    @DisplayName("Thực thể không cần đồng bộ (nhật ký điểm) → không báo")
    void khongDongBoNhatKyDiem() {
        reset(messagingTemplate);
        KpiLedgerEntry e = new KpiLedgerEntry();
        e.setUserId(1L); e.setCategory("attendance"); e.setPoints(5); e.setEffectivePoints(5);
        e.setReason("Chấm công"); e.setWeek("2026-W39"); e.setMonth("2026-09");
        e.setOccurredAt(ZonedDateTime.now()); e.setCreatedAt(ZonedDateTime.now());
        luu(e);

        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }
}
