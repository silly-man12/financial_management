package com.example.financial_management.webhook.adapter;

import com.example.financial_management.webhook.BankDirection;
import com.example.financial_management.webhook.BankEventKind;
import com.example.financial_management.webhook.BankFundingSource;
import com.example.financial_management.webhook.BankTransactionEvent;
import com.example.financial_management.webhook.BankWebhookProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class SePayPayloadAdapter implements BankPayloadAdapter {

    private final ObjectMapper objectMapper;

    @Override
    public BankWebhookProvider getProvider() {
        return BankWebhookProvider.SEPAY;
    }

    @Override
    public List<BankTransactionEvent> adapt(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Empty SePay webhook payload");
        }

        try {
            JsonNode root = objectMapper.readTree(rawBody);

            String externalId = textOrNull(root, "id");
            String referenceCode = textOrNull(root, "referenceCode");
            if (externalId == null || externalId.isBlank()) {
                externalId = (referenceCode != null && !referenceCode.isBlank())
                        ? referenceCode
                        : BankPayloadAdapter.sha256Hex(rawBody).substring(0, 32);
            }

            String gateway = textOrNull(root, "gateway");
            String accountNumber = textOrNull(root, "accountNumber");
            String transferType = textOrNull(root, "transferType");
            String content = textOrNull(root, "content");
            if (content == null || content.isBlank()) {
                content = textOrNull(root, "description");
            }

            BigDecimal amount = decimalOrNull(root, "transferAmount");
            if (amount == null) {
                amount = decimalOrNull(root, "amount");
            }
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Invalid or missing transferAmount in SePay payload");
            }

            BigDecimal accumulated = decimalOrNull(root, "accumulated");
            String txDateStr = textOrNull(root, "transactionDate");
            LocalDateTime occurredAt = BankPayloadAdapter.parseDateTimeFlexible(txDateStr);

            BankDirection direction = "in".equalsIgnoreCase(transferType)
                    ? BankDirection.IN
                    : BankDirection.OUT;

            BankEventKind eventKind = classifyBankEventKind(direction, content);

            BankTransactionEvent event = BankTransactionEvent.builder()
                    .provider(BankWebhookProvider.SEPAY)
                    .externalId(externalId)
                    .sourceKey(accountNumber)
                    .gateway(gateway != null ? gateway : "Bank")
                    .direction(direction)
                    .eventKind(eventKind)
                    .fundingSource(BankFundingSource.BANK_ACCOUNT)
                    .amount(amount)
                    .bankBalance(accumulated)
                    .content(content != null ? content.trim() : "")
                    .referenceCode(referenceCode)
                    .occurredAt(occurredAt)
                    .rawPayload(rawBody)
                    .parsed(true)
                    .build();

            return List.of(event);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Malformed SePay webhook JSON payload");
        }
    }

    public static BankEventKind classifyBankEventKind(BankDirection direction, String content) {
        return direction == BankDirection.IN ? BankEventKind.RECEIVE : BankEventKind.PAYMENT;
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode child = node.get(field);
        if (child == null || child.isNull()) {
            return null;
        }
        String text = child.asText();
        return text != null && !text.isBlank() ? text.trim() : null;
    }

    private BigDecimal decimalOrNull(JsonNode node, String field) {
        JsonNode child = node.get(field);
        if (child == null || child.isNull()) {
            return null;
        }
        try {
            if (child.isNumber()) {
                return child.decimalValue();
            }
            String text = child.asText().replaceAll("[,\\s]", "");
            return text.isEmpty() ? null : new BigDecimal(text);
        } catch (Exception e) {
            return null;
        }
    }
}
