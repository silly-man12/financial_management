package com.example.financial_management.webhook;

import java.util.Locale;

public enum BankWebhookProvider {
    SEPAY;

    public static BankWebhookProvider fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return SEPAY;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if ("SEPAY".equals(normalized)) {
            return SEPAY;
        }
        throw new IllegalArgumentException("Unsupported webhook provider: " + raw + " (Only SEPAY is supported)");
    }
}
