package com.familyhobbies.associationservice.adapter.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.util.Map;

/**
 * Represents a HelloAsso form (membership, event, donation, etc.).
 */
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public record HelloAssoForm(
    @JsonProperty("formSlug") String formSlug,
    @JsonProperty("formType") String formType,
    @JsonProperty("title") String title,
    @JsonProperty("description") String description,
    @JsonProperty("startDate") String startDate,
    @JsonProperty("endDate") String endDate,
    @JsonProperty("url") String url,
    @JsonProperty("organizationSlug") String organizationSlug,
    @JsonProperty("organizationName") String organizationName,
    @JsonProperty("meta") Map<String, Object> meta,
    @JsonProperty("state") String state
) {}
