package com.example.financial_management.model.budget;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class BudgetRequest {
    private int category;
    private String description;
    private BigDecimal amount;
    private int month;
    private int year;
}
