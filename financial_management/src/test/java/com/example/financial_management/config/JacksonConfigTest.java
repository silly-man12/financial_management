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

    @Test
    @DisplayName("UTC ISO-8601 strings with 'Z' and milliseconds should deserialize into local Vietnam time")
    void testUtcIsoStringDeserialization() throws Exception {
        // 2026-10-15T18:12:00.000Z in UTC is 2026-10-16T01:12:00 in Asia/Ho_Chi_Minh (UTC+7)
        String json = "{\"dateTime\":\"2026-10-15T18:12:00.000Z\",\"date\":\"2026-10-15T18:12:00.000Z\"}";

        SampleDateDto result = objectMapper.readValue(json, SampleDateDto.class);

        assertEquals(LocalDateTime.of(2026, 10, 16, 1, 12, 0), result.getDateTime());
        assertEquals(LocalDate.of(2026, 10, 16), result.getDate());
    }

    @Test
    @DisplayName("TransferRequest should deserialize successfully with ISO-8601 UTC timestamp")
    void testTransferRequestDeserialization() throws Exception {
        String json = "{"
                + "\"accountId\":\"eccc151e-c811-4298-b726-e05ad997b495\","
                + "\"targetAccountId\":\"c08b28d3-3ee1-4ac9-b610-888f0f497591\","
                + "\"amount\":500000,"
                + "\"description\":\"Chuyen tien\","
                + "\"createAt\":\"2026-10-15T18:12:00.000Z\""
                + "}";

        com.example.financial_management.model.transaction.TransferRequest req =
                objectMapper.readValue(json, com.example.financial_management.model.transaction.TransferRequest.class);

        assertNotNull(req);
        assertEquals(LocalDateTime.of(2026, 10, 16, 1, 12, 0), req.getCreateAt());

        // Also test alias createdAt
        String jsonWithAlias = "{"
                + "\"accountId\":\"eccc151e-c811-4298-b726-e05ad997b495\","
                + "\"targetAccountId\":\"c08b28d3-3ee1-4ac9-b610-888f0f497591\","
                + "\"amount\":500000,"
                + "\"createdAt\":\"2026-10-15T18:12:00.000Z\""
                + "}";

        com.example.financial_management.model.transaction.TransferRequest reqWithAlias =
                objectMapper.readValue(jsonWithAlias, com.example.financial_management.model.transaction.TransferRequest.class);

        assertNotNull(reqWithAlias);
        assertEquals(LocalDateTime.of(2026, 10, 16, 1, 12, 0), reqWithAlias.getCreateAt());
    }
}
