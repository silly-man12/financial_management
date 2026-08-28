package com.example.financial_management.entity;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

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
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
@Table(name = "budgets")
@Entity
public class Budget extends EntityBase {
    @Column(name = "user_id", nullable = false, columnDefinition = "uniqueidentifier")
    private UUID userId;
    @Column(name = "category", nullable = false)
    private int category;
    @Column(name = "description", length = 255, nullable = true, columnDefinition = "nvarchar(255)")
    private String description;
    @Column(name = "amount", nullable = false)
    private BigDecimal amount;
    @Column(name = "month", nullable = false)
    private int month;
    @Column(name = "year", nullable = false)
    private int year;

    @Column(name = "tag_id", nullable = true, columnDefinition = "uniqueidentifier")
    private UUID tagId;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "budget_tags",
        joinColumns = @JoinColumn(name = "budget_id", referencedColumnName = "id"),
        inverseJoinColumns = @JoinColumn(name = "tag_id", referencedColumnName = "id")
    )
    private Set<Tag> tags = new HashSet<>();
}

