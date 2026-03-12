package com.example.financial_management.entity;

import java.math.BigDecimal;
import java.util.UUID;

import com.example.financial_management.entity.base.EntityBase;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
}
