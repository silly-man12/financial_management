package com.example.financial_management.model.budget;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.Data;

@Data
public class BudgetResponse {
    private UUID userId;
    private int category;
    private String description;
    private BigDecimal amount;
    private String monthYear;
}
