package com.example.financial_management.model.user;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChangeNameRequest {
    @NotBlank(message = "Họ và tên không được để trống")
    private String name;
}
