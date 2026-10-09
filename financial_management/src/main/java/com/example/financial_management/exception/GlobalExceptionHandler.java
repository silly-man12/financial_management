package com.example.financial_management.exception;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

import com.example.financial_management.model.AbstractResponse;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // 1. Lỗi Validation (@Valid trên @RequestBody)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ResponseEntity<AbstractResponse<Map<String, String>>> handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Invalid value",
                        (existing, replacement) -> existing,
                        LinkedHashMap::new
                ));

        log.warn("Validation error: {}", fieldErrors);

        AbstractResponse<Map<String, String>> response = new AbstractResponse<Map<String, String>>()
                .setSuccess(false)
                .setCode(HttpStatus.UNPROCESSABLE_ENTITY.value())
                .setMessage("Validation failed")
                .setData(fieldErrors);

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
    }

    // 2. Lỗi Validation (@Validated trên @RequestParam / @PathVariable)
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<AbstractResponse<Map<String, String>>> handleConstraintViolation(ConstraintViolationException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            errors.putIfAbsent(violation.getPropertyPath().toString(), violation.getMessage());
        }

        log.warn("Constraint violation: {}", errors);

        AbstractResponse<Map<String, String>> response = new AbstractResponse<Map<String, String>>()
                .setSuccess(false)
                .setCode(HttpStatus.BAD_REQUEST.value())
                .setMessage("Validation failed")
                .setData(errors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    // 3. Lỗi ResponseStatusException (được dùng phổ biến trong Service)
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<AbstractResponse<Void>> handleResponseStatusException(ResponseStatusException ex) {
        int statusCode = ex.getStatusCode().value();
        String message = ex.getReason() != null ? ex.getReason() : ex.getMessage();

        log.warn("ResponseStatusException [{}]: {}", statusCode, message);

        AbstractResponse<Void> response = new AbstractResponse<Void>()
                .setSuccess(false)
                .setCode(statusCode)
                .setMessage(message);

        return ResponseEntity.status(ex.getStatusCode()).body(response);
    }

    // 4. Lỗi tham số nghiệp vụ (IllegalArgumentException / IllegalStateException)
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<AbstractResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("IllegalArgumentException: {}", ex.getMessage());

        AbstractResponse<Void> response = new AbstractResponse<Void>()
                .setSuccess(false)
                .setCode(HttpStatus.BAD_REQUEST.value())
                .setMessage(ex.getMessage());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<AbstractResponse<Void>> handleIllegalState(IllegalStateException ex) {
        log.warn("IllegalStateException: {}", ex.getMessage());

        AbstractResponse<Void> response = new AbstractResponse<Void>()
                .setSuccess(false)
                .setCode(HttpStatus.BAD_REQUEST.value())
                .setMessage(ex.getMessage());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    // 5. Lỗi không tìm thấy tài nguyên (EntityNotFoundException / NoSuchElementException)
    @ExceptionHandler({EntityNotFoundException.class, NoSuchElementException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ResponseEntity<AbstractResponse<Void>> handleNotFound(Exception ex) {
        log.warn("EntityNotFound: {}", ex.getMessage());

        AbstractResponse<Void> response = new AbstractResponse<Void>()
                .setSuccess(false)
                .setCode(HttpStatus.NOT_FOUND.value())
                .setMessage(ex.getMessage() != null ? ex.getMessage() : "Resource not found");

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    // 6. Lỗi phân quyền truy cập (AccessDeniedException)
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ResponseEntity<AbstractResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("AccessDenied: {}", ex.getMessage());

        AbstractResponse<Void> response = new AbstractResponse<Void>()
                .setSuccess(false)
                .setCode(HttpStatus.FORBIDDEN.value())
                .setMessage("Access denied");

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    // 7. Lỗi xác thực người dùng (AuthenticationException)
    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ResponseEntity<AbstractResponse<Void>> handleAuthentication(AuthenticationException ex) {
        log.warn("AuthenticationException: {}", ex.getMessage());

        AbstractResponse<Void> response = new AbstractResponse<Void>()
                .setSuccess(false)
                .setCode(HttpStatus.UNAUTHORIZED.value())
                .setMessage(ex.getMessage() != null ? ex.getMessage() : "Authentication failed");

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    // 8. Lỗi JSON request body không hợp lệ (HttpMessageNotReadableException)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<AbstractResponse<Void>> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        log.warn("Malformed JSON request: {}", ex.getMessage());

        AbstractResponse<Void> response = new AbstractResponse<Void>()
                .setSuccess(false)
                .setCode(HttpStatus.BAD_REQUEST.value())
                .setMessage("Malformed request payload");

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    // 9. Lỗi thiếu query parameter bắt buộc
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<AbstractResponse<Void>> handleMissingServletRequestParameter(MissingServletRequestParameterException ex) {
        log.warn("Missing parameter: {}", ex.getParameterName());

        AbstractResponse<Void> response = new AbstractResponse<Void>()
                .setSuccess(false)
                .setCode(HttpStatus.BAD_REQUEST.value())
                .setMessage("Missing required parameter: " + ex.getParameterName());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    // 10. Lỗi sai kiểu dữ liệu query param / path variable
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<AbstractResponse<Void>> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("Type mismatch for param {}: {}", ex.getName(), ex.getMessage());

        AbstractResponse<Void> response = new AbstractResponse<Void>()
                .setSuccess(false)
                .setCode(HttpStatus.BAD_REQUEST.value())
                .setMessage("Invalid format for parameter: " + ex.getName());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    // 11. Lỗi upload file vượt dung lượng
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public ResponseEntity<AbstractResponse<Void>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        log.warn("Max upload size exceeded: {}", ex.getMessage());

        AbstractResponse<Void> response = new AbstractResponse<Void>()
                .setSuccess(false)
                .setCode(HttpStatus.PAYLOAD_TOO_LARGE.value())
                .setMessage("File size exceeds maximum allowed limit");

        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(response);
    }

    // 12. Fallback xử lý mọi exception không mong đợi khác (500 Internal Server Error)
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseEntity<AbstractResponse<Void>> handleGeneral(Exception ex) {
        log.error("Unhandled exception: ", ex);

        AbstractResponse<Void> response = new AbstractResponse<Void>()
                .setSuccess(false)
                .setCode(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .setMessage("Internal server error");

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
