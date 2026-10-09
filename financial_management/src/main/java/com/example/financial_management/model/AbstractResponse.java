package com.example.financial_management.model;

import java.util.List;
import java.util.function.Supplier;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

@Getter
@Setter
@Accessors(chain = true)
@Slf4j
public class AbstractResponse<T> {
    @Schema(description = "Response data")
    private T data;

    @Schema(description = "Indicates if the request was successful")
    private boolean success = true;

    @Schema(description = "Response code")
    private int code = 200;

    @Schema(description = "Response message")
    private String message;

    @Schema(description = "Execution time in seconds", example = "null")
    private double executionTimeInSeconds;

    @Schema(description = "Errors", example = "null")
    private List<ResponseError> errors;

    public ResponseEntity<AbstractResponse<T>> withData(Supplier<T> function) {
        long start = System.currentTimeMillis();
        T result = function.get();
        long processTime = System.currentTimeMillis() - start;

        if (processTime > 300) {
            log.warn("{} ms to get {} from {}", processTime, getName(result), getName(function));
        }

        this.setExecutionTimeInSeconds(processTime / 1000.0);

        if (result != null) {
            this.setSuccess(true)
                .setCode(HttpStatus.OK.value())
                .setData(result);
        } else {
            this.setSuccess(false)
                .setCode(HttpStatus.NOT_FOUND.value())
                .setMessage("No data available");
        }

        int httpStatus = this.isSuccess() ? HttpStatus.OK.value() : (this.getCode() != 0 ? this.getCode() : HttpStatus.NOT_FOUND.value());
        return ResponseEntity
                .status(httpStatus)
                .body(this);
    }

    public static <T> AbstractResponse<T> ok(T data) {
        return new AbstractResponse<T>()
                .setSuccess(true)
                .setCode(HttpStatus.OK.value())
                .setData(data);
    }

    public static <T> AbstractResponse<T> error(int code, String message) {
        return new AbstractResponse<T>()
                .setSuccess(false)
                .setCode(code)
                .setMessage(message);
    }

    private static String getName(Object o) {
        if (o == null) return "null";
        Class<?> clazz = o.getClass();
        String pkg = clazz.getPackageName();
        return clazz.getName().substring(pkg.length() + 1);
    }
}
