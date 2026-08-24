package com.example.financial_management.model.debt;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DebtUpdateRequest {

    @NotBlank(message = "Tên người vay / cho vay không được để trống")
    private String personName;

    private String phoneNumber;

    private LocalDate dueDate; // Ngày hẹn trả (tùy chọn)

    private String note;
}
