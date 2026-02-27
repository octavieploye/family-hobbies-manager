package com.familyhobbies.associationservice.adapter.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a HelloAsso organization from the directory API.
 * Maps HelloAsso snake_case JSON fields to Java record components.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record HelloAssoOrganization(
    @JsonProperty("name") String name,
    @JsonProperty("slug") String slug,
    @JsonProperty("description") String description,
    @JsonProperty("city") String city,
    @JsonProperty("zip_code") String zipCode,
    @JsonProperty("department") String department,
    @JsonProperty("region") String region,
    @JsonProperty("url") String url,
    @JsonProperty("logo") String logo,
    @JsonProperty("category") String category,
    @JsonProperty("type") String type,
    @JsonProperty("fiscal_receipt_eligibility") Boolean fiscalReceiptEligibility
) {}
