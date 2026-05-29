package com.trilong.kpibackend.core.config;

import com.trilong.kpibackend.core.security.JwtUtils;
import com.trilong.kpibackend.core.security.UserPrincipal;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtUtils jwtUtils; // Đã bắt đúng JwtUtils của bạn

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Hỗ trợ WebSocket thuần (Raw WebSocket) cho Mobile App (Flutter, React Native, iOS, Android)
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*");
        // Hỗ trợ SockJS fallback cho Web browser
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*").withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String token = accessor.getFirstNativeHeader("Authorization");
                    if (token != null && token.startsWith("Bearer ")) {
                        token = token.substring(7).trim();

                        // GỌI ĐÚNG HÀM TRONG JWTUTILS CỦA BẠN: isTokenValid()
                        if (jwtUtils.isTokenValid(token)) {
                            try {
                                // GỌI ĐÚNG HÀM TRONG JWTUTILS CỦA BẠN: extractAllClaims()
                                Claims claims = jwtUtils.extractAllClaims(token);
                                Number deptId = (Number) claims.get("departmentId");

                                UserPrincipal principal = UserPrincipal.fromClaims(
                                        Long.parseLong(claims.getSubject()),
                                        claims.get("phoneNumber", String.class),
                                        claims.get("fullName", String.class),
                                        claims.get("role", String.class),
                                        deptId != null ? deptId.longValue() : null,
                                        claims.get("avatarUrl", String.class)
                                );

                                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                                        principal, null, principal.getAuthorities());
                                accessor.setUser(auth);
                            } catch (Exception e) {
                                throw new RuntimeException("Lỗi parse JWT: " + e.getMessage());
                            }
                        }
                    }
                }
                return message;
            }
        });
    }
}