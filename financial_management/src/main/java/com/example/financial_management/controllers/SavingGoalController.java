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
import com.example.financial_management.model.saving_goal.SavingGoalContributionRequest;
import com.example.financial_management.model.saving_goal.SavingGoalContributionResponse;
import com.example.financial_management.model.saving_goal.SavingGoalDepositRequest;
import com.example.financial_management.model.saving_goal.SavingGoalRequest;
import com.example.financial_management.model.saving_goal.SavingGoalResponse;
import com.example.financial_management.model.saving_goal.SavingGoalUpdateRequest;
import com.example.financial_management.model.saving_goal.SavingGoalWithdrawRequest;
import com.example.financial_management.services.SavingGoalService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/saving-goals")
@RequiredArgsConstructor
@Tag(name = "Saving Goal API", description = "Quản lý mục tiêu tiết kiệm và lịch sử góp quỹ")
public class SavingGoalController {

        private final SavingGoalService savingGoalService;

        @GetMapping
        @Operation(summary = "Lấy danh sách mục tiêu (hỗ trợ lọc theo status: 1 = Đang thực hiện, 2 = Hoàn thành)")
        public ResponseEntity<AbstractResponse<List<SavingGoalResponse>>> getAll(
                        @Parameter(hidden = true) @AuthenticationPrincipal Auth auth,
                        @RequestParam(value = "status", required = false) Integer status) {
                return new AbstractResponse<List<SavingGoalResponse>>()
                                .withData(() -> savingGoalService.getAll(auth, status));
        }

        @GetMapping("/{id}")
        @Operation(summary = "Xem thông tin chi tiết, tiến độ % và toàn bộ lịch sử góp quỹ của 1 mục tiêu")
        public ResponseEntity<AbstractResponse<SavingGoalResponse>> getById(
                        @PathVariable UUID id,
                        @Parameter(hidden = true) @AuthenticationPrincipal Auth auth) {
                return new AbstractResponse<SavingGoalResponse>()
                                .withData(() -> savingGoalService.getById(id, auth));
        }

        @PostMapping
        @Operation(summary = "Tạo mục tiêu mới (khởi tạo với số tiền ban đầu có thể = 0)")
        public ResponseEntity<AbstractResponse<SavingGoalResponse>> create(
                        @Valid @RequestBody SavingGoalRequest request,
                        @Parameter(hidden = true) @AuthenticationPrincipal Auth auth) {
                return new AbstractResponse<SavingGoalResponse>()
                                .withData(() -> savingGoalService.create(request, auth));
        }

        @PostMapping("/{id}")
        @Operation(summary = "Cập nhật mục tiêu (sửa tên, số tiền đích, hạn chót, màu sắc)")
        public ResponseEntity<AbstractResponse<SavingGoalResponse>> update(
                        @PathVariable UUID id,
                        @Valid @RequestBody SavingGoalUpdateRequest request,
                        @Parameter(hidden = true) @AuthenticationPrincipal Auth auth) {
                return new AbstractResponse<SavingGoalResponse>()
                                .withData(() -> savingGoalService.update(id, request, auth));
        }

        @PostMapping("/{id}/deposit")
        @Operation(summary = "Nạp tiền / Góp quỹ (tự động chuyển status = 2 nếu đạt >= 100%)")
        public ResponseEntity<AbstractResponse<SavingGoalResponse>> deposit(
                        @PathVariable UUID id,
                        @Valid @RequestBody SavingGoalDepositRequest request,
                        @Parameter(hidden = true) @AuthenticationPrincipal Auth auth) {
                return new AbstractResponse<SavingGoalResponse>()
                                .withData(() -> savingGoalService.deposit(id, request, auth));
        }

        @PostMapping("/{id}/withdraw")
        @Operation(summary = "Rút tiền từ mục tiêu về tài khoản ví/ngân hàng")
        public ResponseEntity<AbstractResponse<SavingGoalResponse>> withdraw(
                        @PathVariable UUID id,
                        @Valid @RequestBody SavingGoalWithdrawRequest request,
                        @Parameter(hidden = true) @AuthenticationPrincipal Auth auth) {
                return new AbstractResponse<SavingGoalResponse>()
                                .withData(() -> savingGoalService.withdraw(id, request, auth));
        }

        @GetMapping("/{id}/contributions")
        @Operation(summary = "Lấy danh sách lịch sử nạp/rút tiền của mục tiêu tiết kiệm")
        public ResponseEntity<AbstractResponse<List<SavingGoalContributionResponse>>> getContributions(
                        @PathVariable UUID id,
                        @Parameter(hidden = true) @AuthenticationPrincipal Auth auth) {
                return new AbstractResponse<List<SavingGoalContributionResponse>>()
                                .withData(() -> savingGoalService.getContributions(id, auth));
        }

        @PostMapping("/{id}/contributions")
        @Operation(summary = "Thêm bản ghi đóng góp / rút quỹ trực tiếp")
        public ResponseEntity<AbstractResponse<SavingGoalResponse>> addContribution(
                        @PathVariable UUID id,
                        @Valid @RequestBody SavingGoalContributionRequest request,
                        @Parameter(hidden = true) @AuthenticationPrincipal Auth auth) {
                return new AbstractResponse<SavingGoalResponse>()
                                .withData(() -> savingGoalService.addContribution(id, request, auth));
        }

        @DeleteMapping("/{id}/contributions/{contributionId}")
        @Operation(summary = "Hủy 1 lần đóng góp (hoàn tác số dư mục tiêu & số dư tài khoản liên quan)")
        public ResponseEntity<AbstractResponse<SavingGoalResponse>> deleteContribution(
                        @PathVariable UUID id,
                        @PathVariable UUID contributionId,
                        @Parameter(hidden = true) @AuthenticationPrincipal Auth auth) {
                return new AbstractResponse<SavingGoalResponse>()
                                .withData(() -> savingGoalService.deleteContribution(id, contributionId, auth));
        }

        @DeleteMapping("/{id}")
        @Operation(summary = "Xóa bỏ mục tiêu tiết kiệm (và toàn bộ lịch sử góp quỹ liên quan)")
        public ResponseEntity<AbstractResponse<Boolean>> delete(
                        @PathVariable UUID id,
                        @Parameter(hidden = true) @AuthenticationPrincipal Auth auth) {
                return new AbstractResponse<Boolean>()
                                .withData(() -> savingGoalService.delete(id, auth));
        }
}
