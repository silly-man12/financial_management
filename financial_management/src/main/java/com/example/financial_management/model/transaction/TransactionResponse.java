package com.example.financial_management.model.transaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import lombok.Data;

@Data
public class TransactionResponse {
    private UUID id;
    private UUID accountId;
    private UUID userId;
    private BigDecimal amount;
    private int type;
    private int currency;
    private int category;
    private String description;
    private String imagePath;
    private boolean haveImage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
