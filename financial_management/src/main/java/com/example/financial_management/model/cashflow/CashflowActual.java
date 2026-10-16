package com.example.financial_management.model.cashflow;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CashflowActual {
    /** Tổng thu nhập thực đã phát sinh từ đầu tháng đến hôm nay */
    private BigDecimal income;
    /** Tổng chi tiêu thực đã phát sinh từ đầu tháng đến hôm nay */
    private BigDecimal expense;
    /** Thu - Chi (có thể âm) */
    private BigDecimal net;
}
