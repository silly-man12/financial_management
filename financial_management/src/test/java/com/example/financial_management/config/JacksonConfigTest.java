package com.example.financial_management.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class JacksonConfigTest {

    @Autowired
    private ObjectMapper objectMapper;

    static class SampleDateDto {
        private LocalDateTime dateTime;
        private LocalDate date;

        public SampleDateDto(LocalDateTime dateTime, LocalDate date) {
            this.dateTime = dateTime;
            this.date = date;
        }

        public LocalDateTime getDateTime() {
            return dateTime;
        }

        public LocalDate getDate() {
            return date;
        }
    }

    @Test
    @DisplayName("LocalDateTime and LocalDate should serialize to ISO-8601 strings, not arrays or timestamps")
    void testLocalDateTimeSerialization() throws Exception {
        LocalDateTime dt = LocalDateTime.of(2026, 10, 9, 14, 30, 0);
        LocalDate d = LocalDate.of(2026, 10, 9);
        SampleDateDto dto = new SampleDateDto(dt, d);

        String json = objectMapper.writeValueAsString(dto);

        assertTrue(json.contains("\"dateTime\":\"2026-10-09T14:30:00\""), 
                "Expected ISO-8601 formatted dateTime string, got: " + json);
        assertTrue(json.contains("\"date\":\"2026-10-09\""), 
                "Expected ISO-8601 formatted date string, got: " + json);
        assertFalse(json.contains("[2026,10,9"), 
                "Date should not be serialized as numeric array");
    }

    @Test
    @DisplayName("ISO-8601 strings should deserialize back into LocalDateTime and LocalDate")
    void testLocalDateTimeDeserialization() throws Exception {
        String json = "{\"dateTime\":\"2026-10-09T14:30:00\",\"date\":\"2026-10-09\"}";

        SampleDateDto result = objectMapper.readValue(json, SampleDateDto.class);

        assertEquals(LocalDateTime.of(2026, 10, 9, 14, 30, 0), result.getDateTime());
        assertEquals(LocalDate.of(2026, 10, 9), result.getDate());
    }
}
