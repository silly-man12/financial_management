package com.example.financial_management.cronjob;

import com.example.financial_management.telegram.TelegramService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cronjob tự động gửi tin nhắn tổng kết chi tiêu hàng ngày vào lúc 21:30 mỗi tối
 * tới các tài khoản người dùng đã kích hoạt nhận báo cáo qua Telegram.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TelegramDailySummaryCronjob {

    private final TelegramService telegramService;

    // Tự động kích hoạt lúc 21:30:00 mỗi tối theo múi giờ Việt Nam (Asia/Ho_Chi_Minh)
    @Scheduled(cron = "0 30 21 * * *", zone = "Asia/Ho_Chi_Minh")
    public void executeDailySummary() {
        log.info("Running TelegramDailySummaryCronjob at 21:30...");
        try {
            int sentCount = telegramService.sendDailySummaryToAllUsers();
            log.info("TelegramDailySummaryCronjob finished successfully. Sent to {} user(s).", sentCount);
        } catch (Exception e) {
            log.error("Error executing TelegramDailySummaryCronjob: {}", e.getMessage(), e);
        }
    }
}
