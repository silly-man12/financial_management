package com.example.financial_management.webhook.adapter;

import com.example.financial_management.webhook.BankTransactionEvent;
import com.example.financial_management.webhook.BankWebhookProvider;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;

public interface BankPayloadAdapter {

    ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    List<DateTimeFormatter> COMMON_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME
    );

    BankWebhookProvider getProvider();

    List<BankTransactionEvent> adapt(String rawBody);

    static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute SHA-256", e);
        }
    }

    static LocalDateTime parseDateTimeFlexible(String raw) {
        if (raw == null || raw.isBlank()) {
            return LocalDateTime.now(VN_ZONE);
        }
        String trimmed = raw.trim();
        try {
            if (trimmed.matches("^\\d+$")) {
                long epoch = Long.parseLong(trimmed);
                if (epoch < 100_000_000_000L) {
                    epoch *= 1000L;
                }
                return LocalDateTime.ofInstant(Instant.ofEpochMilli(epoch), VN_ZONE);
            }
            if (trimmed.endsWith("Z") || trimmed.matches(".*[+-]\\d{2}:?\\d{2}$")) {
                return LocalDateTime.ofInstant(Instant.parse(trimmed), VN_ZONE);
            }
        } catch (Exception ignored) {
        }

        for (DateTimeFormatter fmt : COMMON_FORMATTERS) {
            try {
                return LocalDateTime.parse(trimmed, fmt);
            } catch (Exception ignored) {
            }
        }
        return LocalDateTime.now(VN_ZONE);
    }
}
