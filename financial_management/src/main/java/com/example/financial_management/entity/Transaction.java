package com.example.financial_management.entity;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.example.financial_management.constant.Currency;
import com.example.financial_management.entity.base.EntityBase;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "transactions")
@Getter
@Setter
public class Transaction extends EntityBase {

    @Column(name = "account_id", nullable = true, columnDefinition = "uniqueidentifier")
    private UUID accountId;

    @Column(name = "user_id", nullable = false, columnDefinition = "uniqueidentifier")
    private UUID userId;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "type", nullable = false)
    private int type;

    @Column(name = "category")
    private int category;

    @Column(name = "currency")
    private int currency = Currency.VND;

    @Column(name = "description", length = 255, columnDefinition = "nvarchar(255)")
    private String description;

    @Column(name = "image_path", length = 500)
    private String imagePath;

    @Column(name = "have_image", nullable = false)
    private boolean haveImage = false;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "transaction_tags",
        joinColumns = @JoinColumn(name = "transaction_id", referencedColumnName = "id"),
        inverseJoinColumns = @JoinColumn(name = "tag_id", referencedColumnName = "id")
    )
    private Set<Tag> tags = new HashSet<>();
}

