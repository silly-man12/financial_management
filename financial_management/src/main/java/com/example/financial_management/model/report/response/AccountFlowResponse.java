package com.example.financial_management.model.report.response;

import java.math.BigDecimal;
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
@Schema(description = "Dòng tiền theo tài khoản ví")
public class AccountFlowResponse {

    @Schema(description = "Mã tài khoản", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID accountId;

    @Schema(description = "Tên tài khoản", example = "TP Bank")
    private String accountName;

    @Schema(description = "Loại tài khoản", example = "2")
    private int accountType;

    @Schema(description = "Tổng dòng tiền vào", example = "15000000")
    private BigDecimal inflow;

    @Schema(description = "Tổng dòng tiền ra", example = "8000000")
    private BigDecimal outflow;

    @Schema(description = "Dòng tiền ròng (Inflow - Outflow)", example = "7000000")
    private BigDecimal netFlow;

    @Schema(description = "Số dư hiện tại của tài khoản", example = "11415000")
    private BigDecimal currentBalance;
}
