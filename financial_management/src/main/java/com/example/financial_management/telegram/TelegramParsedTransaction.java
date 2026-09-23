package com.example.financial_management.telegram;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class TelegramParsedTransaction {
    private boolean success;
    private BigDecimal amount;
    private int type; // 0 = EXPENSE, 1 = INCOME
    private int category;
    private String categoryName;
    private String accountKeyword;
    private String description;
    private String errorMessage;
    private String rawInput;
}
