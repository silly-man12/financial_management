package com.example.financial_management.model.webhook;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Builder
public class BankWebhookProcessResult {
    private UUID eventId;
    private String provider;
    private String externalId;
    private String status;
    private String message;
    private UUID transactionId;
    private UUID matchedEventId;
    private UUID accountId;
    private String accountName;
    private BigDecimal amount;
    private Integer category;
    private String categoryName;
    private BigDecimal newAccountBalance;
    private BigDecimal balanceDiscrepancy;
}
