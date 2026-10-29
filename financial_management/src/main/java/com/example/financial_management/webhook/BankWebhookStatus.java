package com.example.financial_management.webhook;

public enum BankWebhookStatus {
    RECEIVED,
    PENDING_MATCH,
    PROCESSED,
    MATCHED,
    DUPLICATE,
    SKIPPED,
    UNPARSED,
    FAILED
}
