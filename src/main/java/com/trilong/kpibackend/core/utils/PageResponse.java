package com.trilong.kpibackend.core.utils;

import org.springframework.data.domain.Page;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Chuẩn hoá body phân trang trả về FE: { content, page, size, totalElements, totalPages }.
 */
public final class PageResponse {

    private PageResponse() {
    }

    public static Map<String, Object> fromPage(Page<?> p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("content", p.getContent());
        m.put("page", p.getNumber());
        m.put("size", p.getSize());
        m.put("totalElements", p.getTotalElements());
        m.put("totalPages", p.getTotalPages());
        return m;
    }

    /** Phân trang trên list đã lọc sẵn trong bộ nhớ. */
    public static <T> Map<String, Object> fromList(List<T> all, int page, int size) {
        int total = all.size();
        int safeSize = size <= 0 ? total : size;
        int from = Math.max(0, page * safeSize);
        int to = Math.min(total, from + safeSize);
        List<T> content = from >= total ? List.of() : all.subList(from, to);
        int totalPages = safeSize <= 0 ? 0 : (int) Math.ceil((double) total / safeSize);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("content", content);
        m.put("page", page);
        m.put("size", safeSize);
        m.put("totalElements", (long) total);
        m.put("totalPages", totalPages);
        return m;
    }
}
