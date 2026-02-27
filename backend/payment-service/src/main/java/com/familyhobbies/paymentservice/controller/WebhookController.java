package com.familyhobbies.paymentservice.controller;

import com.familyhobbies.paymentservice.dto.request.HelloAssoWebhookPayload;
import com.familyhobbies.paymentservice.dto.response.WebhookAckResponse;
import com.familyhobbies.paymentservice.security.WebhookSignatureValidator;
import com.familyhobbies.paymentservice.webhook.HelloAssoWebhookHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public webhook endpoint for receiving HelloAsso events.
 * Always returns HTTP 200 to prevent retries from HelloAsso.
 */
@RestController
@RequestMapping("/api/v1/payments/webhook")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final WebhookSignatureValidator signatureValidator;
    private final HelloAssoWebhookHandler webhookHandler;
    private final ObjectMapper objectMapper;

    public WebhookController(WebhookSignatureValidator signatureValidator,
                              HelloAssoWebhookHandler webhookHandler,
                              ObjectMapper objectMapper) {
        this.signatureValidator = signatureValidator;
        this.webhookHandler = webhookHandler;
        this.objectMapper = objectMapper;
    }

    /**
     * Receives a webhook from HelloAsso.
     * Validates the signature, processes the event, and always returns 200.
     *
     * POST /api/v1/payments/webhook
     */
    @PostMapping
    public ResponseEntity<WebhookAckResponse> handleWebhook(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-HelloAsso-Signature", required = false) String signature) {

        log.info("Received webhook from HelloAsso");

        if (!signatureValidator.isValid(signature, rawBody)) {
            log.warn("Invalid webhook signature");
            return ResponseEntity.ok(new WebhookAckResponse(false, "Invalid signature"));
        }

        try {
            HelloAssoWebhookPayload payload = objectMapper.readValue(rawBody, HelloAssoWebhookPayload.class);
            boolean processed = webhookHandler.handleWebhook(payload, rawBody);
            return ResponseEntity.ok(new WebhookAckResponse(processed,
                    processed ? "Webhook processed" : "Webhook processing failed"));
        } catch (Exception e) {
            log.error("Error deserializing webhook payload", e);
            return ResponseEntity.ok(new WebhookAckResponse(false, "Deserialization error"));
        }
    }
}
