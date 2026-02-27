package com.familyhobbies.associationservice.adapter.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Pagination metadata returned by HelloAsso directory API.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record HelloAssoPagination(
    @JsonProperty("pageIndex") int pageIndex,
    @JsonProperty("pageSize") int pageSize,
    @JsonProperty("totalCount") int totalCount,
    @JsonProperty("totalPages") int totalPages,
    @JsonProperty("continuationToken") String continuationToken
) {}
