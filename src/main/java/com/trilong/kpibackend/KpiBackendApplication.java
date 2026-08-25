package com.trilong.kpibackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync // để gửi thông báo đẩy chạy nền, không làm chậm nghiệp vụ chính
public class KpiBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(KpiBackendApplication.class, args);
	}

}
