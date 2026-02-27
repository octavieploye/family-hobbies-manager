package com.familyhobbies.userservice.dto.response;

import java.time.Instant;
import java.util.List;

public record FamilyResponse(
    Long id,
    String name,
    Long createdBy,
    List<FamilyMemberResponse> members,
    Instant createdAt,
    Instant updatedAt
) {}
