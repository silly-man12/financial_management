package com.example.financial_management.entity;

import com.example.financial_management.entity.base.EntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "bank_webhook_events",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_webhook_provider_ext_id", columnNames = {"provider", "external_id"})
        },
        indexes = {
                @Index(name = "idx_bwe_user_status", columnList = "user_id, status"),
                @Index(name = "idx_bwe_user_occurred", columnList = "user_id, occurred_at"),
                @Index(name = "idx_bwe_status_created", columnList = "status, created_at")
        }
)
@Getter
@Setter
@Accessors(chain = true)
public class BankWebhookEvent extends EntityBase {

    @Column(name = "user_id", nullable = true, columnDefinition = "uniqueidentifier")
    private UUID userId;

    @Column(name = "provider", nullable = false, length = 30)
    private String provider;

    @Column(name = "external_id", nullable = false, length = 120)
    private String externalId;

    @Column(name = "source_key", length = 120)
    private String sourceKey;

    @Column(name = "gateway", length = 100, columnDefinition = "nvarchar(100)")
    private String gateway;

    @Column(name = "direction", length = 10)
    private String direction;

    @Column(name = "event_kind", length = 30)
    private String eventKind;

    @Column(name = "funding_source", length = 30)
    private String fundingSource;

    @Column(name = "amount")
    private BigDecimal amount;

    @Column(name = "bank_balance")
    private BigDecimal bankBalance;

    @Column(name = "content", length = 500, columnDefinition = "nvarchar(500)")
    private String content;

    @Column(name = "counterparty", length = 255, columnDefinition = "nvarchar(255)")
    private String counterparty;

    @Column(name = "reference_code", length = 120)
    private String referenceCode;

    @Column(name = "occurred_at")
    private LocalDateTime occurredAt;

    @Column(name = "raw_payload", columnDefinition = "nvarchar(max)")
    private String rawPayload;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "error_message", length = 500, columnDefinition = "nvarchar(500)")
    private String errorMessage;

    @Column(name = "transaction_id", columnDefinition = "uniqueidentifier")
    private UUID transactionId;

    @Column(name = "matched_event_id", columnDefinition = "uniqueidentifier")
    private UUID matchedEventId;
}
