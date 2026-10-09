package com.example.financial_management.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.financial_management.model.AbstractResponse;
import com.example.financial_management.model.auth.Auth;
import com.example.financial_management.model.dashboard.DashboardResponse;
import com.example.financial_management.services.DashboardService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard API", description = "Aggregated dashboard metrics, charts, accounts, budgets, and transactions")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    @Operation(summary = "Lấy dữ liệu tổng hợp cho màn hình Dashboard",
               description = "Trả về trọn bộ KPI, biểu đồ xu hướng ngày, danh sách tài khoản, tổng hợp ngân sách, và các giao dịch gần đây trong 1 request duy nhất.")
    public ResponseEntity<AbstractResponse<DashboardResponse>> getDashboard(
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            @AuthenticationPrincipal @Parameter(hidden = true) Auth auth) {
        return new AbstractResponse<DashboardResponse>()
                .withData(() -> dashboardService.getDashboard(month, year, auth));
    }
}
