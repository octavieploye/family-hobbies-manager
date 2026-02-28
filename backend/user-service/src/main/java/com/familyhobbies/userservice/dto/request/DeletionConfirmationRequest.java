package com.familyhobbies.userservice.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for confirming account deletion.
 * Requires the user's current password for verification.
 */
public record DeletionConfirmationRequest(
    @NotBlank String password,
    String reason
) {}
