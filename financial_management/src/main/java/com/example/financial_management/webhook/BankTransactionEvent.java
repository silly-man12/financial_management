package com.example.financial_management.webhook;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Builder(toBuilder = true)
public record BankTransactionEvent(
        BankWebhookProvider provider,
        String externalId,
        String sourceKey,
        String gateway,
        BankDirection direction,
        BankEventKind eventKind,
        BankFundingSource fundingSource,
        BigDecimal amount,
        BigDecimal bankBalance,
        String content,
        String counterparty,
        String linkedBankHint,
        String referenceCode,
        LocalDateTime occurredAt,
        String rawPayload,
        boolean parsed,
        String parseError
) {
}
