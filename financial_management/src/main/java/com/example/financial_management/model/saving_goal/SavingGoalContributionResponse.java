package com.example.financial_management.model.saving_goal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import lombok.Data;

@Data
public class SavingGoalContributionResponse {
    private UUID id;
    private UUID savingGoalId;
    private BigDecimal amount;
    private BigDecimal amountUsd;
    private LocalDate contributionDate;
    private int type;
    private String typeName; // "Nạp tiền / Góp quỹ" hoặc "Rút tiền"
    private UUID accountId;
    private UUID transactionId;
    private String note;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
