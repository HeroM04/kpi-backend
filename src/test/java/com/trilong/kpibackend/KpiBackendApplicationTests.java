package com.trilong.kpibackend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Dựng TOÀN BỘ ứng dụng như lúc chạy thật, trên CSDL H2 trong bộ nhớ.
 *
 * <p>Bắt những lỗi mà test từng lớp không thấy: hai service phụ thuộc vòng
 * tròn, bean thiếu cấu hình, truy vấn JPA viết sai tên cột (Spring kiểm tra
 * lúc khởi động). Lỗi kiểu này trước đây chỉ lộ ra khi deploy lên Render —
 * máy chủ không lên được, cả công ty không chấm công được.
 *
 * <p>Trước đây test này chạy hồ sơ "dev" nên đòi PostgreSQL trên máy, không
 * có là hỏng — tức là chẳng bao giờ chạy được.
 */
@SpringBootTest
@ActiveProfiles("test")
class KpiBackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
