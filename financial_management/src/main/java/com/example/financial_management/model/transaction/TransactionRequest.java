package com.example.financial_management.model.transaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonAlias;
import org.springframework.format.annotation.DateTimeFormat;
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
    private List<String> tags;

    @JsonAlias({"createdAt", "created_at"})
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime createAt;
}

