package com.familyhobbies.associationservice.adapter.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

/**
 * Request payload for the HelloAsso directory search endpoint.
 * Only non-null fields are included in the JSON body.
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HelloAssoDirectoryRequest(
    String name,
    String city,
    String zipCode,
    String category,
    Integer pageIndex,
    Integer pageSize
) {}
