package com.trilong.kpibackend.core.config;

import com.trilong.kpibackend.core.security.HoSoNongService;
import com.trilong.kpibackend.core.security.JwtUtils;
import com.trilong.kpibackend.core.security.QuyenKenhWebSocket;
import com.trilong.kpibackend.modules.user.entity.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Kênh WebSocket: không token thì không nối, chỉ nghe được kênh của mình / kênh
 * đúng quyền, người bị đăng xuất mọi thiết bị không nhận tin nữa. Token ký bằng
 * khóa thật của bài test, chỉ giả bản chụp DB.
 */
class BaoVeWebSocketTest {

    private static final String KHOA = "chi-dung-cho-kiem-thu-khong-phai-khoa-that-32ky-tu";

    private final JwtUtils jwt = new JwtUtils();
    private final HoSoNongService hoSoNong = mock(HoSoNongService.class);
    private final BaoVeWebSocket baoVe = new BaoVeWebSocket(jwt, hoSoNong);

    private final User sale = User.builder().id(7L).phoneNumber("0912345678").fullName("Sale A").role("SALE").build();
    private final User admin = User.builder().id(1L).phoneNumber("admin").fullName("Admin").role("ADMIN").build();

    @BeforeEach
    void chuanBi() {
        ReflectionTestUtils.setField(jwt, "jwtSecret", KHOA);
        ReflectionTestUtils.setField(jwt, "jwtExpirationMs", 3_600_000L);
        ReflectionTestUtils.setField(baoVe, "tuoiToiDaMs", 7_776_000_000L);
        coBanChup(7L, "SALE", "ACTIVE", null);
        coBanChup(1L, "ADMIN", "ACTIVE", null);
    }

    private void coBanChup(Long id, String vaiTro, String trangThai, ZonedDateTime phienHopLeTu) {
        when(hoSoNong.lay(id)).thenReturn(Optional.of(new HoSoNongService.BanChup(vaiTro, null, trangThai,
                "Người " + id, null, phienHopLeTu, Set.of(), System.currentTimeMillis() + 60_000)));
    }

    private static Message<byte[]> khung(StompCommand lenh, String phien, String token, String kenh) {
        StompHeaderAccessor a = StompHeaderAccessor.create(lenh);
        a.setSessionId(phien);
        if (token != null) a.setNativeHeader("Authorization", "Bearer " + token);
        if (kenh != null) a.setDestination(kenh);
        a.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], a.getMessageHeaders());
    }

    private static Message<byte[]> tinGuiToi(String phien) {
        SimpMessageHeaderAccessor a = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        a.setSessionId(phien);
        a.setDestination("/topic/kpi/7");
        return MessageBuilder.createMessage("{}".getBytes(StandardCharsets.UTF_8), a.getMessageHeaders());
    }

    private void noi(String phien, User u) {
        baoVe.kiemTraVao(khung(StompCommand.CONNECT, phien, jwt.generateToken(u, 99L), null));
    }

    @Test
    @DisplayName("Không token / token giả → không cho nối")
    void khongTokenKhongNoi() {
        assertThatThrownBy(() -> baoVe.kiemTraVao(khung(StompCommand.CONNECT, "s1", null, null)))
                .isInstanceOf(MessageDeliveryException.class);
        String gia = Jwts.builder().subject("1").issuedAt(new Date())
                .signWith(Keys.hmacShaKeyFor("mot-khoa-khac-hoan-toan-cung-du-32-ky-tu-nhe".getBytes(StandardCharsets.UTF_8)))
                .compact();
        assertThatThrownBy(() -> baoVe.kiemTraVao(khung(StompCommand.CONNECT, "s1", gia, null)))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    @DisplayName("Token hết hạn nhưng ký đúng, người còn làm → vẫn nối (app nối lại bằng token cũ)")
    void tokenHetHanVanNoi() {
        Date phat = new Date(System.currentTimeMillis() - 5 * 3_600_000L);
        String cu = Jwts.builder().subject("7").claim("phoneNumber", "0912345678").issuedAt(phat)
                .expiration(new Date(phat.getTime() + 3_600_000L))
                .signWith(Keys.hmacShaKeyFor(KHOA.getBytes(StandardCharsets.UTF_8))).compact();
        Message<?> kq = baoVe.kiemTraVao(khung(StompCommand.CONNECT, "s1", cu, null));
        assertThat(StompHeaderAccessor.wrap(kq).getUser()).isNotNull();
        assertThat(baoVe.kiemTraRa(tinGuiToi("s1"))).isNotNull();
    }

    @Test
    @DisplayName("Tài khoản bị khóa, hoặc token phát trước lúc đăng xuất mọi thiết bị → không cho nối")
    void phienDaKetThuc() {
        coBanChup(7L, "SALE", "INACTIVE", null);
        assertThatThrownBy(() -> noi("s1", sale)).isInstanceOf(MessageDeliveryException.class);

        coBanChup(7L, "SALE", "ACTIVE", ZonedDateTime.now().plusMinutes(1));
        assertThatThrownBy(() -> noi("s2", sale)).isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    @DisplayName("Sale chỉ nghe kênh của mình; kênh quản trị và kênh người khác bị bỏ")
    void saleChiNgheKenhMinh() {
        noi("s1", sale);
        assertThat(baoVe.kiemTraVao(khung(StompCommand.SUBSCRIBE, "s1", null, "/topic/kpi/7"))).isNotNull();
        assertThat(baoVe.kiemTraVao(khung(StompCommand.SUBSCRIBE, "s1", null, "/topic/dong-bo/all"))).isNotNull();
        assertThat(baoVe.kiemTraVao(khung(StompCommand.SUBSCRIBE, "s1", null, "/topic/kpi/8"))).isNull();
        assertThat(baoVe.kiemTraVao(khung(StompCommand.SUBSCRIBE, "s1", null, "/topic/thuc-chien/admin"))).isNull();
        assertThat(baoVe.kiemTraVao(khung(StompCommand.SUBSCRIBE, "s1", null, "/topic/**"))).isNull();
    }

    @Test
    @DisplayName("Kết nối chưa CONNECT hợp lệ thì không SUBSCRIBE được gì")
    void chuaNoiKhongNgheDuoc() {
        assertThat(baoVe.kiemTraVao(khung(StompCommand.SUBSCRIBE, "la", null, "/topic/dong-bo/all"))).isNull();
    }

    @Test
    @DisplayName("Admin nghe được kênh quản trị; không ai SEND thẳng lên kênh được")
    void adminNgheKenhQuanTri() {
        noi("s1", admin);
        assertThat(baoVe.kiemTraVao(khung(StompCommand.SUBSCRIBE, "s1", null, "/topic/thuc-chien/admin"))).isNotNull();
        assertThat(baoVe.kiemTraVao(khung(StompCommand.SUBSCRIBE, "s1", null, "/topic/admin/requests"))).isNotNull();
        assertThat(baoVe.kiemTraVao(khung(StompCommand.SEND, "s1", null, "/topic/thuc-chien/admin"))).isNull();
    }

    @Test
    @DisplayName("Đang nối thì bị đăng xuất mọi thiết bị → tin sau đó không tới nữa; ngắt kết nối thì quên")
    void biDangXuatThiNgungNhanTin() {
        noi("s1", sale);
        assertThat(baoVe.kiemTraRa(tinGuiToi("s1"))).isNotNull();

        coBanChup(7L, "SALE", "ACTIVE", ZonedDateTime.now().plusMinutes(1));
        assertThat(baoVe.kiemTraRa(tinGuiToi("s1"))).isNull();

        coBanChup(7L, "SALE", "ACTIVE", null);
        baoVe.kiemTraVao(khung(StompCommand.DISCONNECT, "s1", null, null));
        assertThat(baoVe.kiemTraRa(tinGuiToi("s1"))).isNull();
    }

    @Test
    @DisplayName("Bảng quyền kênh")
    void bangQuyen() {
        assertThat(QuyenKenhWebSocket.duocNghe("/topic/ho-so/7", 7L, "SALE")).isTrue();
        assertThat(QuyenKenhWebSocket.duocNghe("/topic/dong-bo/7", 7L, "SALE")).isTrue();
        assertThat(QuyenKenhWebSocket.duocNghe("/topic/dong-bo/70", 7L, "SALE")).isFalse();
        assertThat(QuyenKenhWebSocket.duocNghe("/topic/kpi/7/../8", 7L, "SALE")).isFalse();
        assertThat(QuyenKenhWebSocket.duocNghe("/topic/admin/requests", 3L, "VAN_PHONG")).isTrue();
        assertThat(QuyenKenhWebSocket.duocNghe("/topic/thuc-chien/admin", 3L, "VAN_PHONG")).isFalse();
        assertThat(QuyenKenhWebSocket.duocNghe("/topic/thuc-chien/admin", 4L, "TRUONG_PHONG")).isTrue();
        assertThat(QuyenKenhWebSocket.duocNghe("/topic/feedbacks/hr", 3L, "VAN_PHONG")).isTrue();
        assertThat(QuyenKenhWebSocket.duocNghe("/topic/la", 1L, "ADMIN")).isFalse();
        assertThat(QuyenKenhWebSocket.duocNghe("/topic/kpi/7", null, "SALE")).isFalse();
    }
}
