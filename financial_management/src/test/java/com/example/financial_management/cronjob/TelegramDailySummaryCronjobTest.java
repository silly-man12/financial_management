package com.example.financial_management.cronjob;

import com.example.financial_management.telegram.TelegramService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelegramDailySummaryCronjobTest {

    @Mock
    private TelegramService telegramService;

    @InjectMocks
    private TelegramDailySummaryCronjob cronjob;

    @Test
    @DisplayName("Kích hoạt cronjob 21:30 gọi telegramService.sendDailySummaryToAllUsers")
    void testCronjobExecutionCallsService() {
        when(telegramService.sendDailySummaryToAllUsers()).thenReturn(5);

        cronjob.executeDailySummary();

        verify(telegramService, times(1)).sendDailySummaryToAllUsers();
    }

    @Test
    @DisplayName("Cronjob bắt lỗi ngoại lệ an toàn và không gây sập tiến trình")
    void testCronjobHandlesExceptionGracefully() {
        when(telegramService.sendDailySummaryToAllUsers()).thenThrow(new RuntimeException("Simulated error"));

        cronjob.executeDailySummary();

        verify(telegramService, times(1)).sendDailySummaryToAllUsers();
    }
}
