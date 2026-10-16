package com.example.financial_management.controllers;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.financial_management.model.AbstractResponse;
import com.example.financial_management.model.auth.Auth;
import com.example.financial_management.model.cashflow.CashflowForecastResponse;
import com.example.financial_management.services.CashflowService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/cashflow")
@RequiredArgsConstructor
@Tag(name = "Cashflow Forecast", description = "Dự báo dòng tiền đến cuối tháng")
public class CashflowController {

    private final CashflowService cashflowService;

    /**
     * GET /cashflow/forecast
     * Dự báo dòng tiền đến cuối tháng: actual + recurring scheduled + budget comparison.
     */
    @GetMapping("/forecast")
    @Operation(
        summary = "Dự báo dòng tiền đến cuối tháng",
        description = "Tổng hợp: thu/chi thực tế từ đầu tháng, giao dịch định kỳ sắp đến, dự báo số dư cuối tháng, và so sánh với ngân sách"
    )
    public ResponseEntity<AbstractResponse<CashflowForecastResponse>> getForecast(
            @Parameter(description = "Tháng (1-12). Mặc định = tháng hiện tại", example = "10")
            @RequestParam(required = false) Integer month,
            @Parameter(description = "Năm. Mặc định = năm hiện tại", example = "2026")
            @RequestParam(required = false) Integer year,
            @Parameter(description = "ID tài khoản cụ thể (optional). Mặc định = tất cả tài khoản")
            @RequestParam(required = false) UUID accountId,
            @Parameter(hidden = true) @AuthenticationPrincipal Auth auth) {

        return new AbstractResponse<CashflowForecastResponse>()
                .withData(() -> cashflowService.getForecast(auth, month, year, accountId));
    }
}
