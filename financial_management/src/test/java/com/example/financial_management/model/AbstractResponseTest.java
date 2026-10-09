package com.example.financial_management.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class AbstractResponseTest {

    @Test
    @DisplayName("AbstractResponse and ResponseError must not contain stackTrace field")
    void testNoStackTraceField() {
        boolean abstractResponseHasStackTrace = Arrays.stream(AbstractResponse.class.getDeclaredFields())
                .anyMatch(field -> "stackTrace".equalsIgnoreCase(field.getName()));
        assertFalse(abstractResponseHasStackTrace, "AbstractResponse should not have a stackTrace field");

        boolean responseErrorHasStackTrace = Arrays.stream(ResponseError.class.getDeclaredFields())
                .anyMatch(field -> "stackTrace".equalsIgnoreCase(field.getName()));
        assertFalse(responseErrorHasStackTrace, "ResponseError should not have a stackTrace field");
    }

    @Test
    @DisplayName("withData returns 200 OK when supplier provides data")
    void testWithDataSuccess() {
        AbstractResponse<String> responseObj = new AbstractResponse<>();
        ResponseEntity<AbstractResponse<String>> responseEntity = responseObj.withData(() -> "Hello World");

        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertNotNull(responseEntity.getBody());
        assertTrue(responseEntity.getBody().isSuccess());
        assertEquals(200, responseEntity.getBody().getCode());
        assertEquals("Hello World", responseEntity.getBody().getData());
    }

    @Test
    @DisplayName("withData returns 404 NOT_FOUND when supplier provides null")
    void testWithDataNullData() {
        AbstractResponse<String> responseObj = new AbstractResponse<>();
        ResponseEntity<AbstractResponse<String>> responseEntity = responseObj.withData(() -> null);

        assertEquals(HttpStatus.NOT_FOUND, responseEntity.getStatusCode());
        assertNotNull(responseEntity.getBody());
        assertFalse(responseEntity.getBody().isSuccess());
        assertEquals(404, responseEntity.getBody().getCode());
        assertEquals("No data available", responseEntity.getBody().getMessage());
    }

    @Test
    @DisplayName("Helper factory methods ok and error work properly")
    void testFactoryMethods() {
        AbstractResponse<Integer> okResp = AbstractResponse.ok(42);
        assertTrue(okResp.isSuccess());
        assertEquals(200, okResp.getCode());
        assertEquals(42, okResp.getData());

        AbstractResponse<Void> errResp = AbstractResponse.error(400, "Bad Request");
        assertFalse(errResp.isSuccess());
        assertEquals(400, errResp.getCode());
        assertEquals("Bad Request", errResp.getMessage());
    }
}
