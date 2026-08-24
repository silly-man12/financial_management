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
@Table(name = "debt_payments")
@Getter
@Setter
public class DebtPayment extends EntityBase {

    @Column(name = "debt_id", nullable = false, columnDefinition = "uniqueidentifier")
    private UUID debtId;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Column(name = "account_id", columnDefinition = "uniqueidentifier")
    private UUID accountId; // Tài khoản ví/ngân hàng nhận tiền hoặc trích tiền

    @Column(name = "note", length = 255, columnDefinition = "nvarchar(255)")
    private String note;

    @Column(name = "transaction_id", columnDefinition = "uniqueidentifier")
    private UUID transactionId; // ID giao dịch tương ứng trong bảng transactions
}
