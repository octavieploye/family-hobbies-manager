package com.familyhobbies.userservice.dto.request;

import com.familyhobbies.userservice.entity.enums.ConsentType;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for recording a consent decision.
 */
public record ConsentRequest(
    @NotNull ConsentType consentType,
    @NotNull Boolean granted,
    String ipAddress,
    String userAgent
) {}
