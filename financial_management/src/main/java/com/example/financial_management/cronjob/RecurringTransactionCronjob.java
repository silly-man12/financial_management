package com.example.financial_management.cronjob;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.financial_management.services.RecurringTransactionService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RecurringTransactionCronjob {

    private final RecurringTransactionService recurringTransactionService;

    // Quét tự động hàng ngày lúc 00:00:00
    @Scheduled(cron = "0 0 0 * * *")
    public void execute() {
        recurringTransactionService.executeAllDue();
    }
}
