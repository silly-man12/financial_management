package com.example.financial_management.config;

import com.example.financial_management.util.JwtTokenUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AuthFilterTest {

    @Autowired
    private AuthFilter authFilter;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("AuthFilter should return 401 with standardized Jackson JSON when Authorization header is missing")
    void testMissingAuthorizationHeaderReturnsStandardized401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/transactions/all");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        authFilter.doFilterInternal(request, response, filterChain);

        assertEquals(401, response.getStatus());
        assertEquals("application/json;charset=UTF-8", response.getContentType());

        String jsonContent = response.getContentAsString();
        assertNotNull(jsonContent);

        JsonNode rootNode = objectMapper.readTree(jsonContent);
        assertFalse(rootNode.get("success").asBoolean());
        assertEquals(401, rootNode.get("code").asInt());
        assertTrue(rootNode.has("message"));
        assertEquals("Authorization header missing or invalid format", rootNode.get("message").asText());
    }

    @Test
    @DisplayName("AuthFilter should return 401 when token is invalid or malformed")
    void testInvalidTokenReturnsStandardized401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/transactions/all");
        request.addHeader("Authorization", "Bearer invalid-token-12345");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        authFilter.doFilterInternal(request, response, filterChain);

        assertEquals(401, response.getStatus());
        String jsonContent = response.getContentAsString();

        JsonNode rootNode = objectMapper.readTree(jsonContent);
        assertFalse(rootNode.get("success").asBoolean());
        assertEquals(401, rootNode.get("code").asInt());
        assertEquals("Invalid or expired JWT token", rootNode.get("message").asText());
    }
}
