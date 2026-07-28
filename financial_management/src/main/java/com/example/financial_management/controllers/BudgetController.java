package com.example.financial_management.controllers;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.financial_management.model.AbstractResponse;
import com.example.financial_management.model.auth.Auth;
import com.example.financial_management.model.budget.BudgetCheckingResponse;
import com.example.financial_management.model.budget.BudgetRequest;
import com.example.financial_management.model.budget.BudgetResponse;
import com.example.financial_management.services.BudgetService;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/budgets")
@RequiredArgsConstructor
@Tag(name = "Budget API", description = "Budget Management")
public class BudgetController {
    private final BudgetService budgetService;

    // Tính năng lấy danh sách ngân sách của người dùng
    @GetMapping("/all")
    public ResponseEntity<AbstractResponse<List<BudgetResponse>>> getBudgets(
            @AuthenticationPrincipal @Parameter(hidden = true) Auth auth) {
        return new AbstractResponse<List<BudgetResponse>>()
                .withData(() -> budgetService.getBudgets(auth));
    }

    // Tinh năng kiểm tra ngân sách, hiển thị số tiền đã chi tiêu và số tiền còn lại
    // trong ngân sách
    @GetMapping("/checking")
    public ResponseEntity<AbstractResponse<List<BudgetCheckingResponse>>> checkingBudget(
            @RequestParam int month,
            @RequestParam int year,
            @AuthenticationPrincipal @Parameter(hidden = true) Auth auth) {

        return new AbstractResponse<List<BudgetCheckingResponse>>()
                .withData(() -> budgetService.checkingBudget(month, year, auth));
    }

    // Tính năng lấy thông tin ngân sách theo ID
    @GetMapping("/{id}")
    public ResponseEntity<AbstractResponse<BudgetResponse>> getBudgetById(@RequestParam UUID id,
            @AuthenticationPrincipal @Parameter(hidden = true) Auth auth) {
        return new AbstractResponse<BudgetResponse>()
                .withData(() -> budgetService.getBudgetById(id, auth));
    }

    // Tính năng tạo ngân sách mới
    @PostMapping("/create")
    public ResponseEntity<AbstractResponse<BudgetResponse>> createBudget(@RequestBody BudgetRequest request,
            @AuthenticationPrincipal @Parameter(hidden = true) Auth auth) {
        return new AbstractResponse<BudgetResponse>()
                .withData(() -> budgetService.createBudget(request, auth));
    }

    // Cập nhật ngân sách theo ID
    @PostMapping("/update")
    public ResponseEntity<AbstractResponse<BudgetResponse>> updateBudget(@RequestParam UUID budgetId,
            @RequestBody BudgetRequest request,
            @AuthenticationPrincipal @Parameter(hidden = true) Auth auth) {
        return new AbstractResponse<BudgetResponse>()
                .withData(() -> budgetService.updateBudget(budgetId, request, auth));
    }

    // Xóa ngân sách theo ID
    @PostMapping("/delete")
    public ResponseEntity<AbstractResponse<String>> deleteBudget(@RequestParam UUID budgetId,
            @AuthenticationPrincipal @Parameter(hidden = true) Auth auth) {
        return new AbstractResponse<String>()
                .withData(() -> {
                    return budgetService.deleteBudget(budgetId, auth);
                });
    }
}
