package com.example.financial_management.controllers;

import java.util.List;

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
import com.example.financial_management.model.report.response.CategoryReportResponse;
import com.example.financial_management.model.report.response.CompareReportResponse;
import com.example.financial_management.model.report.response.DailyReportResponse;
import com.example.financial_management.model.report.response.MonthlyReportResponse;
import com.example.financial_management.model.report.response.SummaryReportResponse;
import com.example.financial_management.model.transaction.TransactionResponse;
import com.example.financial_management.services.ReportService;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
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
