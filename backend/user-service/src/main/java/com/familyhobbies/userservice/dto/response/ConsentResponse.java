package com.familyhobbies.userservice.dto.response;

import com.familyhobbies.userservice.entity.enums.ConsentType;

import java.time.Instant;

/**
 * Response DTO for a consent record.
 */
public record ConsentResponse(
    Long id,
    Long userId,
    ConsentType consentType,
    boolean granted,
    String version,
    Instant consentedAt
) {}
