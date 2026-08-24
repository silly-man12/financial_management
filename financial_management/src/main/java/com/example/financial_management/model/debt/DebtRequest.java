package com.example.financial_management.model.debt;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DebtRequest {

    @NotBlank(message = "Tên người vay / cho vay không được để trống")
    private String personName;

    @NotNull(message = "Loại khoản nợ không được để trống")
    private Integer type; // 1: Đi vay (Nợ phải trả), 2: Cho vay (Nợ phải thu)

    private String phoneNumber;

    @NotNull(message = "Số tiền ban đầu không được để trống")
    private BigDecimal initialAmount;

    @NotNull(message = "Ngày mượn / cho mượn không được để trống")
    private LocalDate startDate;

    private LocalDate dueDate; // Ngày hẹn trả (tùy chọn)

    private String note;

    private UUID accountId; // Tài khoản ví/ngân hàng nhận tiền vay hoặc trích tiền cho vay (tùy chọn)
}
