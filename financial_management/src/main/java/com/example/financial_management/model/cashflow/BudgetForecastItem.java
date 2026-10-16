package com.example.financial_management.model.cashflow;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BudgetForecastItem {
    private String budgetId;
    private int category;
    private String categoryName;
    /** Hạn mức ngân sách */
    private BigDecimal limit;
    /** Đã chi thực tế đến hôm nay */
    private BigDecimal spent;
    /**
     * Dự kiến chi đến cuối tháng (tính theo tốc độ chi tiêu hiện tại):
     * spent / daysElapsed * totalDaysInMonth
     */
    private BigDecimal forecasted;
    /** true nếu forecasted > limit */
    private boolean willExceed;
}
