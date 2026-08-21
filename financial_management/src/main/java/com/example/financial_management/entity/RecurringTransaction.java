package com.example.financial_management.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.example.financial_management.constant.Currency;
import com.example.financial_management.constant.RecurrenceType;
import com.example.financial_management.constant.Status;
import com.example.financial_management.entity.base.EntityBase;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "recurring_transactions")
@Getter
@Setter
public class RecurringTransaction extends EntityBase {

    @Column(name = "user_id", nullable = false, columnDefinition = "uniqueidentifier")
    private UUID userId;

    @Column(name = "account_id", nullable = true, columnDefinition = "uniqueidentifier")
    private UUID accountId;

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

    // --- Các trường riêng cho giao dịch định kỳ ---

    @Column(name = "recurrence_type", nullable = false)
    private int recurrenceType = RecurrenceType.MONTHLY;

    @Column(name = "recurrence_interval", nullable = false)
    private int recurrenceInterval = 1;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate; // Nullable: null = lặp mãi mãi

    @Column(name = "next_execution_date", nullable = false)
    private LocalDate nextExecutionDate;

    @Column(name = "status", nullable = false)
    private int status = Status.ACTIVE;
}
