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

import java.util.UUID;

@Entity
@Table(
        name = "wallet_source_mappings",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_wallet_map_user_prov_src", columnNames = {"user_id", "provider", "source_key"})
        },
        indexes = {
                @Index(name = "idx_wsm_prov_src_active", columnList = "provider, source_key, is_active"),
                @Index(name = "idx_wsm_user", columnList = "user_id")
        }
)
@Getter
@Setter
@Accessors(chain = true)
public class WalletSourceMapping extends EntityBase {

    @Column(name = "user_id", nullable = false, columnDefinition = "uniqueidentifier")
    private UUID userId;

    @Column(name = "provider", nullable = false, length = 30)
    private String provider;

    @Column(name = "source_key", nullable = false, length = 120)
    private String sourceKey;

    @Column(name = "account_id", nullable = false, columnDefinition = "uniqueidentifier")
    private UUID accountId;

    @Column(name = "bank_code", length = 50)
    private String bankCode;

    @Column(name = "label", length = 150, columnDefinition = "nvarchar(150)")
    private String label;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
