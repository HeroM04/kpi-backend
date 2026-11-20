package com.trilong.kpibackend.core.config;

import com.trilong.kpibackend.core.security.HoSoNongService;
import com.trilong.kpibackend.core.security.JwtUtils;
import com.trilong.kpibackend.core.security.QuyenKenhWebSocket;
import com.trilong.kpibackend.core.security.UserPrincipal;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cửa kiểm tra kênh WebSocket ({@code /ws}).
 *
 * <p><b>Vì sao:</b> 20/11/2026 thử trên máy chủ chạy thử: nối {@code /ws} KHÔNG
 * kèm token, nghe {@code /topic/thuc-chien/admin} → nhận ngay tên, SĐT khách và
 * vị trí của báo cáo thực chiến vừa gửi. Trước đây máy chủ chỉ đọc token nếu có,
 * không có cũng cho qua, và không xét kênh nào được nghe. Token đã bị thu hồi
 * (đổi mật khẩu, "đăng xuất mọi thiết bị") vẫn giữ kết nối cũ và vẫn nhận tin.
 *
 * <p>Nay ba lớp:
 * <ol>
 *   <li><b>CONNECT</b> phải có token ký đúng, của người còn ACTIVE, phát SAU mốc
 *       "đăng xuất mọi thiết bị". Không đạt → ERROR, đóng kết nối.</li>
 *   <li><b>SUBSCRIBE</b> chỉ được kênh {@link QuyenKenhWebSocket} cho phép (vai trò
 *       đọc từ DB, không tin token). Không được → lặng lẽ bỏ, không đóng kết nối —
 *       bản web cũ đăng ký cả kênh quản trị cho mọi vai trò, đóng là nó nối lại
 *       5 giây một lần mãi.</li>
 *   <li><b>Tin gửi đi</b> tới một kết nối mà chủ của nó vừa bị khóa / bị đăng xuất
 *       mọi thiết bị → bỏ, không chờ người đó tự ngắt.</li>
 * </ol>
 * Máy khách không được gửi tin lên ({@code SEND}): máy chủ không có
 * {@code @MessageMapping} nào, mà SEND thẳng vào {@code /topic/...} thì trình
 * phát tin đơn giản của Spring phát lại cho mọi người đang nghe — ai cũng giả
 * được "thực chiến mới" hiện lên bảng của Admin.
 *
 * <p><b>Token hết hạn vẫn nối được</b> (chữ ký phải đúng, phát trong vòng
 * {@code app.jwt.refresh-expiration-app-ms}): app nối lại kênh bằng đúng token
 * lúc mở kênh, quá 1 giờ là hết hạn, mà app không có cách đổi token cho kênh
 * đang chạy nếu chưa build bản mới. Chặn token hết hạn thì app mất đồng bộ thời
 * gian thực sau một giờ. Token bị thu hồi thì vẫn bị chặn như thường (lớp 1 và 3).
 * Không xét mã phiên (sid): mỗi lần làm mới token máy chủ xoay sang phiên mới và
 * thu hồi phiên cũ, kênh mở bằng token cũ sẽ bị chặn oan.
 */
@Slf4j
@Component
public class BaoVeWebSocket {

    private final JwtUtils jwtUtils;
    private final HoSoNongService hoSoNong;

    @Value("${app.jwt.refresh-expiration-app-ms:7776000000}")
    private long tuoiToiDaMs;

    /** Kết nối đang mở → ai, token phát lúc nào. Xóa khi ngắt kết nối. */
    private final Map<String, KetNoi> theoKetNoi = new ConcurrentHashMap<>();

    record KetNoi(Long userId, Date phatLuc) {}

    public BaoVeWebSocket(JwtUtils jwtUtils, HoSoNongService hoSoNong) {
        this.jwtUtils = jwtUtils;
        this.hoSoNong = hoSoNong;
    }

    public ChannelInterceptor chieuVao() {
        return new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                return kiemTraVao(message);
            }
        };
    }

    public ChannelInterceptor chieuRa() {
        return new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                return kiemTraRa(message);
            }
        };
    }

    Message<?> kiemTraVao(Message<?> message) {
        StompHeaderAccessor a = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (a == null || a.getCommand() == null) return message; // nhịp giữ kết nối

        switch (a.getCommand()) {
            case CONNECT -> {
                UserPrincipal nguoi = xacThuc(a.getFirstNativeHeader("Authorization"), a);
                a.setUser(new UsernamePasswordAuthenticationToken(nguoi, null, nguoi.getAuthorities()));
                return message;
            }
            case SUBSCRIBE -> {
                KetNoi k = theoKetNoi.get(a.getSessionId());
                String kenh = a.getDestination();
                if (k == null) {
                    log.debug("[WS] Bỏ SUBSCRIBE {} của kết nối chưa xác thực {}", kenh, a.getSessionId());
                    return null;
                }
                var banChup = hoSoNong.lay(k.userId());
                if (banChup.isEmpty() || !conHieuLuc(banChup.get(), k)
                        || !QuyenKenhWebSocket.duocNghe(kenh, k.userId(), banChup.get().role())) {
                    log.info("[WS] Không cho nhân sự {} nghe {}", k.userId(), kenh);
                    return null;
                }
                return message;
            }
            case SEND -> {
                KetNoi k = theoKetNoi.get(a.getSessionId());
                log.info("[WS] Bỏ tin SEND tới {} (nhân sự {})", a.getDestination(), k == null ? null : k.userId());
                return null;
            }
            case DISCONNECT -> {
                theoKetNoi.remove(a.getSessionId());
                return message;
            }
            default -> {
                return message;
            }
        }
    }

    Message<?> kiemTraRa(Message<?> message) {
        if (SimpMessageHeaderAccessor.getMessageType(message.getHeaders()) != SimpMessageType.MESSAGE) return message;
        String phien = SimpMessageHeaderAccessor.getSessionId(message.getHeaders());
        KetNoi k = phien == null ? null : theoKetNoi.get(phien);
        if (k == null) {
            log.debug("[WS] Bỏ tin tới kết nối lạ {}", phien);
            return null;
        }
        var banChup = hoSoNong.lay(k.userId()); // bản chụp giữ 60 giây, quên ngay khi bị đăng xuất mọi thiết bị
        if (banChup.isEmpty() || !conHieuLuc(banChup.get(), k)) {
            log.debug("[WS] Bỏ tin tới nhân sự {} (phiên đã kết thúc)", k.userId());
            return null;
        }
        return message;
    }

    @EventListener
    public void khiNgatKetNoi(SessionDisconnectEvent e) {
        theoKetNoi.remove(e.getSessionId());
    }

    private UserPrincipal xacThuc(String header, StompHeaderAccessor a) {
        if (header == null || !header.startsWith("Bearer ")) throw tuChoi("thiếu token");
        Claims c;
        try {
            c = jwtUtils.docClaimsKeCaHetHan(header.substring(7).trim());
        } catch (Exception e) {
            throw tuChoi("token không hợp lệ");
        }
        Long userId;
        try {
            userId = Long.parseLong(c.getSubject());
        } catch (Exception e) {
            throw tuChoi("token không có mã nhân sự");
        }
        Date phatLuc = c.getIssuedAt();
        if (phatLuc == null || phatLuc.getTime() < System.currentTimeMillis() - tuoiToiDaMs) {
            throw tuChoi("token quá cũ");
        }

        var banChup = hoSoNong.lay(userId)
                .orElseThrow(() -> tuChoi("không đọc được nhân sự " + userId));
        KetNoi k = new KetNoi(userId, phatLuc);
        if (!conHieuLuc(banChup, k)) {
            log.info("[WS] Từ chối nối kênh của nhân sự {} (đã khóa hoặc đã đăng xuất mọi thiết bị)", userId);
            throw tuChoi("phiên đăng nhập đã kết thúc");
        }

        theoKetNoi.put(a.getSessionId(), k);
        return UserPrincipal.fromClaims(userId, c.get("phoneNumber", String.class),
                banChup.fullName(), banChup.role(), banChup.departmentId(), banChup.avatarUrl());
    }

    private static boolean conHieuLuc(HoSoNongService.BanChup b, KetNoi k) {
        return b.dangHoatDong() && !b.tokenQuaCu(k.phatLuc());
    }

    private static MessageDeliveryException tuChoi(String lyDo) {
        return new MessageDeliveryException("Không nối được kênh: " + lyDo);
    }
}
