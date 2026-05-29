package com.trilong.kpibackend.modules.notification.repository;

import com.trilong.kpibackend.modules.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    
    // Tìm thông báo cá nhân hoặc broadcast (user_id IS NULL)
    @Query("SELECT n FROM Notification n WHERE n.user.id = :userId OR n.user IS NULL ORDER BY n.createdAt DESC")
    List<Notification> findByUserIdOrBroadcastOrderByCreatedAtDesc(Long userId);

    @Query("SELECT COUNT(n) FROM Notification n WHERE (n.user.id = :userId OR n.user IS NULL) AND n.status = 'UNREAD'")
    long countUnreadByUserId(Long userId);
    
    List<Notification> findByUserIdAndStatus(Long userId, String status);
}
