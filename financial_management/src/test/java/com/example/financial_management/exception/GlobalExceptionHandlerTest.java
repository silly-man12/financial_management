package com.example.financial_management.exception;

import com.example.financial_management.model.AbstractResponse;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    @DisplayName("Validation error returns 422 with field error map")
    void testValidationErrors() throws NoSuchMethodException {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "target");
        bindingResult.addError(new FieldError("target", "email", "Email is required"));
        bindingResult.addError(new FieldError("target", "amount", "Amount must be greater than 0"));

        MethodParameter parameter = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("setUp"), -1);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);

        ResponseEntity<AbstractResponse<Map<String, String>>> response = handler.handleValidationErrors(ex);

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals(422, response.getBody().getCode());
        assertEquals("Validation failed", response.getBody().getMessage());
        assertEquals("Email is required", response.getBody().getData().get("email"));
        assertEquals("Amount must be greater than 0", response.getBody().getData().get("amount"));
    }

    @Test
    @DisplayName("ResponseStatusException returns matching status code and reason")
    void testResponseStatusException() {
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số tiền nạp phải lớn hơn 0");
        ResponseEntity<AbstractResponse<Void>> response = handler.handleResponseStatusException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals(400, response.getBody().getCode());
        assertEquals("Số tiền nạp phải lớn hơn 0", response.getBody().getMessage());
    }

    @Test
    @DisplayName("IllegalArgumentException returns 400 Bad Request")
    void testIllegalArgumentException() {
        IllegalArgumentException ex = new IllegalArgumentException("Invalid argument provided");
        ResponseEntity<AbstractResponse<Void>> response = handler.handleIllegalArgument(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals(400, response.getBody().getCode());
        assertEquals("Invalid argument provided", response.getBody().getMessage());
    }

    @Test
    @DisplayName("EntityNotFoundException returns 404 Not Found")
    void testEntityNotFoundException() {
        EntityNotFoundException ex = new EntityNotFoundException("User not found");
        ResponseEntity<AbstractResponse<Void>> response = handler.handleNotFound(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals(404, response.getBody().getCode());
        assertEquals("User not found", response.getBody().getMessage());
    }

    @Test
    @DisplayName("AccessDeniedException returns 403 Forbidden")
    void testAccessDeniedException() {
        AccessDeniedException ex = new AccessDeniedException("Forbidden action");
        ResponseEntity<AbstractResponse<Void>> response = handler.handleAccessDenied(ex);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals(403, response.getBody().getCode());
        assertEquals("Access denied", response.getBody().getMessage());
    }

    @Test
    @DisplayName("Unhandled general Exception returns 500 without leaking stack trace")
    void testUnhandledException() {
        Exception ex = new RuntimeException("Sensitive database connection failure at 192.168.1.100");
        ResponseEntity<AbstractResponse<Void>> response = handler.handleGeneral(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals(500, response.getBody().getCode());
        assertEquals("Internal server error", response.getBody().getMessage());
        assertNull(response.getBody().getData());
    }
}
