package com.example.financial_management.cronjob;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.financial_management.services.CurrencyExchangeService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class CurrencyExchangeCronjob {

    private final CurrencyExchangeService currencyExchangeService;

    // Chạy tự động hàng ngày lúc 06:00:00 sáng
    // @Scheduled(cron = "0 0 6 * * *")
    @Scheduled(cron = "0 35 10 * * *")
    public void executeDailyRateSync() {
        log.info("Running CurrencyExchangeCronjob to sync daily USD/VND exchange rate...");
        try {
            currencyExchangeService.syncDailyRate();
            log.info("CurrencyExchangeCronjob finished successfully.");
        } catch (Exception e) {
            log.error("Error executing CurrencyExchangeCronjob: {}", e.getMessage(), e);
        }
    }
}
