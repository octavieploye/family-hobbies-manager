package com.familyhobbies.userservice.dto.request;

import com.familyhobbies.userservice.entity.enums.Relationship;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record FamilyMemberRequest(

    @NotBlank(message = "First name is required")
    @Size(max = 100, message = "First name must not exceed 100 characters")
    String firstName,

    @NotBlank(message = "Last name is required")
    @Size(max = 100, message = "Last name must not exceed 100 characters")
    String lastName,

    LocalDate dateOfBirth,

    @NotNull(message = "Relationship is required")
    Relationship relationship,

    @Size(max = 5000, message = "Medical note must not exceed 5000 characters")
    String medicalNote
) {}
