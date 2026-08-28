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
@Schema(description = "Phân bổ và cơ cấu theo danh mục")
public class CategoryDistributionResponse {

    @Schema(description = "Mã danh mục", example = "1")
    private int category;

    @Schema(description = "Tên danh mục", example = "Food")
    private String categoryName;

    @Schema(description = "Tổng số tiền trong danh mục", example = "4500000")
    private BigDecimal total;

    @Schema(description = "Tỷ trọng phần trăm so với tổng kỳ (%)", example = "36.0")
    private Double percentage;

    @Schema(description = "Số lượng giao dịch trong danh mục", example = "18")
    private long transactionCount;

    @Schema(description = "Tăng trưởng/biến động % so với cùng kỳ trước", example = "5.2")
    private Double changeVsPreviousPeriod;
}
