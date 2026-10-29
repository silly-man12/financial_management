package com.example.financial_management.webhook;

import com.example.financial_management.entity.Account;
import com.example.financial_management.entity.BankWebhookEvent;
import com.example.financial_management.entity.Transaction;
import com.example.financial_management.entity.User;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class BankWebhookProcessedEvent {

    public enum NotificationType {
        NORMAL_TRANSACTION,
        SKIPPED_UNMAPPED
    }

    private final NotificationType notificationType;
    private final User user;
    private final BankWebhookEvent webhookEvent;
    private final Transaction transaction;
    private final Account primaryAccount;
    private final Account secondaryAccount;
    private final BigDecimal balanceDiscrepancy;
}
