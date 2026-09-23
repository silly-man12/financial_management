package com.example.financial_management.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class TelegramBotClient {

    private final TelegramBotConfig config;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private static final String TELEGRAM_API_BASE = "https://api.telegram.org/bot";

    private String getApiUrl(String method) {
        return TELEGRAM_API_BASE + config.getToken() + "/" + method;
    }

    public JsonNode getMe() {
        try {
            String url = getApiUrl("getMe");
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return objectMapper.readTree(response.body());
            } else {
                log.warn("[TelegramBot] getMe returned status {}: {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.error("[TelegramBot] Error calling getMe: {}", e.getMessage());
        }
        return null;
    }

    public JsonNode getUpdates(long offset, int timeoutSeconds) {
        try {
            String url = getApiUrl("getUpdates") + "?offset=" + offset + "&timeout=" + timeoutSeconds;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(timeoutSeconds + 15)) // Timeout lớn hơn long polling timeout
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return objectMapper.readTree(response.body());
            } else {
                log.warn("[TelegramBot] getUpdates returned status {}: {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.error("[TelegramBot] Network error during getUpdates: {}", e.getMessage());
        }
        return null;
    }

    public boolean sendMessage(long chatId, String text) {
        return sendMessage(chatId, text, "HTML");
    }

    public boolean sendMessage(long chatId, String text, String parseMode) {
        try {
            String url = getApiUrl("sendMessage");

            Map<String, Object> payload = new HashMap<>();
            payload.put("chat_id", chatId);
            payload.put("text", text);
            if (parseMode != null && !parseMode.isEmpty()) {
                payload.put("parse_mode", parseMode);
            }

            String jsonBody = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return true;
            } else {
                log.warn("[TelegramBot] sendMessage failed ({}) for chat {}: {}", response.statusCode(), chatId, response.body());
            }
        } catch (Exception e) {
            log.error("[TelegramBot] Error sending message to chat {}: {}", chatId, e.getMessage());
        }
        return false;
    }

    public boolean deleteMessage(long chatId, long messageId) {
        try {
            String url = getApiUrl("deleteMessage");
            Map<String, Object> payload = new HashMap<>();
            payload.put("chat_id", chatId);
            payload.put("message_id", messageId);

            String jsonBody = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            log.debug("[TelegramBot] Failed to delete message {}: {}", messageId, e.getMessage());
            return false;
        }
    }
}
