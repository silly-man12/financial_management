package com.example.financial_management.controllers;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.financial_management.model.AbstractResponse;
import com.example.financial_management.model.currency.CurrencyExchangeResponse;
import com.example.financial_management.services.CurrencyExchangeService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/currency-exchange")
@RequiredArgsConstructor
@Tag(name = "Currency Exchange API", description = "Quản lý và tra cứu tỷ giá ngoại tệ")
public class CurrencyExchangeController {

    private final CurrencyExchangeService currencyExchangeService;

    @GetMapping("/latest")
    @Operation(summary = "Lấy tỷ giá USD/VND mới nhất", description = "Trả về tỷ giá USD/VND hiện hành đang áp dụng trong hệ thống")
    public ResponseEntity<AbstractResponse<CurrencyExchangeResponse>> getLatestRate() {
        return new AbstractResponse<CurrencyExchangeResponse>()
                .withData(() -> currencyExchangeService.getLatestRateResponse());
    }

    @GetMapping("/history")
    @Operation(summary = "Lấy lịch sử tỷ giá USD/VND", description = "Trả về danh sách lịch sử tỷ giá USD/VND theo thứ tự ngày mới nhất")
    public ResponseEntity<AbstractResponse<List<CurrencyExchangeResponse>>> getRateHistory() {
        return new AbstractResponse<List<CurrencyExchangeResponse>>()
                .withData(() -> currencyExchangeService.getRateHistory());
    }

    @PostMapping("/sync")
    @Operation(summary = "Đồng bộ tỷ giá trực tuyến ngay lập tức", description = "Gọi API lấy tỷ giá trực tuyến và lưu vào database cho ngày hôm nay")
    public ResponseEntity<AbstractResponse<CurrencyExchangeResponse>> syncDailyRate() {
        return new AbstractResponse<CurrencyExchangeResponse>()
                .withData(() -> currencyExchangeService.syncDailyRate());
    }
}
