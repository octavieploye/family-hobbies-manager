package com.familyhobbies.userservice.dto.response;

import java.time.LocalDate;

/**
 * Nested record for RGPD data export: family member data.
 */
public record FamilyMemberExport(
    Long memberId,
    String firstName,
    String lastName,
    LocalDate dateOfBirth,
    String relationship
) {}
