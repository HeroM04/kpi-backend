package com.trilong.kpibackend.modules.attendance.controller;

import com.trilong.kpibackend.modules.attendance.service.AttendanceReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Tag(name = "Report API", description = "API xuất báo cáo Excel")
public class ReportController {

    private final AttendanceReportService reportService;

    @Operation(summary = "Xuất báo cáo chấm công tháng (Dành cho ADMIN)")
    @GetMapping("/attendance")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> exportMonthlyAttendanceReport(
            @RequestParam int year,
            @RequestParam int month) {
        
        try {
            byte[] fileBytes = reportService.generateMonthlyReport(year, month);
            
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, 
                    "attachment; filename=BaoCaoChamCong_" + month + "_" + year + ".xlsx");
            headers.add(HttpHeaders.CONTENT_TYPE, 
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(fileBytes.length)
                    .body(fileBytes);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
