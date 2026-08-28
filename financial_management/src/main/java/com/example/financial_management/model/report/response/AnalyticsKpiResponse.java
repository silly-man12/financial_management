package com.example.financial_management.model.report.response;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Chỉ số KPI tài chính trong kỳ")
public class AnalyticsKpiResponse {

    @Schema(description = "Tổng thu nhập trong kỳ", example = "25000000")
    private BigDecimal totalIncome;

    @Schema(description = "Tổng chi tiêu trong kỳ", example = "12500000")
    private BigDecimal totalExpense;

    @Schema(description = "Thu nhập ròng / Lợi nhuận ròng (Income - Expense)", example = "12500000")
    private BigDecimal netIncome;

    @Schema(description = "Tỷ lệ tiết kiệm (%)", example = "50.0")
    private Double savingsRate;

    @Schema(description = "Chi tiêu trung bình mỗi ngày", example = "403225")
    private BigDecimal dailyAverage;

    @Schema(description = "Dự báo tổng chi tiêu cả kỳ", example = "12500000")
    private BigDecimal forecastExpense;

    @Schema(description = "Tỷ lệ tăng trưởng thu nhập so với cùng kỳ trước (%)", example = "12.5")
    private Double incomeGrowthRate;

    @Schema(description = "Tỷ lệ tăng trưởng chi tiêu so với cùng kỳ trước (%)", example = "-5.2")
    private Double expenseGrowthRate;
}
