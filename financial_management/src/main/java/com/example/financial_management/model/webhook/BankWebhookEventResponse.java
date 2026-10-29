package com.example.financial_management.model.webhook;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
public class BankWebhookEventResponse {
    private UUID id;
    private UUID userId;
    private String provider;
    private String externalId;
    private String sourceKey;
    private String gateway;
    private String direction;
    private String eventKind;
    private String fundingSource;
    private BigDecimal amount;
    private BigDecimal bankBalance;
    private String content;
    private String counterparty;
    private String referenceCode;
    private LocalDateTime occurredAt;
    private String status;
    private String errorMessage;
    private UUID transactionId;
    private UUID matchedEventId;
    private LocalDateTime createdAt;
}
