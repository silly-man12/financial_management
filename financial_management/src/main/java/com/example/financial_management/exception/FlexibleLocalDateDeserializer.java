package com.example.financial_management.exception;

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

public class FlexibleLocalDateDeserializer extends JsonDeserializer<LocalDate> {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private static final String[] PATTERNS = {
            "yyyy-MM-dd",
            "yyyy-M-d",
            "yyyy-MM-d",
            "yyyy-M-dd",
            "yyyy/MM/dd",
            "dd/MM/yyyy"
    };

    @Override
    public Class<?> handledType() {
        return LocalDate.class;
    }

    @Override
    public LocalDate deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        if (p.currentToken() == JsonToken.VALUE_NUMBER_INT) {
            long epochMilli = p.getLongValue();
            return Instant.ofEpochMilli(epochMilli).atZone(DEFAULT_ZONE).toLocalDate();
        }

        String str = p.getText();
        if (str == null || str.trim().isEmpty()) {
            return null;
        }
        str = str.trim();

        // 1. Try date-only patterns
        for (String pattern : PATTERNS) {
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
                return LocalDate.parse(str, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }

        // 2. Try parsing ISO Instant with UTC / 'Z' (e.g. "2026-10-15T18:12:00.000Z")
        try {
            Instant instant = Instant.parse(str);
            return instant.atZone(DEFAULT_ZONE).toLocalDate();
        } catch (DateTimeParseException ignored) {
        }

        // 3. Try parsing ISO with offset (e.g. "2026-10-16T01:12:00+07:00")
        try {
            OffsetDateTime odt = OffsetDateTime.parse(str);
            return odt.atZoneSameInstant(DEFAULT_ZONE).toLocalDate();
        } catch (DateTimeParseException ignored) {
        }

        // 4. Try parsing ISO local datetime (e.g. "2026-10-16T01:12:00")
        try {
            return LocalDateTime.parse(str, DateTimeFormatter.ISO_LOCAL_DATE_TIME).toLocalDate();
        } catch (DateTimeParseException ignored) {
        }

        throw new IOException("Cannot parse LocalDate from value: " + str);
    }
}
