package com.familyhobbies.userservice.service;

import com.familyhobbies.userservice.dto.request.FamilyMemberRequest;
import com.familyhobbies.userservice.dto.request.FamilyRequest;
import com.familyhobbies.userservice.dto.response.FamilyMemberResponse;
import com.familyhobbies.userservice.dto.response.FamilyResponse;

import java.util.List;

public interface FamilyService {

    FamilyResponse createFamily(FamilyRequest request, Long userId);

    FamilyResponse getMyFamily(Long userId);

    FamilyResponse getFamilyById(Long familyId);

    FamilyResponse updateFamily(Long familyId, FamilyRequest request, Long userId);

    FamilyMemberResponse addMember(Long familyId, FamilyMemberRequest request, Long userId);

    List<FamilyMemberResponse> getMembers(Long familyId);

    FamilyMemberResponse updateMember(Long familyId, Long memberId, FamilyMemberRequest request, Long userId);

    void removeMember(Long familyId, Long memberId, Long userId);
}
