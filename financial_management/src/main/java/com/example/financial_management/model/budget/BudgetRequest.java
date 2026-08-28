package com.example.financial_management.model.budget;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import lombok.Data;

@Data
public class BudgetRequest {
    private int category;
    private String description;
    private BigDecimal amount;
    private int month;
    private int year;
    private UUID tagId;
    private List<String> tags;
}

