package com.example.financial_management.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.example.financial_management.constant.Currency;
import com.example.financial_management.entity.base.EntityBase;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "currency_exchanges")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CurrencyExchange extends EntityBase {

    @Builder.Default
    @Column(name = "from_currency", nullable = false)
    private int fromCurrency = Currency.USD;

    @Builder.Default
    @Column(name = "to_currency", nullable = false)
    private int toCurrency = Currency.VND;

    @Builder.Default
    @Column(name = "from_currency_code", length = 10, nullable = false)
    private String fromCurrencyCode = "USD";

    @Builder.Default
    @Column(name = "to_currency_code", length = 10, nullable = false)
    private String toCurrencyCode = "VND";

    @Column(name = "rate", nullable = false, precision = 18, scale = 6)
    private BigDecimal rate;

    @Column(name = "exchange_date", nullable = false)
    private LocalDate exchangeDate;

    @Column(name = "source", length = 100)
    private String source;
}
