package com.example.financial_management.model.saving_goal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SavingGoalContributionRequest {

    @NotNull(message = "Số tiền không được để trống")
    @DecimalMin(value = "0.01", message = "Số tiền phải lớn hơn 0")
    private BigDecimal amount;

    private LocalDate contributionDate; // Mặc định là ngày hôm nay nếu null

    private Integer type; // 1: Nạp tiền (DEPOSIT), 2: Rút tiền (WITHDRAW). Mặc định là 1 nếu null

    private UUID accountId; // Tài khoản trích tiền hoặc nhận tiền

    private String note;
}
