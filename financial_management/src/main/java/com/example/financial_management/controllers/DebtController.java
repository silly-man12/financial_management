package com.example.financial_management.controllers;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.financial_management.model.AbstractResponse;
import com.example.financial_management.model.auth.Auth;
import com.example.financial_management.model.debt.DebtPaymentRequest;
import com.example.financial_management.model.debt.DebtRequest;
import com.example.financial_management.model.debt.DebtResponse;
import com.example.financial_management.model.debt.DebtUpdateRequest;
import com.example.financial_management.services.DebtService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/debts")
@RequiredArgsConstructor
@Tag(name = "Debt API", description = "Quản lý khoản vay và cho vay (Sổ nợ)")
public class DebtController {

        private final DebtService debtService;

        @GetMapping
        @Operation(summary = "Lấy danh sách khoản nợ (hỗ trợ lọc theo type: 1-Đi vay, 2-Cho vay và status)")
        public ResponseEntity<AbstractResponse<List<DebtResponse>>> getAll(
                        @Parameter(hidden = true) @AuthenticationPrincipal Auth auth,
                        @RequestParam(value = "type", required = false) Integer type,
                        @RequestParam(value = "status", required = false) Integer status) {
                return new AbstractResponse<List<DebtResponse>>()
                                .withData(() -> debtService.getAll(auth, type, status));
        }

        @GetMapping("/{id}")
        @Operation(summary = "Xem chi tiết 1 khoản nợ + lịch sử các lần trả")
        public ResponseEntity<AbstractResponse<DebtResponse>> getById(
                        @PathVariable UUID id,
                        @Parameter(hidden = true) @AuthenticationPrincipal Auth auth) {
                return new AbstractResponse<DebtResponse>()
                                .withData(() -> debtService.getById(id, auth));
        }

        @PostMapping
        @Operation(summary = "Tạo khoản nợ mới (Khởi tạo khoản vay / Cho vay)")
        public ResponseEntity<AbstractResponse<DebtResponse>> create(
                        @Valid @RequestBody DebtRequest request,
                        @Parameter(hidden = true) @AuthenticationPrincipal Auth auth) {
                return new AbstractResponse<DebtResponse>()
                                .withData(() -> debtService.create(request, auth));
        }

        @PostMapping("/{id}")
        @Operation(summary = "Sửa thông tin khoản nợ qua POST")
        public ResponseEntity<AbstractResponse<DebtResponse>> update(
                        @PathVariable UUID id,
                        @Valid @RequestBody DebtUpdateRequest request,
                        @Parameter(hidden = true) @AuthenticationPrincipal Auth auth) {
                return new AbstractResponse<DebtResponse>()
                                .withData(() -> debtService.update(id, request, auth));
        }

        @DeleteMapping("/{id}")
        @Operation(summary = "Xóa khoản nợ")
        public ResponseEntity<AbstractResponse<Boolean>> delete(
                        @PathVariable UUID id,
                        @Parameter(hidden = true) @AuthenticationPrincipal Auth auth) {
                return new AbstractResponse<Boolean>()
                                .withData(() -> debtService.delete(id, auth));
        }

        @PostMapping("/{id}/payments")
        @Operation(summary = "Ghi nhận 1 lần trả nợ / thu nợ (tự động đổi status sang PAID nếu hết nợ)")
        public ResponseEntity<AbstractResponse<DebtResponse>> addPayment(
                        @PathVariable UUID id,
                        @Valid @RequestBody DebtPaymentRequest request,
                        @Parameter(hidden = true) @AuthenticationPrincipal Auth auth) {
                return new AbstractResponse<DebtResponse>()
                                .withData(() -> debtService.addPayment(id, request, auth));
        }

        @DeleteMapping("/{id}/payments/{paymentId}")
        @Operation(summary = "Hủy 1 lần trả tiền (hoàn tác số dư nợ & tài khoản)")
        public ResponseEntity<AbstractResponse<DebtResponse>> deletePayment(
                        @PathVariable UUID id,
                        @PathVariable UUID paymentId,
                        @Parameter(hidden = true) @AuthenticationPrincipal Auth auth) {
                return new AbstractResponse<DebtResponse>()
                                .withData(() -> debtService.deletePayment(id, paymentId, auth));
        }
}
