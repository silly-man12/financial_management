package com.example.financial_management.model.transaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import lombok.Data;

@Data
public class TransferRequest {
    private UUID accountId;
    private UUID targetAccountId;
    private BigDecimal amount;
    private String description;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime createAt;
}
