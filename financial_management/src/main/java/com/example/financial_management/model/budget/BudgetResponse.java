package com.example.financial_management.model.budget;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.example.financial_management.model.tag.TagResponse;
import lombok.Data;

@Data
public class BudgetResponse {
    private UUID id;
    private UUID userId;
    private int category;
    private String description;
    private BigDecimal amount;
    private BigDecimal amountUsd;
    private String monthYear;
    private UUID tagId;
    private String tagName;
    private String tagColor;
    private List<TagResponse> tags;
}

