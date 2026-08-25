package com.trilong.kpibackend.modules.notification.repository;

import com.trilong.kpibackend.modules.notification.entity.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {

    Optional<DeviceToken> findByToken(String token);

    List<DeviceToken> findByUserId(Long userId);

    List<DeviceToken> findByUserIdIn(List<Long> userIds);

    void deleteByToken(String token);
}
