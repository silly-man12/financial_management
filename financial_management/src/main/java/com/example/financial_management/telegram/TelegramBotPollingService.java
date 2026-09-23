package com.example.financial_management.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class TelegramBotPollingService {

    private final TelegramBotConfig config;
    private final TelegramBotClient botClient;
    private final TelegramCommandHandler commandHandler;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "telegram-bot-polling");
        t.setDaemon(true);
        return t;
    });

    private volatile boolean running = false;
    private long lastUpdateId = 0;

    @PostConstruct
    public void start() {
        if (!config.isEnabled()) {
            log.info("[TelegramBot] telegram.bot.enabled is false. Telegram bot service will not start.");
            return;
        }

        if (config.getToken() == null || config.getToken().trim().isEmpty()) {
            log.info("[TelegramBot] telegram.bot.token is empty. Telegram bot service will not start until a token is provided in application.properties.");
            return;
        }

        log.info("[TelegramBot] Initializing Telegram bot long polling service...");
        running = true;
        executorService.submit(this::pollLoop);
    }

    private void pollLoop() {
        // Kiểm tra kết nối ban đầu
        try {
            JsonNode botInfo = botClient.getMe();
            if (botInfo != null && botInfo.has("ok") && botInfo.get("ok").asBoolean()) {
                String botUsername = botInfo.path("result").path("username").asText("Unknown");
                log.info("🤖 [TelegramBot] Connected successfully! Bot username: @{}", botUsername);
            } else {
                log.warn("[TelegramBot] Could not verify bot token with getMe. Will still attempt polling...");
            }
        } catch (Exception e) {
            log.warn("[TelegramBot] Failed initial getMe check: {}", e.getMessage());
        }

        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                // Long polling với timeout 25 giây
                JsonNode updateResponse = botClient.getUpdates(lastUpdateId, 25);

                if (updateResponse != null && updateResponse.has("ok") && updateResponse.get("ok").asBoolean()) {
                    JsonNode results = updateResponse.path("result");
                    if (results.isArray()) {
                        for (JsonNode update : results) {
                            long updateId = update.path("update_id").asLong();
                            if (updateId >= lastUpdateId) {
                                lastUpdateId = updateId + 1;
                            }

                            processUpdate(update);
                        }
                    }
                } else {
                    // Nếu lỗi hoặc không nhận được phản hồi, nghỉ 3 giây trước khi thử lại
                    TimeUnit.SECONDS.sleep(3);
                }
            } catch (InterruptedException e) {
                log.info("[TelegramBot] Polling thread interrupted.");
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("[TelegramBot] Error in polling loop: {}", e.getMessage());
                try {
                    TimeUnit.SECONDS.sleep(5);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        log.info("[TelegramBot] Polling loop stopped.");
    }

    private void processUpdate(JsonNode update) {
        try {
            JsonNode message = update.path("message");
            if (message.isMissingNode() || message.isNull()) {
                return;
            }

            JsonNode textNode = message.path("text");
            if (textNode.isMissingNode() || textNode.isNull()) {
                return;
            }

            long chatId = message.path("chat").path("id").asLong();
            String text = textNode.asText();

            log.info("[TelegramBot] Received message from chatId {}: {}", chatId, text);
            commandHandler.handleIncomingMessage(chatId, text);

        } catch (Exception e) {
            log.error("[TelegramBot] Error processing update: {}", e.getMessage(), e);
        }
    }

    @PreDestroy
    public void stop() {
        log.info("[TelegramBot] Stopping Telegram bot polling service...");
        running = false;
        executorService.shutdownNow();
    }
}
