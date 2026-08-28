package com.example.financial_management.controllers;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.financial_management.model.AbstractResponse;
import com.example.financial_management.model.auth.Auth;
import com.example.financial_management.model.report.request.CategoryReportRequest;
import com.example.financial_management.model.report.request.ReportRequest;
import com.example.financial_management.model.report.request.MonthlyReportRequest;
import com.example.financial_management.model.report.request.SummaryReportRequest;
import com.example.financial_management.model.report.response.AccountFlowResponse;
import com.example.financial_management.model.report.response.AnalyticsReportResponse;
import com.example.financial_management.model.report.response.CategoryDistributionResponse;
import com.example.financial_management.model.report.response.TopExpenseResponse;
import com.example.financial_management.model.report.response.AccountSummary;
import com.example.financial_management.model.report.response.CategoryReportResponse;
import com.example.financial_management.model.report.response.CompareReportResponse;
import com.example.financial_management.model.report.response.DailyReportResponse;
import com.example.financial_management.model.report.response.DistributionSummary;
import com.example.financial_management.model.report.response.MonthlyReportResponse;
import com.example.financial_management.model.report.response.SummaryReportResponse;
import com.example.financial_management.model.transaction.TransactionResponse;
import com.example.financial_management.services.ReportService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
@Tag(name = "Report API", description = "Report and Statistic")
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/chart")
    public ResponseEntity<AbstractResponse<List<TransactionResponse>>> getSummaryByDataRange(
            @Parameter(description = "Start date in format yyMMdd", example = "260301", required = true) @RequestParam String startDate,
            @Parameter(description = "End date in format yyMMdd", example = "260331", required = true) @RequestParam String endDate,
            @Parameter(hidden = true) @AuthenticationPrincipal Auth auth) {
        return new AbstractResponse<List<TransactionResponse>>()
                .withData(() -> reportService.getSummaryByDataRange(auth, startDate, endDate));
    }

    @GetMapping("/analytics")
    @Operation(summary = "Tổng hợp chỉ số KPI & xu hướng dòng tiền vẽ biểu đồ", description = "Trả về 8 chỉ số KPI và danh sách điểm dữ liệu vẽ chart theo ngày")
    public ResponseEntity<AbstractResponse<AnalyticsReportResponse>> getAnalytics(
            @Parameter(description = "Kỳ báo cáo: month, quarter, year, custom", example = "month") @RequestParam(required = false) String period,
            @Parameter(description = "Ngày bắt đầu (yyyy-MM-dd hoặc yyMMdd)", example = "2026-08-01") @RequestParam(required = false) String startDate,
            @Parameter(description = "Ngày kết thúc (yyyy-MM-dd hoặc yyMMdd)", example = "2026-08-31") @RequestParam(required = false) String endDate,
            @Parameter(hidden = true) @AuthenticationPrincipal Auth auth) {
        return new AbstractResponse<AnalyticsReportResponse>()
                .withData(() -> reportService.getAnalyticsReport(auth, period, startDate, endDate));
    }

    @GetMapping("/category-distribution")
    @Operation(summary = "Cơ cấu & phân bổ theo danh mục", description = "Trả về % tỷ trọng, số giao dịch và tăng trưởng theo từng danh mục thu hoặc chi")
    public ResponseEntity<AbstractResponse<List<CategoryDistributionResponse>>> getCategoryDistribution(
            @Parameter(description = "Ngày bắt đầu (yyyy-MM-dd hoặc yyMMdd)", example = "2026-08-01") @RequestParam(required = false) String startDate,
            @Parameter(description = "Ngày kết thúc (yyyy-MM-dd hoặc yyMMdd)", example = "2026-08-31") @RequestParam(required = false) String endDate,
            @Parameter(description = "Loại giao dịch: 0 (Chi tiêu), 1 (Thu nhập)", example = "0") @RequestParam(required = false, defaultValue = "0") Integer type,
            @Parameter(hidden = true) @AuthenticationPrincipal Auth auth) {
        return new AbstractResponse<List<CategoryDistributionResponse>>()
                .withData(() -> reportService.getCategoryDistributionReport(auth, startDate, endDate, type));
    }

    @GetMapping("/account-flow")
    @Operation(summary = "Dòng tiền theo từng tài khoản ví", description = "Trả về tiền vào (inflow), tiền ra (outflow), dòng tiền ròng và số dư từng tài khoản")
    public ResponseEntity<AbstractResponse<List<AccountFlowResponse>>> getAccountFlow(
            @Parameter(description = "Ngày bắt đầu (yyyy-MM-dd hoặc yyMMdd)", example = "2026-08-01") @RequestParam(required = false) String startDate,
            @Parameter(description = "Ngày kết thúc (yyyy-MM-dd hoặc yyMMdd)", example = "2026-08-31") @RequestParam(required = false) String endDate,
            @Parameter(hidden = true) @AuthenticationPrincipal Auth auth) {
        return new AbstractResponse<List<AccountFlowResponse>>()
                .withData(() -> reportService.getAccountFlowReport(auth, startDate, endDate));
    }

    @GetMapping("/top-expenses")
    @Operation(summary = "Top khoản chi tiêu lớn nhất", description = "Trả về danh sách các khoản chi tiêu có số tiền lớn nhất trong kỳ")
    public ResponseEntity<AbstractResponse<List<TopExpenseResponse>>> getTopExpenses(
            @Parameter(description = "Ngày bắt đầu (yyyy-MM-dd hoặc yyMMdd)", example = "2026-08-01") @RequestParam(required = false) String startDate,
            @Parameter(description = "Ngày kết thúc (yyyy-MM-dd hoặc yyMMdd)", example = "2026-08-31") @RequestParam(required = false) String endDate,
            @Parameter(description = "Số lượng khoản chi cần lấy (mặc định 5)", example = "5") @RequestParam(required = false, defaultValue = "5") Integer limit,
            @Parameter(hidden = true) @AuthenticationPrincipal Auth auth) {
        return new AbstractResponse<List<TopExpenseResponse>>()
                .withData(() -> reportService.getTopExpensesReport(auth, startDate, endDate, limit));
    }

    @GetMapping("/account/{accountId}")
    public ResponseEntity<AbstractResponse<AccountSummary>> getReportByAccount(
            @PathVariable UUID accountId,
            @Parameter(hidden = true) @AuthenticationPrincipal Auth auth) {
        return new AbstractResponse<AccountSummary>()
                .withData(() -> reportService.getReportByAccount(accountId, auth));
    }

    @GetMapping("/distribution/{accountId}")
    public ResponseEntity<AbstractResponse<DistributionSummary>> getReportDistributionByAccount(
            @PathVariable UUID accountId,
            @Parameter(hidden = true) @AuthenticationPrincipal Auth auth) {
        return new AbstractResponse<DistributionSummary>()
                .withData(() -> reportService.getReportDistributionByAccount(accountId, auth));
    }

    @PostMapping("/summary")
    public ResponseEntity<AbstractResponse<SummaryReportResponse>> getSummary(@RequestBody SummaryReportRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal Auth auth) {
        return new AbstractResponse<SummaryReportResponse>().withData(() -> reportService.getSummary(request, auth));
    }

    @PostMapping("/daily")
    public ResponseEntity<AbstractResponse<DailyReportResponse>> getDailyReport(@RequestBody ReportRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal Auth auth) {
        return new AbstractResponse<DailyReportResponse>().withData(() -> reportService.getDailyReport(request, auth));
    }

    @PostMapping("/monthly")
    public ResponseEntity<AbstractResponse<MonthlyReportResponse>> getMonthlyReport(
            @RequestBody MonthlyReportRequest request, @Parameter(hidden = true) @AuthenticationPrincipal Auth auth) {
        return new AbstractResponse<MonthlyReportResponse>()
                .withData(() -> reportService.getMonthlyReport(request, auth));
    }

    @PostMapping("/category")
    public ResponseEntity<AbstractResponse<CategoryReportResponse>> getCategoryReport(
            @RequestBody CategoryReportRequest request, @Parameter(hidden = true) @AuthenticationPrincipal Auth auth) {
        return new AbstractResponse<CategoryReportResponse>()
                .withData(() -> reportService.getCategoryReport(request, auth));
    }

    @PostMapping("/compare")
    public ResponseEntity<AbstractResponse<CompareReportResponse>> getCompareReport(@RequestBody ReportRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal Auth auth) {
        return new AbstractResponse<CompareReportResponse>()
                .withData(() -> reportService.getCompareReport(request, auth));
    }

    @PostMapping("/export/month")
    public ResponseEntity<byte[]> exportMonthlyReportByMonth(
            @RequestBody ReportRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal Auth auth) {
        return reportService.exportMonthlyReportByMonthPDF(request, auth);
    }

    @PostMapping("/export/year")
    public ResponseEntity<byte[]> exportMonthlyReportByYear(
            @RequestBody MonthlyReportRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal Auth auth) {
        return reportService.exportMonthlyReportByYearPDF(request, auth);
    }

}
