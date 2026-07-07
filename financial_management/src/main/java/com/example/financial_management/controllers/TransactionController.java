package com.example.financial_management.controllers;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.financial_management.model.AbstractResponse;
import com.example.financial_management.model.PageResponse;
import com.example.financial_management.model.auth.Auth;
import com.example.financial_management.model.transaction.TransactionDateRequest;
import com.example.financial_management.model.transaction.TransactionFilterRequest;
import com.example.financial_management.model.transaction.TransactionRequest;
import com.example.financial_management.model.transaction.TransactionResponse;
import com.example.financial_management.model.transaction.TransactionUpdateResponse;
import com.example.financial_management.model.transaction.TransferRequest;
import com.example.financial_management.services.TransactionService;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
@Tag(name = "Transaction API", description = "Transaction Management")
public class TransactionController {
        private final TransactionService transactionService;

        // Các endpoint khác sẽ được thêm vào đây

        // Lấy thông tin giao dịch theo ID
        @GetMapping("/{id}")
        public ResponseEntity<AbstractResponse<TransactionResponse>> getById(
                        @RequestParam UUID id,
                        @Parameter(hidden = true) @AuthenticationPrincipal Auth auth) {
                return new AbstractResponse<TransactionResponse>()
                                .withData(() -> transactionService.getById(id, auth));
        }

        // Lấy tất cả giao dịch với phân trang
        @GetMapping("/all")
        public ResponseEntity<AbstractResponse<PageResponse<TransactionResponse>>> getAll(
                        @Parameter(hidden = true) @AuthenticationPrincipal Auth auth,
                        @RequestParam(defaultValue = "1") int page,
                        @RequestParam(defaultValue = "20") int size) {

                return new AbstractResponse<PageResponse<TransactionResponse>>()
                                .withData(() -> transactionService.getAllTransactions(auth,
                                                PageRequest.of(page - 1, size)));
        }

        // Lấy tất cả giao dịch theo accountId với phân trang
        @GetMapping("/{accountId}/all")
        public ResponseEntity<AbstractResponse<PageResponse<TransactionResponse>>> getAllByAccount(
                        @PathVariable UUID accountId,
                        @RequestParam(defaultValue = "1") int page,
                        @RequestParam(defaultValue = "20") int size,
                        @Parameter(hidden = true) @AuthenticationPrincipal Auth auth) {
                return new AbstractResponse<PageResponse<TransactionResponse>>()
                                .withData(() -> transactionService.getTransactionByAccount(accountId,
                                                auth, PageRequest.of(page - 1, size)));
        }

        // Tạo giao dịch mới với khả năng upload file
        @PostMapping(value = "/create", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        public ResponseEntity<AbstractResponse<TransactionResponse>> createTransaction(
                        @ModelAttribute TransactionDateRequest request,
                        @RequestPart(value = "file", required = false) MultipartFile file,
                        @Parameter(hidden = true) @AuthenticationPrincipal Auth auth) {
                return new AbstractResponse<TransactionResponse>()
                                .withData(() -> transactionService.createTransaction(request, auth, file));
        }

        // Cập nhật giao dịch theo ID
        @PostMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        public ResponseEntity<AbstractResponse<TransactionUpdateResponse>> updateTransaction(
                        @PathVariable("id") UUID transactionId,
                        @ModelAttribute TransactionRequest request,
                        @RequestPart(value = "file", required = false) MultipartFile file,
                        @Parameter(hidden = true) @AuthenticationPrincipal Auth auth) {

                return new AbstractResponse<TransactionUpdateResponse>()
                                .withData(() -> transactionService.updateTransaction(request, auth, transactionId,
                                                file));
        }

        // Xóa giao dịch theo ID
        @DeleteMapping("/{id}")
        public ResponseEntity<AbstractResponse<Boolean>> dropTransaction(@RequestParam UUID id,
                        @Parameter(hidden = true) @AuthenticationPrincipal Auth auth) {
                return new AbstractResponse<Boolean>().withData(() -> transactionService.deleteTransaction(id, auth));
        }

        // Lọc giao dịch theo các tiêu chí
        @PostMapping("/filter")
        public ResponseEntity<AbstractResponse<PageResponse<TransactionResponse>>> filterTransactions(
                        @RequestBody TransactionFilterRequest filter,
                        @Parameter(hidden = true) @AuthenticationPrincipal Auth auth) {

                return new AbstractResponse<PageResponse<TransactionResponse>>()
                                .withData(() -> transactionService.filterTransactions(auth, filter));
        }

        // Chuyển tiền giữa các tài khoản
        @PostMapping("/transfer")
        public ResponseEntity<AbstractResponse<TransactionResponse>> transfer(@RequestBody TransferRequest request,
                        @Parameter(hidden = true) @AuthenticationPrincipal Auth auth) {
                return new AbstractResponse<TransactionResponse>()
                                .withData(() -> transactionService.transfer(request, auth));
        }

}
