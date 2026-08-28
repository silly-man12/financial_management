package com.example.financial_management.model.tag;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TagSummaryResponse {
    private UUID id;
    private String name;
    private String color;
    private BigDecimal totalExpense;
    private BigDecimal totalIncome;
    private BigDecimal balance;
    private int transactionCount;
    private LocalDateTime createdAt;
}
