package com.example.financial_management.webhook.verifier;

import com.example.financial_management.webhook.BankWebhookProvider;
import jakarta.servlet.http.HttpServletRequest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public interface BankWebhookVerifier {

    BankWebhookProvider getProvider();

    void verify(HttpServletRequest request, String rawBody);

    static boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8)
        );
    }

    static String computeHmacHex(String algorithm, String secret, String data) {
        try {
            Mac mac = Mac.getInstance(algorithm);
            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), algorithm);
            mac.init(keySpec);
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(rawHmac);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute HMAC with " + algorithm, e);
        }
    }
}
