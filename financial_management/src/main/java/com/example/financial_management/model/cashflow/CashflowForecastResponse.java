package com.example.financial_management.model.cashflow;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CashflowForecastResponse {
    /** Ngày hôm nay */
    private LocalDate today;
    /** Số ngày còn lại trong tháng (không tính hôm nay) */
    private int daysRemaining;
    /** Số ngày đã trôi qua trong tháng (tính cả hôm nay) */
    private int daysElapsed;
    /** Tổng số ngày trong tháng */
    private int totalDaysInMonth;

    /** Tổng số dư hiện tại trên tất cả tài khoản */
    private BigDecimal currentBalance;

    /** Thực thu/chi từ đầu tháng đến hôm nay */
    private CashflowActual actualSoFar;

    /**
     * Danh sách các giao dịch định kỳ dự kiến xảy ra từ hôm nay+1 đến cuối tháng
     */
    private List<RecurringScheduledItem> recurringScheduled;

    /** Tổng thu/chi từ recurring sẽ xảy ra đến cuối tháng */
    private CashflowRecurringForecast recurringForecast;

    /** Dự báo tổng quan cuối tháng */
    private CashflowForecastSummary forecast;

    /** So sánh dự kiến chi tiêu với ngân sách từng danh mục */
    private List<BudgetForecastItem> budgetComparison;
}
