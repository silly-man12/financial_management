package com.example.financial_management.model.budget;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BudgetRequest {
    private int category;
    private String description;

    @NotNull(message = "Số tiền ngân sách không được để trống")
    private BigDecimal amount;
    private int month;
    private int year;
    private UUID tagId;
    private List<String> tags;
}

