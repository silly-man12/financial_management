package com.example.financial_management.model.recurring;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RecurringTransactionRequest {

    @NotNull(message = "Account ID không được để trống")
    private UUID accountId;

    @NotNull(message = "Số tiền không được để trống")
    private BigDecimal amount;

    private int type;
    private int category;
    private int currency;
    private String description;

    @NotNull(message = "Loại chu kỳ không được để trống")
    private int recurrenceType; // DAILY=1, WEEKLY=2, MONTHLY=3, YEARLY=4

    private int recurrenceInterval = 1; // Mỗi bao nhiêu chu kỳ (mặc định = 1)

    @NotNull(message = "Ngày bắt đầu không được để trống")
    private LocalDate startDate;

    private LocalDate endDate; // Nullable: null = lặp mãi mãi
}
