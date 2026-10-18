package com.example.financial_management.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class FlexibleLocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private static final DateTimeFormatter[] FORMATTERS = {
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
    };

    @Override
    public Class<?> handledType() {
        return LocalDateTime.class;
    }

    @Override
    public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        if (p.currentToken() == JsonToken.VALUE_NUMBER_INT) {
            long epochMilli = p.getLongValue();
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMilli), DEFAULT_ZONE);
        }

        String str = p.getText();
        if (str == null || str.trim().isEmpty()) {
            return null;
        }
        str = str.trim();

        // 1. Parse ISO Instant with UTC / 'Z' (e.g. "2026-10-15T18:12:00.000Z", "2026-10-15T18:12:00Z")
        try {
            Instant instant = Instant.parse(str);
            return LocalDateTime.ofInstant(instant, DEFAULT_ZONE);
        } catch (DateTimeParseException ignored) {
        }

        // 2. Parse ISO with offset (e.g. "2026-10-16T01:12:00+07:00")
        try {
            OffsetDateTime odt = OffsetDateTime.parse(str);
            return odt.atZoneSameInstant(DEFAULT_ZONE).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
        }

        // 3. Parse with standard local datetime formatters
        for (DateTimeFormatter formatter : FORMATTERS) {
            try {
                return LocalDateTime.parse(str, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }

        // 4. Fallback: Parse date-only string (e.g. "2026-10-15") to start of day
        try {
            LocalDate date = LocalDate.parse(str, DateTimeFormatter.ISO_LOCAL_DATE);
            return date.atStartOfDay();
        } catch (DateTimeParseException ignored) {
        }

        throw new IOException("Cannot parse LocalDateTime from value: " + str);
    }
}
