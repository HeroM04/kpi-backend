package com.trilong.kpibackend.modules.notification.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.*;
import com.trilong.kpibackend.modules.notification.entity.DeviceToken;
import com.trilong.kpibackend.modules.notification.repository.DeviceTokenRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Gửi thông báo đẩy tới điện thoại nhân sự qua Firebase Cloud Messaging.
 *
 * <p>Khác với mục Thông báo trong ứng dụng (chỉ thấy khi mở app), thông báo đẩy
 * hiện thẳng trên màn hình kể cả khi app đóng. Dùng khi có buổi đào tạo mới,
 * đơn xin vắng cần duyệt, hay điểm KPI thay đổi.
 *
 * <p>Cần khóa dịch vụ Firebase, nạp qua biến môi trường {@code
 * FIREBASE_SERVICE_ACCOUNT} (nội dung tệp JSON). Không đặt biến này thì mọi lời
 * gọi gửi thông báo lặng lẽ bỏ qua — hệ thống vẫn chạy bình thường, chỉ là
 * không có thông báo đẩy, nên chạy local hay chưa cấu hình đều không sao.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PushNotificationService {

    private final DeviceTokenRepository deviceTokenRepository;

    @Value("${firebase.service-account:}")
    private String serviceAccountJson;

    private volatile boolean khaDung = false;

    @PostConstruct
    void khoiTao() {
        if (serviceAccountJson == null || serviceAccountJson.isBlank()) {
            log.warn("[Push] Chưa cấu hình FIREBASE_SERVICE_ACCOUNT — thông báo đẩy tắt.");
            return;
        }
        try {
            var creds = GoogleCredentials.fromStream(
                    new ByteArrayInputStream(serviceAccountJson.getBytes(StandardCharsets.UTF_8)));
            var options = FirebaseOptions.builder().setCredentials(creds).build();
            // Tránh tạo trùng nếu Spring khởi tạo lại bean
            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
            }
            khaDung = true;
            log.info("[Push] Firebase đã sẵn sàng gửi thông báo đẩy.");
        } catch (Exception e) {
            log.error("[Push] Không khởi tạo được Firebase: {}", e.getMessage());
        }
    }

    /**
     * Gửi một thông báo tới mọi thiết bị của một nhân sự.
     *
     * <p>Không ném lỗi ra ngoài: gửi thông báo là việc phụ, hỏng thì ghi log
     * chứ không được làm sập nghiệp vụ chính đã gọi tới nó.
     *
     * @param data dữ liệu kèm theo để app biết mở màn hình nào khi bấm vào
     */
    @org.springframework.scheduling.annotation.Async
    @org.springframework.transaction.annotation.Transactional
    public void guiToiNhanSu(Long userId, String tieuDe, String noiDung, Map<String, String> data) {
        if (!khaDung) return;
        guiToiCacToken(deviceTokenRepository.findByUserId(userId), tieuDe, noiDung, data);
    }

    /** Gửi cùng một thông báo tới nhiều nhân sự (ví dụ: có buổi đào tạo mới). */
    @org.springframework.scheduling.annotation.Async
    @org.springframework.transaction.annotation.Transactional
    public void guiToiNhieuNguoi(List<Long> userIds, String tieuDe, String noiDung, Map<String, String> data) {
        if (!khaDung || userIds == null || userIds.isEmpty()) return;
        guiToiCacToken(deviceTokenRepository.findByUserIdIn(userIds), tieuDe, noiDung, data);
    }

    private void guiToiCacToken(List<DeviceToken> thietBi, String tieuDe, String noiDung,
                                Map<String, String> data) {
        if (thietBi.isEmpty()) return;

        Notification thongBao = Notification.builder().setTitle(tieuDe).setBody(noiDung).build();
        List<String> tokenHong = new ArrayList<>();

        for (DeviceToken tb : thietBi) {
            Message.Builder mb = Message.builder()
                    .setToken(tb.getToken())
                    .setNotification(thongBao)
                    .setAndroidConfig(AndroidConfig.builder()
                            .setPriority(AndroidConfig.Priority.HIGH)
                            .setNotification(AndroidNotification.builder()
                                    .setChannelId("kpi_default").build())
                            .build())
                    .setApnsConfig(ApnsConfig.builder()
                            .setAps(Aps.builder().setSound("default").build()).build());
            if (data != null) data.forEach(mb::putData);

            try {
                FirebaseMessaging.getInstance().send(mb.build());
            } catch (FirebaseMessagingException e) {
                // Mã thiết bị hết hạn hoặc app bị gỡ → xóa để lần sau khỏi gửi nữa
                var code = e.getMessagingErrorCode();
                if (code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT) {
                    tokenHong.add(tb.getToken());
                } else {
                    log.warn("[Push] Gửi thất bại tới token …{}: {}", duoiToken(tb.getToken()), e.getMessage());
                }
            } catch (Exception e) {
                log.warn("[Push] Lỗi gửi thông báo: {}", e.getMessage());
            }
        }

        for (String t : tokenHong) {
            try { deviceTokenRepository.deleteByToken(t); } catch (Exception ignored) {}
        }
    }

    private String duoiToken(String token) {
        return token == null || token.length() < 6 ? "?" : token.substring(token.length() - 6);
    }
}
