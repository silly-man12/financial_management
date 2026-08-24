package com.example.financial_management.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.example.financial_management.constant.DebtStatus;
import com.example.financial_management.constant.DebtType;
import com.example.financial_management.entity.base.EntityBase;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "debts")
@Getter
@Setter
public class Debt extends EntityBase {

    @Column(name = "user_id", nullable = false, columnDefinition = "uniqueidentifier")
    private UUID userId;

    @Column(name = "type", nullable = false)
    private int type = DebtType.BORROW; // 1: Đi vay, 2: Cho vay

    @Column(name = "person_name", nullable = false, length = 150, columnDefinition = "nvarchar(150)")
    private String personName;

    @Column(name = "phone_number", length = 30)
    private String phoneNumber;

    @Column(name = "initial_amount", nullable = false)
    private BigDecimal initialAmount;

    @Column(name = "remaining_amount", nullable = false)
    private BigDecimal remainingAmount;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "due_date")
    private LocalDate dueDate; // Ngày hẹn trả (tùy chọn)

    @Column(name = "status", nullable = false)
    private int status = DebtStatus.IN_PROGRESS; // 1: IN_PROGRESS, 2: PAID, 3: OVERDUE

    @Column(name = "note", length = 255, columnDefinition = "nvarchar(255)")
    private String note;
}
