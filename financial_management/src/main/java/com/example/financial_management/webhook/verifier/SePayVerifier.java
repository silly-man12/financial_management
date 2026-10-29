package com.example.financial_management.webhook.verifier;

import com.example.financial_management.config.BankWebhookProperties;
import com.example.financial_management.webhook.BankWebhookProvider;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@RequiredArgsConstructor
public class SePayVerifier implements BankWebhookVerifier {

    private final BankWebhookProperties properties;

    @Override
    public BankWebhookProvider getProvider() {
        return BankWebhookProvider.SEPAY;
    }

    @Override
    public void verify(HttpServletRequest request, String rawBody) {
        String configuredKey = properties.getSepay() != null ? properties.getSepay().getApiKey() : null;
        if (configuredKey == null || configuredKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "SePay API key is not configured on server");
        }

        // 1. Hỗ trợ xác thực HMAC-SHA256 nếu có header X-Webhook-Signature hoặc X-SePay-Signature
        String signatureHeader = request.getHeader("X-SePay-Signature");
        if (signatureHeader == null || signatureHeader.isBlank()) {
            signatureHeader = request.getHeader("X-Webhook-Signature");
        }
        if (signatureHeader != null && !signatureHeader.isBlank()) {
            String cleanSig = signatureHeader.trim();
            if (cleanSig.regionMatches(true, 0, "sha256=", 0, 7)) {
                cleanSig = cleanSig.substring(7).trim();
            }
            String expectedHmac = BankWebhookVerifier.computeHmacHex(
                    "HmacSHA256",
                    configuredKey.trim(),
                    rawBody != null ? rawBody : ""
            );
            if (BankWebhookVerifier.constantTimeEquals(expectedHmac.toLowerCase(), cleanSig.toLowerCase())) {
                return;
            }
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid SePay HMAC signature");
        }

        // 2. Xác thực chuẩn của SePay: Header "Authorization: Apikey <API_KEY>" (hoặc X-Api-Key)
        String authHeader = request.getHeader("Authorization");
        String candidateKey = null;

        if (authHeader != null && !authHeader.isBlank()) {
            String trimmed = authHeader.trim();
            if (trimmed.regionMatches(true, 0, "Apikey ", 0, 7)) {
                candidateKey = trimmed.substring(7).trim();
            } else if (trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
                candidateKey = trimmed.substring(7).trim();
            } else {
                candidateKey = trimmed;
            }
        }

        if (candidateKey == null || candidateKey.isBlank()) {
            String xApiKey = request.getHeader("X-Api-Key");
            if (xApiKey != null && !xApiKey.isBlank()) {
                candidateKey = xApiKey.trim();
            }
        }

        if (candidateKey == null || !BankWebhookVerifier.constantTimeEquals(configuredKey.trim(), candidateKey)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or missing SePay API key");
        }
    }
}
