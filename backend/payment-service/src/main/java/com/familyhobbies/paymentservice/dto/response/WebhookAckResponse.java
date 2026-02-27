package com.familyhobbies.paymentservice.dto.response;

/**
 * Acknowledgement response returned to HelloAsso after receiving a webhook.
 */
public record WebhookAckResponse(
        boolean received,
        String message
) {}
