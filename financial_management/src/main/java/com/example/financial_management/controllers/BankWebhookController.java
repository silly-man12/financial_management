package com.example.financial_management.controllers;

import com.example.financial_management.model.AbstractResponse;
import com.example.financial_management.model.PageResponse;
import com.example.financial_management.model.auth.Auth;
import com.example.financial_management.model.webhook.BankCategoryRuleRequest;
import com.example.financial_management.model.webhook.BankCategoryRuleResponse;
import com.example.financial_management.model.webhook.BankWebhookEventResponse;
import com.example.financial_management.model.webhook.BankWebhookProcessResult;
import com.example.financial_management.model.webhook.BankWebhookSimulateRequest;
import com.example.financial_management.model.webhook.WalletSourceMappingRequest;
import com.example.financial_management.model.webhook.WalletSourceMappingResponse;
import com.example.financial_management.services.BankWebhookService;
import com.example.financial_management.webhook.BankWebhookProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Bank Webhook API (SePay)", description = "Tiếp nhận biến động số dư ngân hàng từ SePay, tự động tạo Transaction và gửi thông báo Telegram")
public class BankWebhookController {

    private final BankWebhookService bankWebhookService;

    // =========================================================================
    // 1. Inbound Webhook Endpoints (Public - Xác thực qua SePay API Key / HMAC)
    // =========================================================================

    @PostMapping({"/webhooks/bank/sepay", "/api/v1/webhooks/bank/sepay", "/api/v1/webhooks/bank"})
    @Operation(summary = "Tiếp nhận Webhook biến động số dư từ SePay (xác thực Apikey hoặc HMAC)")
    public ResponseEntity<AbstractResponse<List<BankWebhookProcessResult>>> receiveSePayWebhook(
            HttpServletRequest request,
            @RequestBody String rawBody) {

        return new AbstractResponse<List<BankWebhookProcessResult>>().withData(
                () -> bankWebhookService.handleIncomingWebhook(BankWebhookProvider.SEPAY, request, rawBody)
        );
    }

    // =========================================================================
    // 2. Management Endpoints (Yêu cầu xác thực JWT)
    // =========================================================================

    @GetMapping("/webhooks/bank/events")
    @Operation(summary = "Lấy lịch sử các sự kiện webhook đã tiếp nhận (có lọc theo trạng thái và phân trang)")
    public ResponseEntity<AbstractResponse<PageResponse<BankWebhookEventResponse>>> getWebhookEvents(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @Parameter(hidden = true) @AuthenticationPrincipal Auth auth) {

        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        return new AbstractResponse<PageResponse<BankWebhookEventResponse>>().withData(
                () -> bankWebhookService.getEvents(auth, status, pageable)
        );
    }

    @PostMapping("/webhooks/bank/events/{id}/reprocess")
    @Operation(summary = "Xử lý lại một sự kiện webhook có trạng thái SKIPPED hoặc FAILED")
    public ResponseEntity<AbstractResponse<BankWebhookProcessResult>> reprocessEvent(
            @PathVariable UUID id,
            @Parameter(hidden = true) @AuthenticationPrincipal Auth auth) {

        return new AbstractResponse<BankWebhookProcessResult>().withData(
                () -> bankWebhookService.reprocessEvent(auth, id)
        );
    }

    @PostMapping("/webhooks/bank/events/reprocess-all")
    @Operation(summary = "Tự động xử lý bù toàn bộ các sự kiện webhook bị SKIPPED/RECEIVED chưa tạo Transaction")
    public ResponseEntity<AbstractResponse<List<BankWebhookProcessResult>>> reprocessAllSkippedEvents(
            @Parameter(hidden = true) @AuthenticationPrincipal Auth auth) {

        return new AbstractResponse<List<BankWebhookProcessResult>>().withData(
                () -> bankWebhookService.reprocessAllPendingEvents(auth)
        );
    }

    @GetMapping("/webhooks/bank/mappings")
    @Operation(summary = "Danh sách ánh xạ số tài khoản ngân hàng sang ví trong hệ thống")
    public ResponseEntity<AbstractResponse<List<WalletSourceMappingResponse>>> getMappings(
            @Parameter(hidden = true) @AuthenticationPrincipal Auth auth) {

        return new AbstractResponse<List<WalletSourceMappingResponse>>().withData(
                () -> bankWebhookService.getMappings(auth)
        );
    }

    @PostMapping("/webhooks/bank/mappings")
    @Operation(summary = "Tạo mới hoặc cập nhật ánh xạ số tài khoản ngân hàng sang ví")
    public ResponseEntity<AbstractResponse<WalletSourceMappingResponse>> upsertMapping(
            @Valid @RequestBody WalletSourceMappingRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal Auth auth) {

        return new AbstractResponse<WalletSourceMappingResponse>().withData(
                () -> bankWebhookService.upsertMapping(auth, request)
        );
    }

    @DeleteMapping("/webhooks/bank/mappings/{id}")
    @Operation(summary = "Xóa một cấu hình ánh xạ nguồn tiền")
    public ResponseEntity<AbstractResponse<String>> deleteMapping(
            @PathVariable UUID id,
            @Parameter(hidden = true) @AuthenticationPrincipal Auth auth) {

        return new AbstractResponse<String>().withData(() -> {
            bankWebhookService.deleteMapping(auth, id);
            return "Đã xóa ánh xạ nguồn thành công";
        });
    }

    @GetMapping("/webhooks/bank/rules")
    @Operation(summary = "Danh sách quy tắc tự động phân loại danh mục theo từ khóa nội dung chuyển khoản")
    public ResponseEntity<AbstractResponse<List<BankCategoryRuleResponse>>> getRules(
            @Parameter(hidden = true) @AuthenticationPrincipal Auth auth) {

        return new AbstractResponse<List<BankCategoryRuleResponse>>().withData(
                () -> bankWebhookService.getRules(auth)
        );
    }

    @PostMapping("/webhooks/bank/rules")
    @Operation(summary = "Thêm quy tắc tự động gắn danh mục khi nội dung chuyển khoản chứa từ khóa")
    public ResponseEntity<AbstractResponse<BankCategoryRuleResponse>> createRule(
            @Valid @RequestBody BankCategoryRuleRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal Auth auth) {

        return new AbstractResponse<BankCategoryRuleResponse>().withData(
                () -> bankWebhookService.createRule(auth, request)
        );
    }

    @DeleteMapping("/webhooks/bank/rules/{id}")
    @Operation(summary = "Xóa quy tắc phân loại tự động")
    public ResponseEntity<AbstractResponse<String>> deleteRule(
            @PathVariable UUID id,
            @Parameter(hidden = true) @AuthenticationPrincipal Auth auth) {

        return new AbstractResponse<String>().withData(() -> {
            bankWebhookService.deleteRule(auth, id);
            return "Đã xóa quy tắc phân loại thành công";
        });
    }

    @PostMapping("/webhooks/bank/simulate")
    @Operation(summary = "Giả lập bắn Webhook SePay trực tiếp trên Web/Swagger để kiểm thử")
    public ResponseEntity<AbstractResponse<List<BankWebhookProcessResult>>> simulateWebhook(
            @Valid @RequestBody BankWebhookSimulateRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal Auth auth) {

        return new AbstractResponse<List<BankWebhookProcessResult>>().withData(
                () -> bankWebhookService.simulateWebhook(auth, request)
        );
    }
}
