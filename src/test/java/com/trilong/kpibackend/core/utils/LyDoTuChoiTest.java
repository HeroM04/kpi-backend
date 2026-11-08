package com.trilong.kpibackend.core.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Lý do Admin từ chối: bắt buộc, gọn, đọc được từ thân yêu cầu kiểu mới lẫn cũ. */
class LyDoTuChoiTest {

    @Test
    @DisplayName("trống / toàn khoảng trắng → báo Admin nhập lý do")
    void batBuoc() {
        assertThatThrownBy(() -> LyDoTuChoi.batBuoc(null)).hasMessage(LyDoTuChoi.THIEU);
        assertThatThrownBy(() -> LyDoTuChoi.batBuoc("   ")).hasMessage(LyDoTuChoi.THIEU);
        assertThat(LyDoTuChoi.batBuoc("  Ảnh mờ ")).isEqualTo("Ảnh mờ");
    }

    @Test
    @DisplayName("dài quá 500 ký tự → báo ghi ngắn lại")
    void quaDai() {
        assertThatThrownBy(() -> LyDoTuChoi.batBuoc("a".repeat(501))).hasMessageContaining("500");
    }

    @Test
    @DisplayName("đọc reason (web mới) hoặc note (đời cũ)")
    void tuThan() {
        assertThat(LyDoTuChoi.tuThan(Map.of("reason", "x"))).isEqualTo("x");
        assertThat(LyDoTuChoi.tuThan(Map.of("note", "y"))).isEqualTo("y");
        assertThat(LyDoTuChoi.tuThan(null)).isNull();
    }
}
