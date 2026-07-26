package com.example.financial_management.model.transaction;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.Data;

@Data
public class TransferRequest {
    private UUID accountId;
    private UUID targetAccountId;
    private BigDecimal amount;
    private String description;
    private OffsetDateTime createAt;
}
