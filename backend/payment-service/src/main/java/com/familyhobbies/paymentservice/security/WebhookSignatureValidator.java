package com.familyhobbies.paymentservice.security;

import com.familyhobbies.common.config.HelloAssoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Validates webhook signatures from HelloAsso using HMAC-SHA256.
 * In dev mode (empty webhook secret), accepts all webhooks with a warning.
 */
@Component
public class WebhookSignatureValidator {

    private static final Logger log = LoggerFactory.getLogger(WebhookSignatureValidator.class);
    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String SIGNATURE_PREFIX = "sha256=";

    private final HelloAssoProperties properties;

    public WebhookSignatureValidator(HelloAssoProperties properties) {
        this.properties = properties;
    }

    /**
     * Validates the signature of a webhook payload.
     *
     * @param signatureHeader the X-HelloAsso-Signature header value (format: "sha256=<hex>")
     * @param body            the raw request body
     * @return true if the signature is valid or if dev mode (no secret configured)
     */
    public boolean isValid(String signatureHeader, String body) {
        String webhookSecret = properties.getWebhookSecret();

        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.warn("Webhook secret not configured -- accepting all webhooks (dev mode)");
            return true;
        }

        if (signatureHeader == null || !signatureHeader.startsWith(SIGNATURE_PREFIX)) {
            log.warn("Missing or malformed webhook signature header");
            return false;
        }

        try {
            String receivedHex = signatureHeader.substring(SIGNATURE_PREFIX.length());
            String expectedHex = computeHmacSha256(webhookSecret, body);
            return MessageDigest.isEqual(
                    receivedHex.getBytes(StandardCharsets.UTF_8),
                    expectedHex.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("Error validating webhook signature", e);
            return false;
        }
    }

    private String computeHmacSha256(String secret, String data) throws Exception {
        Mac mac = Mac.getInstance(HMAC_SHA256);
        SecretKeySpec keySpec = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
        mac.init(keySpec);
        byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(hash);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
