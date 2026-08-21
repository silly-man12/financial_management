package com.example.financial_management.model.recurring;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import lombok.Data;

@Data
public class RecurringTransactionResponse {
    private UUID id;
    private UUID userId;
    private UUID accountId;

    private BigDecimal amount;
    private int type;
    private int category;
    private int currency;
    private String description;

    private int recurrenceType;
    private int recurrenceInterval;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate nextExecutionDate;
    private int status;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
