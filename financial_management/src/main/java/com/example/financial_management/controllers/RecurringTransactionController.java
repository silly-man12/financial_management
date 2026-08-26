package com.example.financial_management.controllers;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.example.financial_management.model.AbstractResponse;
import com.example.financial_management.model.auth.Auth;
import com.example.financial_management.model.recurring.RecurringTransactionRequest;
import com.example.financial_management.model.recurring.RecurringTransactionResponse;
import com.example.financial_management.services.RecurringTransactionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/recurring-transactions")
@RequiredArgsConstructor
@Tag(name = "Recurring Transaction API", description = "Quản lý giao dịch định kỳ")
public class RecurringTransactionController {

        private final RecurringTransactionService recurringTransactionService;

        @GetMapping
        @Operation(summary = "Lấy danh sách giao dịch định kỳ (hỗ trợ lọc theo status)")
        public ResponseEntity<AbstractResponse<List<RecurringTransactionResponse>>> getAll(
                        @Parameter(hidden = true) @AuthenticationPrincipal Auth auth,
                        @RequestParam(value = "status", required = false) Integer status) {
                return new AbstractResponse<List<RecurringTransactionResponse>>()
                                .withData(() -> recurringTransactionService.getAll(auth, status));
        }

        @GetMapping("/{id}")
        @Operation(summary = "Xem chi tiết 1 giao dịch định kỳ")
        public ResponseEntity<AbstractResponse<RecurringTransactionResponse>> getById(
                        @PathVariable UUID id,
                        @Parameter(hidden = true) @AuthenticationPrincipal Auth auth) {
                return new AbstractResponse<RecurringTransactionResponse>()
                                .withData(() -> recurringTransactionService.getById(id, auth));
        }

        @PostMapping
        @Operation(summary = "Tạo mới giao dịch định kỳ")
        public ResponseEntity<AbstractResponse<RecurringTransactionResponse>> create(
                        @Valid @RequestBody RecurringTransactionRequest request,
                        @Parameter(hidden = true) @AuthenticationPrincipal Auth auth) {
                return new AbstractResponse<RecurringTransactionResponse>()
                                .withData(() -> recurringTransactionService.create(request, auth));
        }

        @PostMapping("/{id}")
        @Operation(summary = "Cập nhật giao dịch định kỳ")
        public ResponseEntity<AbstractResponse<RecurringTransactionResponse>> update(
                        @PathVariable UUID id,
                        @Valid @RequestBody RecurringTransactionRequest request,
                        @Parameter(hidden = true) @AuthenticationPrincipal Auth auth) {
                return new AbstractResponse<RecurringTransactionResponse>()
                                .withData(() -> recurringTransactionService.update(id, request, auth));
        }

        @PostMapping("/{id}/status")
        @Operation(summary = "Bật / Tạm dừng giao dịch định kỳ (ACTIVE=1, PAUSED=2)")
        public ResponseEntity<AbstractResponse<RecurringTransactionResponse>> updateStatus(
                        @PathVariable UUID id,
                        @RequestParam("status") int status,
                        @Parameter(hidden = true) @AuthenticationPrincipal Auth auth) {
                return new AbstractResponse<RecurringTransactionResponse>()
                                .withData(() -> recurringTransactionService.updateStatus(id, status, auth));
        }

        @PostMapping("/{id}/execute-now")
        @Operation(summary = "Ghi nhận giao dịch ngay lập tức theo quy tắc này (không cần đợi đến ngày đến hạn)")
        public ResponseEntity<AbstractResponse<RecurringTransactionResponse>> executeNow(
                        @PathVariable UUID id,
                        @Parameter(hidden = true) @AuthenticationPrincipal Auth auth) {
                return new AbstractResponse<RecurringTransactionResponse>()
                                .withData(() -> recurringTransactionService.executeNow(id, auth));
        }

        @DeleteMapping("/{id}")
        @Operation(summary = "Xóa giao dịch định kỳ (không xóa các giao dịch lịch sử)")
        public ResponseEntity<AbstractResponse<Boolean>> delete(
                        @PathVariable UUID id,
                        @Parameter(hidden = true) @AuthenticationPrincipal Auth auth) {
                return new AbstractResponse<Boolean>()
                                .withData(() -> recurringTransactionService.delete(id, auth));
        }
}
