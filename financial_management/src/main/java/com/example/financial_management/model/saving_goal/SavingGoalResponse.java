package com.example.financial_management.model.saving_goal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import lombok.Data;

@Data
public class SavingGoalResponse {
    private UUID id;
    private UUID userId;
    private String name;
    private BigDecimal targetAmount;
    private BigDecimal targetAmountUsd;
    private BigDecimal currentAmount;
    private BigDecimal currentAmountUsd;
    private LocalDate targetDate;
    private String color;
    private String description;
    private int status;
    private double progressPercentage; // Tiến độ % (vd: 50.0, 100.0)
    private List<SavingGoalContributionResponse> contributions; // Lịch sử góp/rút quỹ
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

