package com.example.financial_management.model.dashboard;

import java.util.List;

import com.example.financial_management.model.account.AccountResponse;
import com.example.financial_management.model.budget.BudgetSummaryResponse;
import com.example.financial_management.model.report.response.AnalyticsChartPoint;
import com.example.financial_management.model.report.response.AnalyticsKpiResponse;
import com.example.financial_management.model.transaction.TransactionResponse;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardResponse {

    @Schema(description = "Chỉ số tài chính chủ chốt (KPI) của tháng")
    private AnalyticsKpiResponse kpi;

    @Schema(description = "Chuỗi điểm dữ liệu biểu đồ xu hướng dòng tiền theo ngày")
    private List<AnalyticsChartPoint> chart;

    @Schema(description = "Danh sách tài khoản / ví ngân hàng và số dư")
    private List<AccountResponse> accounts;

    @Schema(description = "Tổng hợp ngân sách chi tiêu và tỷ lệ đã sử dụng (%)")
    private BudgetSummaryResponse budgetSummary;

    @Schema(description = "Danh sách các giao dịch gần đây nhất của người dùng")
    private List<TransactionResponse> recentTransactions;
}
