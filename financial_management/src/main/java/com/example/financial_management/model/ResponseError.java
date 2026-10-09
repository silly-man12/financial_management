package com.example.financial_management.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResponseError {
    @Schema(description = "Error field name, if applicable")
    private String field;

    @Schema(description = "Error message")
    private String message;

    public ResponseError(String message) {
        this.message = message;
    }
}
