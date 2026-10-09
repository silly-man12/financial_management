package com.example.financial_management.config;

import com.example.financial_management.model.transaction.TransactionResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class CorsAndResourceHandlerTest {

    @Autowired
    private CorsConfigurationSource corsConfigurationSource;

    @Test
    @DisplayName("CORS should allow specific origins (localhost:5173, etc.) and disallow wildcard * with credentials")
    void testCorsAllowedOrigins() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/transactions/all");

        CorsConfiguration config = corsConfigurationSource.getCorsConfiguration(request);
        assertNotNull(config, "CORS configuration should be present");

        List<String> allowedOrigins = config.getAllowedOrigins();
        assertNotNull(allowedOrigins);
        assertTrue(allowedOrigins.contains("http://localhost:5173"), "Should allow Vue dev server origin http://localhost:5173");
        assertFalse(allowedOrigins.contains("*"), "Allowed origins should NOT be wildcard *");
        assertTrue(config.getAllowCredentials(), "Should allow credentials for JWT auth");
    }

    @Test
    @DisplayName("TransactionResponse should support and retain imageUrl property")
    void testTransactionResponseImageUrl() {
        TransactionResponse response = new TransactionResponse();
        response.setImagePath("images/test_invoice.jpg");
        response.setImageUrl("http://localhost:8080/images/test_invoice.jpg");

        assertEquals("images/test_invoice.jpg", response.getImagePath());
        assertEquals("http://localhost:8080/images/test_invoice.jpg", response.getImageUrl());
    }
}
