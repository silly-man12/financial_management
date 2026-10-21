package com.example.financial_management.controllers;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.financial_management.model.AbstractResponse;
import com.example.financial_management.model.transaction_type.response.TransactionTypeResponse;
import com.example.financial_management.services.TransactionTypeService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/transaction-types")
@RequiredArgsConstructor
@Tag(name = "Transaction Type API", description = "Tra cứu Loại giao dịch (Chi tiêu, Thu nhập, Chuyển khoản)")
public class TransactionTypeController {

    private final TransactionTypeService transactionTypeService;

    @GetMapping
    @Operation(summary = "Lấy danh sách tất cả các loại giao dịch")
    public ResponseEntity<AbstractResponse<List<TransactionTypeResponse>>> getAllTransactionTypes() {
        return new AbstractResponse<List<TransactionTypeResponse>>()
                .withData(transactionTypeService::getAllTransactionTypes);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lấy thông tin chi tiết loại giao dịch theo ID")
    public ResponseEntity<AbstractResponse<TransactionTypeResponse>> getById(@PathVariable("id") Integer id) {
        return new AbstractResponse<TransactionTypeResponse>()
                .withData(() -> transactionTypeService.getTransactionTypeById(id));
    }
}
