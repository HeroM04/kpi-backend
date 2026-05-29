package com.trilong.kpibackend.core.utils;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.Locale;

/**
 * Tiện ích xử lý ngày giờ cho hệ thống KPI.
 *
 * Timezone mặc định: Asia/Ho_Chi_Minh (UTC+7)
 *
 * Các hàm được thiết kế sẵn cho nhu cầu tính KPI:
 * - Xác định tuần hiện tại trong tháng (4 tuần hay 5 tuần)
 * - Kiểm tra check-in đúng giờ (trước 8:30 / sau 17:30)
 * - Lấy khoảng thời gian tuần/tháng để query DB
 */
public class DateFormatUtils {

    public static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    // Định dạng hiển thị chuẩn cho toàn hệ thống
    public static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    public static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    public static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    // Mốc giờ check-in theo quy định
    private static final LocalTime MORNING_CHECKIN_DEADLINE = LocalTime.of(8, 30);   // 8:30
    private static final LocalTime AFTERNOON_CHECKOUT_TIME = LocalTime.of(17, 30);   // 17:30

    // ── Giờ hiện tại ────────────────────────────────────────────────────────

    /** Lấy thời điểm hiện tại theo giờ Việt Nam */
    public static ZonedDateTime nowVN() {
        return ZonedDateTime.now(VN_ZONE);
    }

    /** Lấy ngày hiện tại theo giờ Việt Nam */
    public static LocalDate todayVN() {
        return LocalDate.now(VN_ZONE);
    }

    // ── Kiểm tra check-in ───────────────────────────────────────────────────

    /**
     * Kiểm tra check-in buổi sáng có đúng giờ không (trước 8:30).
     * @param checkinTime Thời gian check-in
     * @return true nếu đúng giờ (không đi muộn)
     */
    public static boolean isMorningOnTime(ZonedDateTime checkinTime) {
        LocalTime localTime = checkinTime.withZoneSameInstant(VN_ZONE).toLocalTime();
        return !localTime.isAfter(MORNING_CHECKIN_DEADLINE);
    }

    /**
     * Kiểm tra check-out buổi chiều có đủ giờ không (sau 17:30).
     * @param checkoutTime Thời gian check-out
     * @return true nếu đủ giờ
     */
    public static boolean isAfternoonOnTime(ZonedDateTime checkoutTime) {
        LocalTime localTime = checkoutTime.withZoneSameInstant(VN_ZONE).toLocalTime();
        return !localTime.isBefore(AFTERNOON_CHECKOUT_TIME);
    }

    /**
     * Tính số phút đi muộn so với 8:30.
     * @return 0 nếu đúng giờ, số phút dương nếu đi muộn
     */
    public static long minutesLate(ZonedDateTime checkinTime) {
        LocalTime localTime = checkinTime.withZoneSameInstant(VN_ZONE).toLocalTime();
        if (!localTime.isAfter(MORNING_CHECKIN_DEADLINE)) return 0;
        return Duration.between(MORNING_CHECKIN_DEADLINE, localTime).toMinutes();
    }

    // ── Tuần / Tháng ────────────────────────────────────────────────────────

    /**
     * Lấy số tuần trong tháng của một ngày.
     * Tuần 1 = tuần chứa ngày đầu tiên của tháng.
     */
    public static int getWeekOfMonth(LocalDate date) {
        WeekFields weekFields = WeekFields.of(DayOfWeek.MONDAY, 1);
        return date.get(weekFields.weekOfMonth());
    }

    /**
     * Đếm số tuần làm việc trong tháng (4 hoặc 5 tuần).
     * Dùng để xác định ngưỡng điểm KPI theo tháng.
     */
    public static int countWorkingWeeksInMonth(int year, int month) {
        LocalDate firstDay = LocalDate.of(year, month, 1);
        LocalDate lastDay = firstDay.withDayOfMonth(firstDay.lengthOfMonth());
        WeekFields weekFields = WeekFields.of(DayOfWeek.MONDAY, 1);
        int firstWeek = firstDay.get(weekFields.weekOfMonth());
        int lastWeek = lastDay.get(weekFields.weekOfMonth());
        return lastWeek - firstWeek + 1;
    }

    /**
     * Lấy ngày bắt đầu tuần (Thứ Hai) của một ngày.
     */
    public static LocalDate getStartOfWeek(LocalDate date) {
        return date.with(DayOfWeek.MONDAY);
    }

    /**
     * Lấy ngày kết thúc tuần (Thứ Sáu — ngày làm việc cuối) của một ngày.
     */
    public static LocalDate getEndOfWeek(LocalDate date) {
        return date.with(DayOfWeek.FRIDAY);
    }

    /**
     * Lấy ngày đầu tháng.
     */
    public static LocalDate getStartOfMonth(int year, int month) {
        return LocalDate.of(year, month, 1);
    }

    /**
     * Lấy ngày cuối tháng.
     */
    public static LocalDate getEndOfMonth(int year, int month) {
        return LocalDate.of(year, month, 1).withDayOfMonth(
                LocalDate.of(year, month, 1).lengthOfMonth());
    }

    // ── Format hiển thị ─────────────────────────────────────────────────────

    /** Format ZonedDateTime thành chuỗi "dd/MM/yyyy HH:mm" */
    public static String format(ZonedDateTime dateTime) {
        if (dateTime == null) return "";
        return dateTime.withZoneSameInstant(VN_ZONE).format(DATETIME_FORMAT);
    }

    /** Format LocalDate thành chuỗi "dd/MM/yyyy" */
    public static String format(LocalDate date) {
        if (date == null) return "";
        return date.format(DATE_FORMAT);
    }
}
