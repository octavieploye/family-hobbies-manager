package com.familyhobbies.userservice.dto.response;

import java.util.List;

/**
 * Nested record for RGPD data export: family data with members.
 */
public record FamilyExport(
    Long familyId,
    String familyName,
    List<FamilyMemberExport> members
) {}
