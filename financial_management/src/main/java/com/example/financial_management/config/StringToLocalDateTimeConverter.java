package com.example.financial_management.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class StringToLocalDateTimeConverter implements Converter<String, LocalDateTime> {

    @Override
    public LocalDateTime convert(String source) {
        if (source == null || source.trim().isEmpty()) {
            return null;
        }
        String trimmed = source.trim();

        // 1. Thử parse dạng ISO OffsetDateTime (ví dụ: 2026-08-27T06:47:00+07:00 hoặc 2026-08-27T06:47:00Z)
        try {
            return OffsetDateTime.parse(trimmed).toLocalDateTime();
        } catch (Exception ignored) {
        }

        // 2. Thử parse dạng ISO LocalDateTime (ví dụ: 2026-08-27T06:47:00 hoặc 2026-08-27T06:47:00.123)
        try {
            return LocalDateTime.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception ignored) {
        }

        // 3. Thử parse dạng standard datetime (ví dụ: 2026-08-27 06:47:00)
        try {
            return LocalDateTime.parse(trimmed, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception ignored) {
        }

        // 4. Thử parse dạng ngày (ví dụ: 2026-08-27)
        try {
            return LocalDate.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay();
        } catch (Exception ignored) {
        }

        throw new IllegalArgumentException("Không thể chuyển đổi chuỗi ngày giờ: " + source);
    }
}
