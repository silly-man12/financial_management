package com.example.financial_management.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.financial_management.entity.CurrencyExchange;

@Repository
public interface CurrencyExchangeRepository extends JpaRepository<CurrencyExchange, UUID> {

    Optional<CurrencyExchange> findTopByFromCurrencyAndToCurrencyOrderByExchangeDateDesc(
            int fromCurrency, int toCurrency);

    Optional<CurrencyExchange> findByFromCurrencyAndToCurrencyAndExchangeDate(
            int fromCurrency, int toCurrency, LocalDate exchangeDate);

    boolean existsByFromCurrencyAndToCurrencyAndExchangeDate(
            int fromCurrency, int toCurrency, LocalDate exchangeDate);

    List<CurrencyExchange> findAllByFromCurrencyAndToCurrencyOrderByExchangeDateDesc(
            int fromCurrency, int toCurrency);
}
