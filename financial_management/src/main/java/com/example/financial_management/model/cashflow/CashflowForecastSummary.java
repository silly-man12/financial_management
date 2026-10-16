package com.example.financial_management.model.cashflow;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CashflowForecastSummary {
    /**
     * Số dư tài khoản dự kiến cuối tháng
     * = currentBalance + recurringForecastIncome - recurringForecastExpense
     */
    private BigDecimal projectedEndBalance;

    /** Tổng thu nhập dự kiến cả tháng (actual + recurring còn lại) */
    private BigDecimal projectedIncome;

    /** Tổng chi tiêu dự kiến cả tháng (actual + recurring còn lại) */
    private BigDecimal projectedExpense;

    /** Thu - Chi dự kiến */
    private BigDecimal projectedNet;

    /**
     * Số tiền an toàn có thể chi tiêu thêm đến cuối tháng
     * (projectedEndBalance nếu dương, nếu âm thì = 0)
     */
    private BigDecimal safeToSpend;

    /**
     * Mức cảnh báo: "ok" | "caution" | "danger"
     * - ok: safeToSpend > 20% currentBalance
     * - caution: safeToSpend > 0 nhưng <= 20% currentBalance
     * - danger: projectedEndBalance <= 0
     */
    private String warningLevel;
}
