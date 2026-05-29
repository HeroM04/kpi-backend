package com.trilong.kpibackend.core.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * JPA Configuration — cấu hình tầng data access.
 *
 * Hiện tại chủ yếu dùng annotation để kích hoạt tính năng.
 * Cấu hình chi tiết hơn sẽ được thêm khi cần:
 * - Auditing (tự động gán createdBy, updatedBy)
 * - Multiple DataSource (nếu cần đọc/ghi tách biệt sau này)
 * - Custom naming strategy
 */
@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(basePackages = "com.trilong.kpibackend.modules")
public class JpaConfig {

    // ──────────────────────────────────────────────────────────────────────────
    // TODO Phase 2: Bật JPA Auditing khi cần tự động gán created_by/updated_by
    // ──────────────────────────────────────────────────────────────────────────
    //
    // @Bean
    // public AuditorAware<Long> auditorProvider() {
    //     return () -> {
    //         // Lấy userId từ SecurityContext
    //         // return Optional.ofNullable(currentUserId);
    //         return Optional.empty();
    //     };
    // }
}
