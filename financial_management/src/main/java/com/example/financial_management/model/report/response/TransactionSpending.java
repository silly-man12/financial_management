package com.example.financial_management.model.report.response;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class TransactionSpending {
    private int category;
    private BigDecimal spending;
}
