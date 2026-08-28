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
@Schema(description = "Điểm dữ liệu biểu đồ xu hướng dòng tiền")
public class AnalyticsChartPoint {

    @Schema(description = "Nhãn hiển thị trên trục hoành (ví dụ: dd/MM)", example = "01/08")
    private String label;

    @Schema(description = "Ngày theo định dạng yyyy-MM-dd", example = "2026-08-01")
    private String date;

    @Schema(description = "Tổng thu trong mốc thời gian này", example = "5000000")
    private BigDecimal income;

    @Schema(description = "Tổng chi trong mốc thời gian này", example = "1200000")
    private BigDecimal expense;

    @Schema(description = "Lợi nhuận ròng (Income - Expense)", example = "3800000")
    private BigDecimal net;
}
