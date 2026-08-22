package com.example.financial_management.model.saving_goal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SavingGoalRequest {

    @NotBlank(message = "Tên mục tiêu không được để trống")
    private String name;

    @NotNull(message = "Số tiền mục tiêu không được để trống")
    private BigDecimal targetAmount;

    private BigDecimal initialAmount = BigDecimal.ZERO; // Số tiền ban đầu (mặc định = 0)

    private UUID accountId; // Tài khoản nguồn để trích tiền ban đầu (nếu initialAmount > 0)

    private LocalDate targetDate; // Hạn chót đạt mục tiêu

    private String color; // Màu sắc đại diện (vd: "#4CAF50")

    private String description;
}
