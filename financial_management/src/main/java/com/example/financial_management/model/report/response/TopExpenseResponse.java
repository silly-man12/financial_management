package com.example.financial_management.model.report.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Khoản chi tiêu lớn nhất")
public class TopExpenseResponse {

    @Schema(description = "Mã giao dịch", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID id;

    @Schema(description = "Mô tả khoản chi", example = "Mua laptop mới")
    private String description;

    @Schema(description = "Số tiền chi", example = "25000000")
    private BigDecimal amount;

    @Schema(description = "Mã danh mục", example = "7")
    private int category;

    @Schema(description = "Tên danh mục", example = "Shopping")
    private String categoryName;

    @Schema(description = "Mã tài khoản sử dụng", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID accountId;

    @Schema(description = "Tên tài khoản", example = "TP Bank")
    private String accountName;

    @Schema(description = "Thời gian tạo giao dịch")
    private LocalDateTime createdAt;
}
