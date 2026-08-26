package com.example.financial_management.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.example.financial_management.entity.base.EntityBase;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "saving_goal_contributions")
@Getter
@Setter
public class SavingGoalContribution extends EntityBase {

    @Column(name = "saving_goal_id", nullable = false, columnDefinition = "uniqueidentifier")
    private UUID savingGoalId;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "contribution_date", nullable = false)
    private LocalDate contributionDate;

    @Column(name = "type", nullable = false)
    private int type; // 1: Nạp tiền (DEPOSIT), 2: Rút tiền (WITHDRAW)

    @Column(name = "account_id", columnDefinition = "uniqueidentifier")
    private UUID accountId; // Tài khoản ví / ngân hàng nguồn hoặc đích

    @Column(name = "transaction_id", columnDefinition = "uniqueidentifier")
    private UUID transactionId; // ID giao dịch tương ứng trong bảng transactions

    @Column(name = "note", length = 255, columnDefinition = "nvarchar(255)")
    private String note;
}
