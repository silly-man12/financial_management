package com.example.financial_management.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
public class DateTimeUtils {

    private static final String[] YEAR_MONTH_PATTERNS = {
            "MM-yyyy", "M-yyyy",
            "yyyy-MM", "yyyy-M",
            "MM/yyyy", "M/yyyy",
            "yyyy/MM", "yyyy/M",
            "MM.yyyy", "M.yyyy",
            "yyyy.MM", "yyyy.M",
            "yyyyMM"
    };

    private static final String[] DATE_PATTERNS = {
            "yyyy-MM-dd", "yyyy-M-d",
            "dd/MM/yyyy", "d/M/yyyy",
            "dd-MM-yyyy", "d-M-yyyy",
            "yyyy/MM/dd", "yyyy/M/d",
            "yyyyMMdd", "yyMMdd"
    };

    /**
     * Phân tích chuỗi tháng thành YearMonth hỗ trợ nhiều định dạng:
     * MM-yyyy, yyyy-MM, MM/yyyy, yyyyMM, LocalDate string, hoặc chỉ tháng "8", "08".
     */
    public static YearMonth parseYearMonth(String monthStr) {
        if (monthStr == null || monthStr.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tham số month không được để trống");
        }
        String cleanStr = monthStr.trim().replaceAll("^[\"']+|[\"']+$", "");

        // Nếu là ISO datetime (ví dụ: 2026-09-07T16:26:09 hoặc có khoảng trắng giờ)
        if (cleanStr.contains("T")) {
            cleanStr = cleanStr.substring(0, cleanStr.indexOf("T")).trim();
        } else if (cleanStr.contains(" ")) {
            cleanStr = cleanStr.substring(0, cleanStr.indexOf(" ")).trim();
        }

        // Tự động thay thế placeholder yyyy hoặc MM nếu người dùng copy nguyên mẫu hướng dẫn
        if (cleanStr.toLowerCase().contains("yyyy")) {
            cleanStr = cleanStr.replaceAll("(?i)yyyy", String.valueOf(LocalDate.now().getYear()));
        }
        if (cleanStr.toLowerCase().contains("mm")) {
            cleanStr = cleanStr.replaceAll("(?i)mm", String.format("%02d", LocalDate.now().getMonthValue()));
        }

        // 1. Thử parse trực tiếp theo YearMonth
        for (String pattern : YEAR_MONTH_PATTERNS) {
            try {
                return YearMonth.parse(cleanStr, DateTimeFormatter.ofPattern(pattern));
            } catch (DateTimeParseException ignored) {
            }
        }

        // 2. Thử parse nếu client truyền cả ngày (LocalDate) ví dụ: 2026-09-07, 07/09/2026, 07-09-2026
        for (String pattern : DATE_PATTERNS) {
            try {
                LocalDate d = LocalDate.parse(cleanStr, DateTimeFormatter.ofPattern(pattern));
                return YearMonth.from(d);
            } catch (DateTimeParseException ignored) {
            }
        }

        // 3. Nếu là chuỗi số nguyên: ví dụ chỉ gửi tháng "9" hoặc "09"
        if (cleanStr.matches("^\\d{1,2}$")) {
            int m = Integer.parseInt(cleanStr);
            if (m >= 1 && m <= 12) {
                return YearMonth.of(LocalDate.now().getYear(), m);
            }
        }

        // 4. Nếu gửi năm 4 chữ số: ví dụ "2026"
        if (cleanStr.matches("^\\d{4}$")) {
            int y = Integer.parseInt(cleanStr);
            return YearMonth.of(y, LocalDate.now().getMonthValue());
        }

        log.warn("Invalid month input received: '{}'", monthStr);
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                String.format("Giá trị tháng '%s' không hợp lệ. Vui lòng gửi định dạng MM-yyyy (ví dụ: '09-2026'), yyyy-MM ('2026-09'), hoặc MM/yyyy ('09/2026')", monthStr));
    }

    /**
     * Xác định khoảng thời gian startDateTime và endDateTime dựa trên kỳ period (month, quarter, year, custom)
     * hoặc theo ngày bắt đầu / ngày kết thúc cụ thể.
     */
    public static LocalDateTime[] resolveDateRange(String period, String startDateStr, String endDateStr) {
        LocalDate start = parseFlexibleDate(startDateStr);
        LocalDate end = parseFlexibleDate(endDateStr);

        if (start != null && end != null) {
            if (start.isAfter(end)) {
                LocalDate temp = start;
                start = end;
                end = temp;
            }
        } else if (start != null) {
            end = start.plusMonths(1).minusDays(1);
        } else if (end != null) {
            start = end.withDayOfMonth(1);
        } else {
            LocalDate now = LocalDate.now();
            if ("quarter".equalsIgnoreCase(period)) {
                int currentQuarter = (now.getMonthValue() - 1) / 3 + 1;
                int startMonth = (currentQuarter - 1) * 3 + 1;
                start = LocalDate.of(now.getYear(), startMonth, 1);
                end = start.plusMonths(3).minusDays(1);
            } else if ("year".equalsIgnoreCase(period)) {
                start = LocalDate.of(now.getYear(), 1, 1);
                end = LocalDate.of(now.getYear(), 12, 31);
            } else {
                start = now.withDayOfMonth(1);
                end = now.withDayOfMonth(now.lengthOfMonth());
            }
        }

        LocalDateTime startDateTime = start.atStartOfDay();
        LocalDateTime endDateTime = end.atTime(LocalTime.MAX);
        return new LocalDateTime[] { startDateTime, endDateTime };
    }

    /**
     * Parse chuỗi ngày tháng linh hoạt hỗ trợ nhiều định dạng.
     */
    public static LocalDate parseFlexibleDate(String dateString) {
        if (dateString == null || dateString.isBlank()) {
            return null;
        }
        String cleanStr = dateString.trim();
        if (cleanStr.contains("T")) {
            cleanStr = cleanStr.substring(0, cleanStr.indexOf("T"));
        } else if (cleanStr.contains(" ")) {
            cleanStr = cleanStr.substring(0, cleanStr.indexOf(" "));
        }

        if (cleanStr.matches("\\d{6}")) {
            return LocalDate.parse(cleanStr, DateTimeFormatter.ofPattern("yyMMdd"));
        }
        if (cleanStr.matches("\\d{8}")) {
            return LocalDate.parse(cleanStr, DateTimeFormatter.ofPattern("yyyyMMdd"));
        }
        if (cleanStr.matches("\\d{4}-\\d{1,2}-\\d{1,2}")) {
            return LocalDate.parse(cleanStr, DateTimeFormatter.ofPattern("yyyy-M-d"));
        }
        if (cleanStr.matches("\\d{1,2}/\\d{1,2}/\\d{4}")) {
            return LocalDate.parse(cleanStr, DateTimeFormatter.ofPattern("d/M/yyyy"));
        }
        try {
            return LocalDate.parse(cleanStr);
        } catch (Exception e) {
            log.warn("Cannot parse date: {}", dateString);
            return null;
        }
    }
}
