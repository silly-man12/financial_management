package com.example.financial_management.model.budget;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class BudgetCheckingResponse {
    private String id;
    private int category;
    private String description;
    private BigDecimal amount;
    private BigDecimal spending;
    private BigDecimal overSpending;
}
