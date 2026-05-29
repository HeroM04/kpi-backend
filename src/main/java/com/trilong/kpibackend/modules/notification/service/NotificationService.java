package com.trilong.kpibackend.modules.notification.service;

import com.trilong.kpibackend.modules.notification.dto.NotificationResponseDTO;
import com.trilong.kpibackend.modules.notification.entity.Notification;
import com.trilong.kpibackend.modules.notification.repository.NotificationRepository;
import com.trilong.kpibackend.modules.user.entity.User;
import com.trilong.kpibackend.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public void createNotification(Long userId, String title, String message, String type) {
        User user = null;
        if (userId != null) {
            user = userRepository.findById(userId).orElse(null);
        }

        Notification notification = Notification.builder()
                .user(user)
                .title(title)
                .message(message)
                .type(type)
                .status("UNREAD")
                .build();

        Notification saved = notificationRepository.save(notification);
        
        // Gửi qua WebSocket
        NotificationResponseDTO dto = NotificationResponseDTO.from(saved);
        if (userId != null) {
            messagingTemplate.convertAndSend("/topic/notifications/user/" + userId, dto);
        } else {
            messagingTemplate.convertAndSend("/topic/notifications/broadcast", dto);
        }
    }

    public List<NotificationResponseDTO> getMyNotifications(Long userId) {
        return notificationRepository.findByUserIdOrBroadcastOrderByCreatedAtDesc(userId)
                .stream()
                .map(NotificationResponseDTO::from)
                .collect(Collectors.toList());
    }

    public long getUnreadCount(Long userId) {
        return notificationRepository.countUnreadByUserId(userId);
    }

    @Transactional
    public void markAsRead(Long id, Long userId) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thông báo"));
        
        if (notification.getUser() != null && !notification.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Không có quyền cập nhật thông báo này");
        }

        notification.setStatus("READ");
        notificationRepository.save(notification);
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        List<Notification> unread = notificationRepository.findByUserIdAndStatus(userId, "UNREAD");
        unread.forEach(n -> n.setStatus("READ"));
        notificationRepository.saveAll(unread);
    }
}
