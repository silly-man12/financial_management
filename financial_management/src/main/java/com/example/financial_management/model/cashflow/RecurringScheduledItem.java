package com.example.financial_management.model.cashflow;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RecurringScheduledItem {
    private UUID recurringId;
    private String description;
    private BigDecimal amount;
    private int type;           // 0: EXPENSE, 1: INCOME
    private int category;
    private String categoryName;
    private LocalDate expectedDate;
    private int recurrenceType;
}
