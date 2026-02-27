package com.familyhobbies.userservice.dto.response;

import com.familyhobbies.userservice.entity.enums.Relationship;

import java.time.Instant;
import java.time.LocalDate;

public record FamilyMemberResponse(
    Long id,
    Long familyId,
    String firstName,
    String lastName,
    LocalDate dateOfBirth,
    Integer age,
    Relationship relationship,
    String medicalNote,
    Instant createdAt,
    Instant updatedAt
) {}
