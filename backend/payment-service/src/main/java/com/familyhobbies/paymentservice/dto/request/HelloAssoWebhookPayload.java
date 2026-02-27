package com.familyhobbies.paymentservice.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * DTO representing the webhook payload received from HelloAsso.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record HelloAssoWebhookPayload(
        @JsonProperty("eventType") String eventType,
        @JsonProperty("data") WebhookData data,
        @JsonProperty("metadata") Map<String, Object> metadata
) {

    /**
     * Nested record representing the data section of a HelloAsso webhook payload.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WebhookData(
            @JsonProperty("id") String id,
            @JsonProperty("amount") Integer amount,
            @JsonProperty("state") String state,
            @JsonProperty("paymentMeans") String paymentMeans,
            @JsonProperty("order") WebhookOrder order,
            @JsonProperty("payer") WebhookPayer payer
    ) {}

    /**
     * Nested record for the order within webhook data.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WebhookOrder(
            @JsonProperty("id") String id,
            @JsonProperty("formSlug") String formSlug,
            @JsonProperty("organizationSlug") String organizationSlug
    ) {}

    /**
     * Nested record for the payer within webhook data.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WebhookPayer(
            @JsonProperty("email") String email,
            @JsonProperty("firstName") String firstName,
            @JsonProperty("lastName") String lastName
    ) {}
}
