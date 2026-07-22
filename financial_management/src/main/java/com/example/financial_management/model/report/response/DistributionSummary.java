package com.example.financial_management.model.report.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DistributionSummary {
    private BigDecimal startBalance;
    private BigDecimal endBalance;
    private BigDecimal totalIncome;
    private BigDecimal totalExpense;
    private List<CategoryDistribution> incomeByCategory;
    private List<CategoryDistribution> expenseByCategory;
}
