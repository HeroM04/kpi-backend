package com.trilong.kpibackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class KpiBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(KpiBackendApplication.class, args);
	}

}
