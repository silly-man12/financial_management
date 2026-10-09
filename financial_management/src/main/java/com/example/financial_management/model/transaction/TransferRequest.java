package com.example.financial_management.model.transaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import lombok.Data;

@Data
public class TransferRequest {
    @NotNull(message = "Tài khoản nguồn không được để trống")
    private UUID accountId;

    @NotNull(message = "Tài khoản đích không được để trống")
    private UUID targetAccountId;

    @NotNull(message = "Số tiền chuyển không được để trống")
    @DecimalMin(value = "0.0", inclusive = false, message = "Số tiền chuyển phải lớn hơn 0")
    private BigDecimal amount;
    private String description;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime createAt;
}
