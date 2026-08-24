package com.example.financial_management.model.debt;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DebtPaymentRequest {

    @NotNull(message = "Số tiền trả không được để trống")
    private BigDecimal amount;

    @NotNull(message = "Ngày trả không được để trống")
    private LocalDate paymentDate;

    private UUID accountId; // Tài khoản ví/ngân hàng nhận tiền (nếu thu nợ) hoặc trích tiền (nếu trả nợ)

    private String note;
}
