package com.example.financial_management.model.saving_goal;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SavingGoalUpdateRequest {

    @NotBlank(message = "Tên mục tiêu không được để trống")
    private String name;

    @NotNull(message = "Số tiền mục tiêu không được để trống")
    private BigDecimal targetAmount;

    private LocalDate targetDate; // Hạn chót đạt mục tiêu

    private String color; // Màu sắc đại diện

    private String description;
}
