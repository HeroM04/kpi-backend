package com.trilong.kpibackend.modules.zalo;

import com.trilong.kpibackend.modules.battle.entity.FieldBattle;
import com.trilong.kpibackend.modules.training.entity.OneOnOneTraining;
import com.trilong.kpibackend.modules.user.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Tự đăng báo cáo ĐÃ DUYỆT lên nhóm Zalo của công ty qua Zalo Bot
 * (bot.zapps.me): Thực chiến (gặp / hỗ trợ khách) và Đào tạo 1-1.
 *
 * Trước khi có app, nhân sự tự chụp ảnh gửi nhóm Zalo nên nhóm luôn sôi nổi;
 * từ khi nộp qua app thì nhóm im hẳn. Duyệt xong máy chủ đăng thay: ảnh + tên
 * người + phòng + nội dung. Các loại khác (chấm công, bài đăng, chốt căn…)
 * không đăng.
 *
 * Bật / tắt bằng biến môi trường, không cần sửa code:
 *   ZALO_BOT_TOKEN        token của bot
 *   ZALO_NHOM_THUC_CHIEN  chat_id nhóm nhận Thực chiến (trống = không đăng)
 *   ZALO_NHOM_DAO_TAO     chat_id nhóm nhận Đào tạo 1-1 (trống = không đăng; có thể trùng nhóm trên)
 * chat_id lấy ở GET /api/v1/zalo/nhom-gan-day sau khi nhắn một câu vào nhóm có bot.
 *
 * Gửi SAU khi lưu xong (afterCommit) và chạy nền: Zalo chậm hay lỗi thì việc
 * duyệt, cộng điểm vẫn bình thường, chỉ ghi log. Duyệt hàng loạt thì gửi lần
 * lượt từng bài theo một hàng đợi, không dồn Zalo cùng lúc.
 */
@Slf4j
@Service
public class ZaloNhomService {

    static final String API = "https://bot-api.zaloplatforms.com/bot";
    /** Zalo nhận ảnh tải lên tối đa 10 MB. */
    static final int ANH_TOI_DA = 10 * 1024 * 1024;
    /** Chú thích ảnh / tin nhắn tối đa 2000 ký tự. */
    static final int CHU_TOI_DA = 2000;
    private static final DateTimeFormatter GIO = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private final String token;
    private final String nhomThucChien;
    private final String nhomDaoTao;
    private final RestClient http;
    private final ExecutorService hang = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "zalo-nhom");
        t.setDaemon(true);
        return t;
    });

    public ZaloNhomService(@Value("${zalo.bot-token:}") String token,
                           @Value("${zalo.nhom-thuc-chien:}") String nhomThucChien,
                           @Value("${zalo.nhom-dao-tao:}") String nhomDaoTao) {
        this.token = token == null ? "" : token.trim();
        this.nhomThucChien = nhomThucChien == null ? "" : nhomThucChien.trim();
        this.nhomDaoTao = nhomDaoTao == null ? "" : nhomDaoTao.trim();
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(10_000);
        f.setReadTimeout(40_000);
        this.http = RestClient.builder().requestFactory(f).build();
    }

    public boolean coBot() { return !token.isEmpty(); }

    // ── Nội dung ─────────────────────────────────────────────────────────────

    /** Thực chiến vừa được duyệt. Gọi TRONG transaction duyệt (đọc được user, phòng). */
    public void thucChienDaDuyet(FieldBattle b) {
        if (!coBot() || nhomThucChien.isEmpty() || b == null) return;
        try {
            guiSauKhiLuu(nhomThucChien, noiDungThucChien(b), b.getPhotoUrl());
        } catch (Exception e) {
            log.warn("Zalo: không dựng được tin thực chiến #{}: {}", b.getId(), e.getMessage());
        }
    }

    /** Đào tạo 1-1 vừa được duyệt. Gọi TRONG transaction duyệt. */
    public void daoTaoDaDuyet(OneOnOneTraining t) {
        if (!coBot() || nhomDaoTao.isEmpty() || t == null) return;
        try {
            guiSauKhiLuu(nhomDaoTao, noiDungDaoTao(t), t.getPhotoUrl());
        } catch (Exception e) {
            log.warn("Zalo: không dựng được tin đào tạo 1-1 #{}: {}", t.getId(), e.getMessage());
        }
    }

    static String noiDungThucChien(FieldBattle b) {
        String loai = "SUPPORT".equalsIgnoreCase(b.getBattleType()) ? "Hỗ trợ khách" : "Gặp khách";
        StringBuilder s = new StringBuilder("THỰC CHIẾN — ").append(loai).append('\n');
        s.append(nguoi(b.getUser())).append('\n');
        dong(s, "Dự án", b.getProject());
        dong(s, "Khách", b.getCustomerName());   // KHÔNG đăng số điện thoại khách lên nhóm
        dong(s, "Địa điểm", b.getLocation());
        dong(s, "Thời gian", gio(b.getSubmittedAt()));
        if (!trong(b.getContent())) s.append('\n').append(b.getContent().trim());
        return cat(s.toString());
    }

    static String noiDungDaoTao(OneOnOneTraining t) {
        StringBuilder s = new StringBuilder("ĐÀO TẠO 1-1\n");
        s.append(nguoi(t.getUser())).append('\n');
        dong(s, "Thời gian", gio(t.getSubmittedAt()));
        if (!trong(t.getContent())) s.append('\n').append(t.getContent().trim());
        return cat(s.toString());
    }

    private static String nguoi(User u) {
        if (u == null) return "";
        String ten = trong(u.getFullName()) ? "" : u.getFullName().trim();
        String phong = u.getDepartment() != null && !trong(u.getDepartment().getName())
                ? u.getDepartment().getName().trim() : null;
        return phong == null ? ten : ten + " · " + phong;
    }

    private static void dong(StringBuilder s, String nhan, String giaTri) {
        if (!trong(giaTri)) s.append(nhan).append(": ").append(giaTri.trim()).append('\n');
    }

    private static String gio(ZonedDateTime t) {
        return t == null ? null : t.withZoneSameInstant(VN).format(GIO);
    }

    static String cat(String s) {
        String gon = s.strip();
        return gon.length() <= CHU_TOI_DA ? gon : gon.substring(0, CHU_TOI_DA - 1) + "…";
    }

    private static boolean trong(String s) { return s == null || s.isBlank(); }

    // ── Gửi ──────────────────────────────────────────────────────────────────

    /** Chờ transaction duyệt lưu xong mới gửi — duyệt lỗi, rollback thì không đăng. */
    void guiSauKhiLuu(String chatId, String chu, String anhUrl) {
        Runnable xepHang = () -> hang.submit(() -> gui(chatId, chu, anhUrl));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { xepHang.run(); }
            });
        } else {
            xepHang.run();
        }
    }

    /** Có ảnh thì gửi ảnh kèm chú thích; ảnh hỏng / quá lớn thì gửi chữ. */
    void gui(String chatId, String chu, String anhUrl) {
        try {
            byte[] anh = taiAnh(anhUrl);
            if (anh != null && guiAnh(chatId, anh, chu)) return;
            guiChu(chatId, chu);
        } catch (Exception e) {
            log.warn("Zalo: gửi nhóm {} lỗi: {}", chatId, an(e.getMessage()));
        }
    }

    private byte[] taiAnh(String url) {
        if (trong(url) || !url.startsWith("http")) return null;
        try {
            byte[] b = http.get().uri(url).retrieve().body(byte[].class);
            return b != null && b.length > 0 && b.length <= ANH_TOI_DA ? b : null;
        } catch (Exception e) {
            log.warn("Zalo: không tải được ảnh {}: {}", url, e.getMessage());
            return null;
        }
    }

    private boolean guiAnh(String chatId, byte[] anh, String chu) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("chat_id", chatId);
        form.add("caption", chu);
        form.add("photo", new ByteArrayResource(anh) {
            @Override public String getFilename() { return "anh.jpg"; }
        });
        Map<?, ?> kq = http.post().uri(API + token + "/sendPhoto")
                .contentType(MediaType.MULTIPART_FORM_DATA).body(form)
                .retrieve().body(Map.class);
        if (thanhCong(kq)) return true;
        log.warn("Zalo: sendPhoto bị từ chối: {}", kq);
        return false;
    }

    void guiChu(String chatId, String chu) {
        Map<?, ?> kq = http.post().uri(API + token + "/sendMessage")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("chat_id", chatId, "text", chu))
                .retrieve().body(Map.class);
        if (!thanhCong(kq)) throw new IllegalStateException("sendMessage bị từ chối: " + kq);
    }

    /** Lỗi mạng của RestClient in cả URL — có token trong đó; che trước khi ghi log / trả về. */
    String an(String loi) {
        return loi == null || token.isEmpty() ? loi : loi.replace(token, "***");
    }

    private static boolean thanhCong(Map<?, ?> kq) {
        return kq != null && Boolean.TRUE.equals(kq.get("ok"));
    }

    // ── Cài đặt ──────────────────────────────────────────────────────────────

    /**
     * Các cuộc trò chuyện vừa nhắn cho bot (getUpdates) — để Admin lấy chat_id
     * của nhóm. Chỉ chạy được khi bot CHƯA đặt webhook.
     */
    public List<Map<String, Object>> nhomGanDay() {
        if (!coBot()) throw new IllegalStateException("Chưa đặt ZALO_BOT_TOKEN trên máy chủ.");
        Map<?, ?> kq;
        try {
            kq = http.post().uri(API + token + "/getUpdates")
                    .contentType(MediaType.APPLICATION_JSON).body(Map.of("timeout", 5))
                    .retrieve().body(Map.class);
        } catch (Exception e) {
            throw new IllegalStateException("Không gọi được Zalo: " + an(e.getMessage()));
        }
        if (!thanhCong(kq)) throw new IllegalStateException("Zalo trả lỗi: " + kq);
        Object r = kq.get("result");
        List<Object> ds = r instanceof List<?> l ? new ArrayList<>(l) : r == null ? List.of() : List.of(r);
        List<Map<String, Object>> ra = new ArrayList<>();
        for (Object o : ds) {
            if (!(o instanceof Map<?, ?> u) || !(u.get("message") instanceof Map<?, ?> m)) continue;
            if (!(m.get("chat") instanceof Map<?, ?> chat)) continue;
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("chatId", chat.get("id"));
            d.put("loai", chat.get("chat_type"));
            d.put("nguoiNhan", m.get("from") instanceof Map<?, ?> f ? f.get("display_name") : null);
            d.put("noiDung", m.get("text"));
            ra.add(d);
        }
        return ra;
    }

    /** Gửi thử một câu vào nhóm (ngay, không chờ hàng đợi) để kiểm tra cài đặt. */
    public void guiThu(String chatId) {
        if (!coBot()) throw new IllegalStateException("Chưa đặt ZALO_BOT_TOKEN trên máy chủ.");
        if (chatId == null || chatId.isBlank()) throw new IllegalArgumentException("Thiếu chatId của nhóm.");
        try {
            guiChu(chatId, "Tin thử từ hệ thống KPI Trí Long: báo cáo Thực chiến / Đào tạo 1-1 được duyệt sẽ tự đăng vào đây.");
        } catch (Exception e) {
            throw new IllegalStateException(an(e.getMessage()));
        }
    }

    public Map<String, Object> trangThai() {
        return Map.of("coBot", coBot(),
                "nhomThucChien", !nhomThucChien.isEmpty(),
                "nhomDaoTao", !nhomDaoTao.isEmpty());
    }
}
