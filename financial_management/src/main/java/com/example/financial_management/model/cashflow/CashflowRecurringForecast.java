package com.example.financial_management.model.cashflow;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CashflowRecurringForecast {
    /** Tổng thu nhập định kỳ dự kiến còn lại trong tháng */
    private BigDecimal expectedIncome;
    /** Tổng chi tiêu định kỳ dự kiến còn lại trong tháng */
    private BigDecimal expectedExpense;
}
