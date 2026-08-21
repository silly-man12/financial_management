package com.example.financial_management.cronjob;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.financial_management.services.RecurringTransactionService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RecurringTransactionCronjob {

    private final RecurringTransactionService recurringTransactionService;

    // Mùng 5 hàng tháng 12h
    @Scheduled(cron = "0 0 12 5 * *")
    public void execute() {
        recurringTransactionService.executeNow();
    }
}
