package com.familyhobbies.userservice.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * Response DTO for RGPD data export (data portability).
 * Aggregates all user data: profile, family, members, consent history.
 */
public record UserDataExportResponse(
    Long userId,
    String email,
    String firstName,
    String lastName,
    String phone,
    String role,
    String status,
    Instant createdAt,
    Instant lastLoginAt,
    FamilyExport family,
    List<ConsentResponse> consentHistory,
    Instant exportedAt
) {}
