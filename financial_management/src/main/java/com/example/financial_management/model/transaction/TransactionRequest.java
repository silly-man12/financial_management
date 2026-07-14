package com.example.financial_management.model.transaction;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.Data;

@Data
public class TransactionRequest {
    private UUID accountId;
    private BigDecimal amount;
    private int type;
    private int category;
    private int currency;
    private String description;
    private boolean haveImage;
    private OffsetDateTime createAt;
}
