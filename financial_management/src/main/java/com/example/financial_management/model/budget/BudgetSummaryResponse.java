package com.example.financial_management.model.budget;

import java.math.BigDecimal;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetSummaryResponse {
    private int month;
    private int year;
    private int totalBudgets;
    private BigDecimal totalBudgetAmount;
    private BigDecimal totalBudgetAmountUsd;
    private BigDecimal totalSpendingAmount;
    private BigDecimal totalSpendingAmountUsd;
    private BigDecimal remainingAmount;
    private BigDecimal remainingAmountUsd;
    private BigDecimal usedPercentage;
    private boolean isOverBudget;
    private List<BudgetCheckingResponse> details;
}
