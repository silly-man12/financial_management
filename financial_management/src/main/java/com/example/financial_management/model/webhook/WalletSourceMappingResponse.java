package com.example.financial_management.model.webhook;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
public class WalletSourceMappingResponse {
    private UUID id;
    private UUID userId;
    private String provider;
    private String sourceKey;
    private UUID accountId;
    private String accountName;
    private String bankCode;
    private String label;
    private boolean active;
    private LocalDateTime createdAt;
}
