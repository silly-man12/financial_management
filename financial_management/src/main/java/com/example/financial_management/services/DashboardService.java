package com.example.financial_management.services;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.financial_management.model.account.AccountResponse;
import com.example.financial_management.model.auth.Auth;
import com.example.financial_management.model.budget.BudgetSummaryResponse;
import com.example.financial_management.model.dashboard.DashboardResponse;
import com.example.financial_management.model.report.response.AnalyticsReportResponse;
import com.example.financial_management.model.transaction.TransactionResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final ReportService reportService;
    private final AccountService accountService;
    private final BudgetService budgetService;
    private final TransactionService transactionService;

    public DashboardResponse getDashboard(Integer month, Integer year, Auth auth) {
        LocalDate now = LocalDate.now();
        int queryMonth = (month != null && month >= 1 && month <= 12) ? month : now.getMonthValue();
        int queryYear = (year != null && year >= 1970 && year <= 2100) ? year : now.getYear();

        YearMonth ym = YearMonth.of(queryYear, queryMonth);
        String startDateStr = ym.atDay(1).toString();
        String endDateStr = ym.atEndOfMonth().toString();

        log.debug("Fetching dashboard data for user {}, month {}/{}", auth != null ? auth.getUUID() : null, queryMonth, queryYear);

        AnalyticsReportResponse analytics = reportService.getAnalyticsReport(auth, null, startDateStr, endDateStr);
        List<AccountResponse> accounts = accountService.getAllAccounts(auth);
        BudgetSummaryResponse budgetSummary = budgetService.getBudgetSummary(queryMonth, queryYear, auth);
        List<TransactionResponse> recentTransactions = transactionService.getRecentTransactions(auth, 6);

        return DashboardResponse.builder()
                .kpi(analytics != null ? analytics.getKpi() : null)
                .chart(analytics != null ? analytics.getChart() : null)
                .accounts(accounts)
                .budgetSummary(budgetSummary)
                .recentTransactions(recentTransactions)
                .build();
    }
}
