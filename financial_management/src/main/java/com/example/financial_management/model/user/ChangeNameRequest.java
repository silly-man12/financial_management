package com.example.financial_management.model.user;

import jakarta.validation.constraints.Email;
import lombok.Data;

@Data
public class ChangeNameRequest {
    private String name;

    @Email(message = "Email không đúng định dạng")
    private String email;
}
