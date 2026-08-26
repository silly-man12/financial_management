package com.example.financial_management.model.saving_goal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SavingGoalWithdrawRequest {

    @NotNull(message = "Số tiền rút không được để trống")
    private BigDecimal amount;

    private LocalDate contributionDate;

    private UUID accountId; // Tài khoản nhận tiền sau khi rút (tùy chọn)

    private String note;
}

