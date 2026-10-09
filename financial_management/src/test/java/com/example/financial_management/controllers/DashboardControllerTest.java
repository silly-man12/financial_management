package com.example.financial_management.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.example.financial_management.model.AbstractResponse;
import com.example.financial_management.model.account.AccountResponse;
import com.example.financial_management.model.auth.Auth;
import com.example.financial_management.model.budget.BudgetSummaryResponse;
import com.example.financial_management.model.dashboard.DashboardResponse;
import com.example.financial_management.model.report.response.AnalyticsKpiResponse;
import com.example.financial_management.model.report.response.AnalyticsReportResponse;
import com.example.financial_management.model.transaction.TransactionResponse;
import com.example.financial_management.services.AccountService;
import com.example.financial_management.services.BudgetService;
import com.example.financial_management.services.DashboardService;
import com.example.financial_management.services.ReportService;
import com.example.financial_management.services.TransactionService;

class DashboardControllerTest {

    private ReportService reportService;
    private AccountService accountService;
    private BudgetService budgetService;
    private TransactionService transactionService;
    private DashboardService dashboardService;
    private DashboardController dashboardController;
    private Auth auth;

    @BeforeEach
    void setUp() {
        reportService = mock(ReportService.class);
        accountService = mock(AccountService.class);
        budgetService = mock(BudgetService.class);
        transactionService = mock(TransactionService.class);

        dashboardService = new DashboardService(reportService, accountService, budgetService, transactionService);
        dashboardController = new DashboardController(dashboardService);

        auth = new Auth();
        auth.setId(UUID.randomUUID().toString());
        auth.setEmail("tester@example.com");
        auth.setName("Tester");
        auth.setRole(1);
    }

    @Test
    @DisplayName("DashboardService properly aggregates KPI, charts, accounts, budgets, and transactions")
    void testDashboardServiceAggregation() {
        AnalyticsKpiResponse kpi = AnalyticsKpiResponse.builder()
                .totalIncome(BigDecimal.valueOf(5000000))
                .totalExpense(BigDecimal.valueOf(2000000))
                .netIncome(BigDecimal.valueOf(3000000))
                .savingsRate(60.0)
                .build();

        AnalyticsReportResponse reportResponse = AnalyticsReportResponse.builder()
                .kpi(kpi)
                .chart(Collections.emptyList())
                .build();

        AccountResponse account = new AccountResponse();
        account.setId(UUID.randomUUID());
        account.setName("Ví chính");
        account.setBalance(BigDecimal.valueOf(10000000));
        List<AccountResponse> accounts = List.of(account);

        BudgetSummaryResponse budgetSummary = BudgetSummaryResponse.builder()
                .month(10)
                .year(2026)
                .totalBudgetAmount(BigDecimal.valueOf(3000000))
                .totalSpendingAmount(BigDecimal.valueOf(1500000))
                .build();

        TransactionResponse tx = new TransactionResponse();
        tx.setId(UUID.randomUUID());
        tx.setAmount(BigDecimal.valueOf(50000));
        tx.setDescription("Ăn trưa");
        List<TransactionResponse> recentTransactions = List.of(tx);

        when(reportService.getAnalyticsReport(eq(auth), eq(null), eq("2026-10-01"), eq("2026-10-31")))
                .thenReturn(reportResponse);
        when(accountService.getAllAccounts(auth)).thenReturn(accounts);
        when(budgetService.getBudgetSummary(10, 2026, auth)).thenReturn(budgetSummary);
        when(transactionService.getRecentTransactions(auth, 6)).thenReturn(recentTransactions);

        DashboardResponse result = dashboardService.getDashboard(10, 2026, auth);

        assertNotNull(result);
        assertEquals(kpi, result.getKpi());
        assertEquals(0, result.getChart().size());
        assertEquals(1, result.getAccounts().size());
        assertEquals(budgetSummary, result.getBudgetSummary());
        assertEquals(1, result.getRecentTransactions().size());

        verify(reportService).getAnalyticsReport(eq(auth), eq(null), eq("2026-10-01"), eq("2026-10-31"));
        verify(accountService).getAllAccounts(auth);
        verify(budgetService).getBudgetSummary(10, 2026, auth);
        verify(transactionService).getRecentTransactions(auth, 6);
    }

    @Test
    @DisplayName("DashboardService defaults to current month and year when parameters are null")
    void testDashboardServiceDefaults() {
        LocalDate now = LocalDate.now();
        when(reportService.getAnalyticsReport(any(), any(), any(), any()))
                .thenReturn(AnalyticsReportResponse.builder().build());
        when(accountService.getAllAccounts(any())).thenReturn(Collections.emptyList());
        when(budgetService.getBudgetSummary(eq(now.getMonthValue()), eq(now.getYear()), any()))
                .thenReturn(BudgetSummaryResponse.builder().build());
        when(transactionService.getRecentTransactions(any(), eq(6))).thenReturn(Collections.emptyList());

        DashboardResponse result = dashboardService.getDashboard(null, null, auth);

        assertNotNull(result);
        verify(budgetService).getBudgetSummary(eq(now.getMonthValue()), eq(now.getYear()), eq(auth));
    }

    @Test
    @DisplayName("DashboardController endpoint returns 200 OK and wrapped AbstractResponse")
    void testDashboardControllerEndpoint() {
        AnalyticsReportResponse reportResponse = AnalyticsReportResponse.builder()
                .kpi(AnalyticsKpiResponse.builder().build())
                .chart(Collections.emptyList())
                .build();

        when(reportService.getAnalyticsReport(any(), any(), any(), any())).thenReturn(reportResponse);
        when(accountService.getAllAccounts(any())).thenReturn(Collections.emptyList());
        when(budgetService.getBudgetSummary(any(Integer.class), any(Integer.class), any()))
                .thenReturn(BudgetSummaryResponse.builder().build());
        when(transactionService.getRecentTransactions(any(), eq(6))).thenReturn(Collections.emptyList());

        ResponseEntity<AbstractResponse<DashboardResponse>> response = dashboardController.getDashboard(10, 2026, auth);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals(200, response.getBody().getCode());
        assertNotNull(response.getBody().getData());
    }
}
