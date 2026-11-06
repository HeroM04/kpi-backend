package com.trilong.kpibackend.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Controller khai {@code @Transactional(readOnly = true)} ở đầu lớp thì mọi API
 * ghi (POST/PUT/DELETE/PATCH) trong đó phải tự khai {@code @Transactional}.
 * Thiếu là lệnh ghi chạy trong giao dịch chỉ đọc: Postgres báo "cannot execute
 * INSERT in a read-only transaction" (nút Thu lại tăng ca tính trùng, 06/11/2026)
 * hoặc thay đổi lặng lẽ không được lưu (nút cắm cờ KPI).
 */
class ApiGhiCoGiaoDichTest {

    @Test
    @DisplayName("API ghi trong controller chỉ đọc đều tự mở giao dịch ghi")
    void apiGhiCoGiaoDichGhi() throws Exception {
        var quet = new ClassPathScanningCandidateComponentProvider(false);
        quet.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        List<String> thieu = new ArrayList<>();
        int soLopChiDoc = 0;
        for (BeanDefinition bd : quet.findCandidateComponents("com.trilong.kpibackend")) {
            Class<?> lop = Class.forName(bd.getBeanClassName());
            Transactional macDinh = lop.getAnnotation(Transactional.class);
            if (macDinh == null || !macDinh.readOnly()) continue;
            soLopChiDoc++;
            for (Method m : lop.getDeclaredMethods()) {
                boolean ghi = m.isAnnotationPresent(PostMapping.class) || m.isAnnotationPresent(PutMapping.class)
                        || m.isAnnotationPresent(DeleteMapping.class) || m.isAnnotationPresent(PatchMapping.class);
                Transactional t = m.getAnnotation(Transactional.class);
                if (ghi && (t == null || t.readOnly())) thieu.add(lop.getSimpleName() + "." + m.getName());
            }
        }
        assertThat(soLopChiDoc).as("có ít nhất KpiController khai chỉ đọc").isPositive();
        assertThat(thieu).as("API ghi thiếu @Transactional").isEmpty();
    }
}
