package com.example.financial_management.model.debt;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import lombok.Data;

@Data
public class DebtPaymentResponse {
    private UUID id;
    private UUID debtId;
    private BigDecimal amount;
    private LocalDate paymentDate;
    private UUID accountId;
    private UUID transactionId;
    private String note;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
