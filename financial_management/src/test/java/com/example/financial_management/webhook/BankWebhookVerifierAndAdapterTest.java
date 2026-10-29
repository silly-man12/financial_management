package com.example.financial_management.webhook;

import com.example.financial_management.config.BankWebhookProperties;
import com.example.financial_management.webhook.adapter.SePayPayloadAdapter;
import com.example.financial_management.webhook.verifier.BankWebhookVerifier;
import com.example.financial_management.webhook.verifier.SePayVerifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BankWebhookVerifierAndAdapterTest {

    private BankWebhookProperties properties;
    private ObjectMapper objectMapper;
    private SePayVerifier sePayVerifier;
    private SePayPayloadAdapter sePayAdapter;

    @BeforeEach
    void setUp() {
        properties = new BankWebhookProperties();
        properties.setEnabled(true);
        properties.setReplayWindowSeconds(300);
        properties.getSepay().setApiKey("nghyqn120104@");

        objectMapper = new ObjectMapper();
        sePayVerifier = new SePayVerifier(properties);
        sePayAdapter = new SePayPayloadAdapter(objectMapper);
    }

    @Test
    @DisplayName("SePayVerifier: Cho phép header Authorization Apikey hợp lệ và chặn key sai")
    void testSePayVerifier() {
        String body = "{\"id\":92704,\"transferAmount\":50000}";

        MockHttpServletRequest validReq = new MockHttpServletRequest();
        validReq.addHeader("Authorization", "Apikey nghyqn120104@");
        assertDoesNotThrow(() -> sePayVerifier.verify(validReq, body));

        // Hỗ trợ cả chữ ký HMAC-SHA256
        MockHttpServletRequest hmacReq = new MockHttpServletRequest();
        String sig = BankWebhookVerifier.computeHmacHex("HmacSHA256", "nghyqn120104@", body);
        hmacReq.addHeader("X-Webhook-Signature", "sha256=" + sig);
        assertDoesNotThrow(() -> sePayVerifier.verify(hmacReq, body));

        MockHttpServletRequest invalidReq = new MockHttpServletRequest();
        invalidReq.addHeader("Authorization", "Apikey wrong_key");
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> sePayVerifier.verify(invalidReq, body));
        assertEquals(401, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("SePayPayloadAdapter: Bóc tách chuẩn xác giao dịch tiền ra (OUT) và tiền vào (IN) của MB Bank qua SePay")
    void testSePayPayloadAdapterForMbBank() {
        String outPayload = """
                {
                  "id": 92704,
                  "gateway": "MBBank",
                  "transactionDate": "2026-10-27 14:02:37",
                  "accountNumber": "0987654321",
                  "content": "ZALOPAY THANH TOAN HIGHLANDS COFFEE 55000",
                  "transferType": "out",
                  "transferAmount": 55000,
                  "accumulated": 4945000,
                  "referenceCode": "FT2630012345"
                }
                """;

        List<BankTransactionEvent> outEvents = sePayAdapter.adapt(outPayload);
        assertEquals(1, outEvents.size());
        BankTransactionEvent outEv = outEvents.get(0);
        assertEquals(BankWebhookProvider.SEPAY, outEv.provider());
        assertEquals("92704", outEv.externalId());
        assertEquals("0987654321", outEv.sourceKey());
        assertEquals("MBBank", outEv.gateway());
        assertEquals(BankDirection.OUT, outEv.direction());
        assertEquals(BankEventKind.PAYMENT, outEv.eventKind());
        assertEquals(0, new BigDecimal("55000").compareTo(outEv.amount()));
        assertEquals(0, new BigDecimal("4945000").compareTo(outEv.bankBalance()));
        assertTrue(outEv.parsed());

        String inPayload = """
                {
                  "id": 92705,
                  "gateway": "MBBank",
                  "transactionDate": "2026-10-27 14:05:10",
                  "accountNumber": "0987654321",
                  "content": "NGUYEN VAN A CHUYEN TIEN",
                  "transferType": "in",
                  "transferAmount": 200000,
                  "accumulated": 5145000,
                  "referenceCode": "FT2630012346"
                }
                """;

        List<BankTransactionEvent> inEvents = sePayAdapter.adapt(inPayload);
        assertEquals(1, inEvents.size());
        BankTransactionEvent inEv = inEvents.get(0);
        assertEquals(BankDirection.IN, inEv.direction());
        assertEquals(BankEventKind.RECEIVE, inEv.eventKind());
        assertEquals(0, new BigDecimal("200000").compareTo(inEv.amount()));
        assertEquals(0, new BigDecimal("5145000").compareTo(inEv.bankBalance()));
    }
}
