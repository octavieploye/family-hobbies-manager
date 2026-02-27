package com.familyhobbies.paymentservice.entity.enums;

import java.util.Map;

/**
 * Webhook event types received from HelloAsso.
 * Provides mapping from HelloAsso's raw event type strings.
 */
public enum WebhookEventType {
    PAYMENT_AUTHORIZED,
    PAYMENT_COMPLETED,
    PAYMENT_FAILED,
    PAYMENT_REFUNDED,
    ORDER_CREATED;

    private static final Map<String, WebhookEventType> HELLOASSO_MAPPINGS = Map.of(
        "Payment", PAYMENT_AUTHORIZED,
        "PaymentRefund", PAYMENT_REFUNDED,
        "Order", ORDER_CREATED
    );

    /**
     * Maps a HelloAsso event type string to the corresponding {@link WebhookEventType}.
     *
     * @param helloAssoEventType the event type string from HelloAsso webhook payload
     * @return the matching WebhookEventType
     * @throws IllegalArgumentException if the event type is unknown
     */
    public static WebhookEventType fromHelloAsso(String helloAssoEventType) {
        WebhookEventType mapped = HELLOASSO_MAPPINGS.get(helloAssoEventType);
        if (mapped != null) {
            return mapped;
        }
        throw new IllegalArgumentException("Unknown HelloAsso event type: " + helloAssoEventType);
    }
}
