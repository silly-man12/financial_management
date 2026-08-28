package com.example.financial_management.model.currency;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Thông tin tỷ giá ngoại tệ")
public class CurrencyExchangeResponse {

    @Schema(description = "ID bản ghi tỷ giá")
    private UUID id;

    @Schema(description = "Mã tiền tệ gốc (0 = USD)", example = "0")
    private int fromCurrency;

    @Schema(description = "Mã tiền tệ đích (1 = VND)", example = "1")
    private int toCurrency;

    @Schema(description = "Ký hiệu tiền tệ gốc", example = "USD")
    private String fromCurrencyCode;

    @Schema(description = "Ký hiệu tiền tệ đích", example = "VND")
    private String toCurrencyCode;

    @Schema(description = "Tỷ giá quy đổi (1 USD = X VND)", example = "25450.00")
    private BigDecimal rate;

    @Schema(description = "Ngày áp dụng tỷ giá", example = "2026-08-28")
    private LocalDate exchangeDate;

    @Schema(description = "Nguồn cung cấp tỷ giá", example = "OpenExchangeRates API")
    private String source;
}
