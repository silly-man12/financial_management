package com.example.financial_management.telegram;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "telegram.bot")
@Getter
@Setter
public class TelegramBotConfig {
    /**
     * Bật/tắt bot Telegram (mặc định true)
     */
    private boolean enabled = true;

    /**
     * Bot Token lấy từ @BotFather (ví dụ: 123456789:ABCdefGhIJKlmNoPQRsTUVwxyZ)
     */
    private String token;

    /**
     * Username của bot (ví dụ: MyFinanceAppBot)
     */
    private String username;

    /**
     * Email mặc định của chủ sở hữu để tự động liên kết nhanh khi gửi /start
     */
    private String defaultUserEmail;
}
