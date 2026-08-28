package com.example.financial_management.model.budget;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.example.financial_management.model.tag.TagResponse;
import lombok.Data;

@Data
public class BudgetCheckingResponse {
    private String id;
    private int category;
    private String description;
    private BigDecimal amount;
    private BigDecimal amountUsd;
    private BigDecimal spending;
    private BigDecimal spendingUsd;
    private BigDecimal overSpending;
    private BigDecimal overSpendingUsd;
    private UUID tagId;
    private String tagName;
    private String tagColor;
    private List<TagResponse> tags;
}

