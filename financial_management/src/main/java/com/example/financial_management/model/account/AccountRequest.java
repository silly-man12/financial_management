package com.example.financial_management.model.account;

import java.math.BigDecimal;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AccountRequest {
    @NotBlank(message = "Tên tài khoản không được để trống")
    private String name;
    private int type;
    private int currency;
    private String description;
    private BigDecimal initialBalance = BigDecimal.ZERO;
}
