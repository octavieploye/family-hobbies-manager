package com.familyhobbies.userservice.mapper;

import com.familyhobbies.userservice.dto.request.FamilyMemberRequest;
import com.familyhobbies.userservice.dto.request.FamilyRequest;
import com.familyhobbies.userservice.dto.response.FamilyMemberResponse;
import com.familyhobbies.userservice.dto.response.FamilyResponse;
import com.familyhobbies.userservice.entity.Family;
import com.familyhobbies.userservice.entity.FamilyMember;
import com.familyhobbies.userservice.entity.User;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.Period;
import java.util.Collections;
import java.util.List;

@Component
public class FamilyMapper {

    public Family toEntity(FamilyRequest request, User createdBy) {
        return Family.builder()
                .name(request.name())
                .createdBy(createdBy)
                .build();
    }

    public FamilyResponse toResponse(Family family) {
        List<FamilyMemberResponse> memberResponses = family.getMembers() != null
                ? family.getMembers().stream().map(this::toMemberResponse).toList()
                : Collections.emptyList();

        return new FamilyResponse(
                family.getId(),
                family.getName(),
                family.getCreatedBy().getId(),
                memberResponses,
                family.getCreatedAt(),
                family.getUpdatedAt()
        );
    }

    public FamilyMember toMemberEntity(FamilyMemberRequest request, Family family) {
        return FamilyMember.builder()
                .family(family)
                .firstName(request.firstName())
                .lastName(request.lastName())
                .dateOfBirth(request.dateOfBirth())
                .relationship(request.relationship())
                .medicalNote(request.medicalNote())
                .build();
    }

    public FamilyMemberResponse toMemberResponse(FamilyMember member) {
        return new FamilyMemberResponse(
                member.getId(),
                member.getFamily().getId(),
                member.getFirstName(),
                member.getLastName(),
                member.getDateOfBirth(),
                calculateAge(member.getDateOfBirth()),
                member.getRelationship(),
                member.getMedicalNote(),
                member.getCreatedAt(),
                member.getUpdatedAt()
        );
    }

    public void updateMemberFromRequest(FamilyMember member, FamilyMemberRequest request) {
        member.setFirstName(request.firstName());
        member.setLastName(request.lastName());
        member.setDateOfBirth(request.dateOfBirth());
        member.setRelationship(request.relationship());
        member.setMedicalNote(request.medicalNote());
    }

    private Integer calculateAge(LocalDate dateOfBirth) {
        if (dateOfBirth == null) {
            return null;
        }
        return Period.between(dateOfBirth, LocalDate.now()).getYears();
    }
}
