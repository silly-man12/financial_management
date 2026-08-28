package com.example.financial_management.model.debt;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import lombok.Data;

@Data
public class DebtResponse {
    private UUID id;
    private UUID userId;
    private int type;
    private String typeName; // "Đi vay (Nợ phải trả)" hoặc "Cho vay (Nợ phải thu)"
    private String personName;
    private String phoneNumber;
    private BigDecimal initialAmount;
    private BigDecimal initialAmountUsd;
    private BigDecimal remainingAmount;
    private BigDecimal remainingAmountUsd;
    private BigDecimal paidAmount; // Số tiền đã trả/đã thu (initialAmount - remainingAmount)
    private BigDecimal paidAmountUsd;
    private LocalDate startDate;
    private LocalDate dueDate;
    private int status;
    private String statusName; // "Đang nợ", "Đã trả xong", "Quá hạn"
    private String note;
    private List<DebtPaymentResponse> payments; // Lịch sử các lần trả (kèm theo khi xem chi tiết)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
