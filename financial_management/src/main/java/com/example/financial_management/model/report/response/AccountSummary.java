package com.example.financial_management.model.report.response;

import java.math.BigDecimal;
import java.util.List;

import com.example.financial_management.model.transaction.TransactionResponse;

import lombok.Data;

@Data
public class AccountSummary {
    private BigDecimal startBalance;
    private BigDecimal endBalance;
    private BigDecimal totalIncome;
    private BigDecimal totalExpense;
    private List<TransactionResponse> balanceHistory;
}
