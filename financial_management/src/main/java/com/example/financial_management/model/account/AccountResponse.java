package com.example.financial_management.model.account;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import lombok.Data;

@Data
public class AccountResponse {
    private UUID id;
    private String name;
    private int type;
    private int currency;
    private int status;
    private String description;
    private UUID userId;
    private BigDecimal balance;
    private BigDecimal balanceUsd;
    private BigDecimal exchangeRate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
