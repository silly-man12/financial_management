package com.example.financial_management.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "bank.webhook")
@Getter
@Setter
public class BankWebhookProperties {

    /**
     * Bật/tắt tiếp nhận webhook biến động số dư ngân hàng từ SePay
     */
    private boolean enabled = true;

    /**
     * Email mặc định của chủ tài khoản
     */
    private String defaultUserEmail;

    /**
     * Thời gian cho phép độ lệch timestamp (giây)
     */
    private int replayWindowSeconds = 300;

    /**
     * Khoảng thời gian (phút) để đối soát chuyển khoản nội bộ giữa các tài khoản ngân hàng
     */
    private int matchWindowMinutes = 10;

    /**
     * Bật/tắt gửi thông báo Telegram sau khi ghi nhận giao dịch từ SePay
     */
    private boolean notifyTelegram = true;

    private Sepay sepay = new Sepay();

    @Getter
    @Setter
    public static class Sepay {
        private String apiKey;
    }
}
