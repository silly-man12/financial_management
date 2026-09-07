package com.example.financial_management.entity;

import java.math.BigDecimal;
import java.util.UUID;

import com.example.financial_management.constant.Currency;
import com.example.financial_management.constant.Status;
import com.example.financial_management.entity.base.EntityBase;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter 
@Setter
@Accessors(chain = true)
@Table(name = "accounts")
@Entity
public class Account extends EntityBase {

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @PostLoad
    public void postLoad() {
        if (this.version == null) {
            this.version = 0L;
        }
    }

    @Column(name = "user_id", nullable = false, columnDefinition = "uniqueidentifier")
    private UUID userId;

    @Column(name = "name", nullable = false, length = 100, columnDefinition = "nvarchar(100)")
    private String name;

    @Column(name = "balance", nullable = false)
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(name = "type", nullable = false)
    private int type; // tham chiếu từ AccountType constant

    @Column(name = "currency", nullable = false)
    private int currency = Currency.VND; // default is VND

    @Column(name = "status", nullable = false)
    private int status = Status.ACTIVE; // 1 = ACTIVE, 0 = INACTIVE

    @Column(name = "description", length = 255, nullable = true, columnDefinition = "nvarchar(255)")
    private String description;
}
