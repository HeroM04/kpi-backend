package com.trilong.kpibackend.modules.attendance.service;

import com.trilong.kpibackend.modules.attendance.entity.CheckinLog;
import com.trilong.kpibackend.modules.attendance.repository.CheckinLogRepository;
import com.trilong.kpibackend.modules.user.entity.User;
import com.trilong.kpibackend.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttendanceReportService {

    private final CheckinLogRepository checkinLogRepository;
    private final UserRepository userRepository;

    public byte[] generateMonthlyReport(int year, int month) throws IOException {
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();

        // Convert to UTC/ZonedDateTime for DB query
        ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
        ZonedDateTime startZDT = startDate.atStartOfDay(zone);
        ZonedDateTime endZDT = endDate.atTime(23, 59, 59).atZone(zone);

        List<User> users = userRepository.findAll();
        List<CheckinLog> logs = checkinLogRepository.findByCheckinTimeBetween(startZDT, endZDT);

        // Gom nhóm log theo user_id, sau đó theo ngày
        Map<Long, Map<LocalDate, List<CheckinLog>>> logsByUserAndDate = logs.stream()
                .collect(Collectors.groupingBy(CheckinLog::getUserId,
                        Collectors.groupingBy(log -> log.getCheckinTime().withZoneSameInstant(zone).toLocalDate())));

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Báo Cáo Chấm Công T" + month);

            // Cấu hình style
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle infoStyle = createInfoStyle(workbook);
            CellStyle cellStyle = createNormalStyle(workbook);

            int currentRow = 0;

            for (User user : users) {
                // Hàng thông tin nhân viên
                Row infoRow = sheet.createRow(currentRow++);
                infoRow.createCell(0).setCellValue("Mã nhân viên: " + user.getId());
                infoRow.createCell(2).setCellValue("Tên nhân viên: " + user.getFullName());
                String deptName = user.getDepartment() != null ? user.getDepartment().getName() : "Không có";
                infoRow.createCell(5).setCellValue("Bộ phận: " + deptName);
                
                for(int c : new int[]{0,2,5}) {
                    infoRow.getCell(c).setCellStyle(infoStyle);
                }

                // Hàng Header cho chi tiết ngày
                Row headerRow = sheet.createRow(currentRow++);
                String[] headers = {"Ngày", "Thứ", "Vào", "Ra", "Tổng Giờ", "Công", "Ghi chú"};
                for (int i = 0; i < headers.length; i++) {
                    Cell cell = headerRow.createCell(i);
                    cell.setCellValue(headers[i]);
                    cell.setCellStyle(headerStyle);
                }

                double totalCong = 0;
                double totalHours = 0;

                Map<LocalDate, List<CheckinLog>> userLogs = logsByUserAndDate.getOrDefault(user.getId(), new HashMap<>());

                // Duyệt qua từng ngày trong tháng
                for (int day = 1; day <= yearMonth.lengthOfMonth(); day++) {
                    LocalDate date = yearMonth.atDay(day);
                    Row row = sheet.createRow(currentRow++);
                    
                    row.createCell(0).setCellValue(date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
                    row.createCell(1).setCellValue(getVietnameseDayOfWeek(date.getDayOfWeek()));

                    List<CheckinLog> dayLogs = userLogs.getOrDefault(date, new ArrayList<>());
                    
                    if (dayLogs.isEmpty()) {
                        row.createCell(2).setCellValue("");
                        row.createCell(3).setCellValue("");
                        row.createCell(4).setCellValue(0);
                        row.createCell(5).setCellValue(0);
                        row.createCell(6).setCellValue("Không có dữ liệu");
                    } else {
                        // Lọc các bản ghi hợp lệ (bỏ REJECTED)
                        List<CheckinLog> validLogs = dayLogs.stream()
                                .filter(l -> !"REJECTED".equals(l.getStatus()))
                                .collect(Collectors.toList());

                        if (validLogs.isEmpty()) {
                            row.createCell(2).setCellValue("");
                            row.createCell(3).setCellValue("");
                            row.createCell(4).setCellValue(0);
                            row.createCell(5).setCellValue(0);
                            row.createCell(6).setCellValue("Dữ liệu bị từ chối");
                            continue;
                        }

                        // Tìm giờ Check-in sớm nhất và Check-out muộn nhất
                        Optional<ZonedDateTime> firstIn = validLogs.stream()
                                .filter(l -> "CHECK_IN".equals(l.getActionType()))
                                .map(CheckinLog::getCheckinTime)
                                .min(ZonedDateTime::compareTo);

                        Optional<ZonedDateTime> lastOut = validLogs.stream()
                                .filter(l -> "CHECK_OUT".equals(l.getActionType()))
                                .map(CheckinLog::getCheckinTime)
                                .max(ZonedDateTime::compareTo);

                        if (firstIn.isPresent()) {
                            row.createCell(2).setCellValue(firstIn.get().withZoneSameInstant(zone).format(DateTimeFormatter.ofPattern("HH:mm")));
                        } else {
                            row.createCell(2).setCellValue("");
                        }

                        if (lastOut.isPresent()) {
                            row.createCell(3).setCellValue(lastOut.get().withZoneSameInstant(zone).format(DateTimeFormatter.ofPattern("HH:mm")));
                        } else {
                            row.createCell(3).setCellValue("");
                        }

                        double hours = 0;
                        double cong = 0;
                        String note = "";

                        if (firstIn.isPresent() && lastOut.isPresent()) {
                            long seconds = ChronoUnit.SECONDS.between(firstIn.get(), lastOut.get());
                            // Trừ 1.5 tiếng nghỉ trưa (từ 12h đến 13h30) nếu khung giờ bao trọn buổi trưa
                            // Code cơ bản tính tổng thời gian giữa 2 mốc
                            // Để chính xác tuyệt đối, ta kiểm tra xem in có trước 12h và out sau 13h30 không
                            long breakSeconds = 0;
                            ZonedDateTime noonStart = date.atTime(12, 0).atZone(zone);
                            ZonedDateTime noonEnd = date.atTime(13, 30).atZone(zone);
                            
                            if (firstIn.get().isBefore(noonStart) && lastOut.get().isAfter(noonEnd)) {
                                breakSeconds = 90 * 60; // 1.5 tiếng
                            }
                            
                            hours = (seconds - breakSeconds) / 3600.0;
                            if (hours < 0) hours = 0;

                            // Quy tắc tính công:
                            // >= 5h -> 1 công
                            // >= 2h và < 5h -> 0.5 công
                            // < 2h -> 0 công
                            if (hours >= 5.0) {
                                cong = 1.0;
                            } else if (hours >= 2.0) {
                                cong = 0.5;
                            } else {
                                cong = 0;
                            }

                        } else if (firstIn.isPresent() && !lastOut.isPresent()) {
                            note = "Quên Check-out";
                        } else if (!firstIn.isPresent() && lastOut.isPresent()) {
                            note = "Chỉ có Check-out";
                        }

                        row.createCell(4).setCellValue(Math.round(hours * 10.0) / 10.0);
                        row.createCell(5).setCellValue(cong);
                        row.createCell(6).setCellValue(note);

                        totalHours += hours;
                        totalCong += cong;
                    }
                    
                    // Format tất cả các ô trên row
                    for(int c = 0; c < 7; c++) {
                        if (row.getCell(c) != null) row.getCell(c).setCellStyle(cellStyle);
                    }
                }

                // Dòng tổng kết cuối tháng
                Row summaryRow = sheet.createRow(currentRow++);
                summaryRow.createCell(0).setCellValue("TỔNG CỘNG");
                summaryRow.createCell(4).setCellValue(Math.round(totalHours * 10.0) / 10.0);
                summaryRow.createCell(5).setCellValue(totalCong);
                for(int c = 0; c < 7; c++) {
                    if (summaryRow.getCell(c) != null) summaryRow.getCell(c).setCellStyle(headerStyle);
                }

                currentRow += 2; // Cách ra 2 dòng cho người tiếp theo
            }

            // Tự động căn chỉnh độ rộng cột
            for (int i = 0; i < 7; i++) {
                sheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    private String getVietnameseDayOfWeek(DayOfWeek dayOfWeek) {
        switch (dayOfWeek) {
            case MONDAY: return "Hai";
            case TUESDAY: return "Ba";
            case WEDNESDAY: return "Tư";
            case THURSDAY: return "Năm";
            case FRIDAY: return "Sáu";
            case SATURDAY: return "Bảy";
            case SUNDAY: return "CN";
            default: return "";
        }
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createInfoStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.BLUE.getIndex());
        style.setFont(font);
        return style;
    }
    
    private CellStyle createNormalStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }
}
