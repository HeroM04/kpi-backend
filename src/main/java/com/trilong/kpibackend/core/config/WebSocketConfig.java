package com.trilong.kpibackend.core.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /** Bắt đăng nhập, chặn kênh không được nghe, chặn tin tới người đã bị đăng xuất. */
    private final BaoVeWebSocket baoVe;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Cấu hình TaskScheduler cho Heartbeat để giữ connection không bị Cloudflare ngắt (100s timeout)
        ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(1);
        taskScheduler.setThreadNamePrefix("wss-heartbeat-");
        taskScheduler.initialize();

        config.enableSimpleBroker("/topic", "/queue")
              .setTaskScheduler(taskScheduler)
              .setHeartbeatValue(new long[]{20000, 20000}); // Gửi Ping/Pong mỗi 20s

        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Mọi nguồn gốc đều được nối: xác thực bằng token trong khung CONNECT, không
        // dùng cookie, nên trang lạ không mượn được phiên của người dùng.
        // Hỗ trợ WebSocket thuần (Raw WebSocket) cho Mobile App (Flutter, React Native, iOS, Android)
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*");
        // Hỗ trợ SockJS fallback cho Web browser
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*").withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(baoVe.chieuVao());
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.interceptors(baoVe.chieuRa());
    }
}
