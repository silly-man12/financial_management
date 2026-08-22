package com.example.financial_management.model.saving_goal;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SavingGoalDepositRequest {

    @NotNull(message = "Số tiền nạp không được để trống")
    private BigDecimal amount;

    private UUID accountId; // Tài khoản nguồn để trích tiền (tùy chọn)
}
