package com.example.financial_management.model.webhook;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
public class BankCategoryRuleResponse {
    private UUID id;
    private UUID userId;
    private String keyword;
    private int category;
    private String categoryName;
    private String direction;
    private int priority;
    private String description;
    private boolean active;
    private LocalDateTime createdAt;
}
