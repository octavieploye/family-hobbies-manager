package com.familyhobbies.associationservice.adapter.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response from the HelloAsso directory search endpoint.
 * Contains a list of organizations and pagination metadata.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record HelloAssoDirectoryResponse(
    @JsonProperty("data") List<HelloAssoOrganization> data,
    @JsonProperty("pagination") HelloAssoPagination pagination
) {}
