package com.familyhobbies.userservice.service.impl;

import com.familyhobbies.errorhandling.exception.web.ConflictException;
import com.familyhobbies.errorhandling.exception.web.ForbiddenException;
import com.familyhobbies.errorhandling.exception.web.ResourceNotFoundException;
import com.familyhobbies.userservice.dto.request.FamilyMemberRequest;
import com.familyhobbies.userservice.dto.request.FamilyRequest;
import com.familyhobbies.userservice.dto.response.FamilyMemberResponse;
import com.familyhobbies.userservice.dto.response.FamilyResponse;
import com.familyhobbies.userservice.entity.Family;
import com.familyhobbies.userservice.entity.FamilyMember;
import com.familyhobbies.userservice.entity.User;
import com.familyhobbies.userservice.mapper.FamilyMapper;
import com.familyhobbies.userservice.repository.FamilyMemberRepository;
import com.familyhobbies.userservice.repository.FamilyRepository;
import com.familyhobbies.userservice.repository.UserRepository;
import com.familyhobbies.userservice.service.FamilyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class FamilyServiceImpl implements FamilyService {

    private final FamilyRepository familyRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final UserRepository userRepository;
    private final FamilyMapper familyMapper;

    public FamilyServiceImpl(
            FamilyRepository familyRepository,
            FamilyMemberRepository familyMemberRepository,
            UserRepository userRepository,
            FamilyMapper familyMapper) {
        this.familyRepository = familyRepository;
        this.familyMemberRepository = familyMemberRepository;
        this.userRepository = userRepository;
        this.familyMapper = familyMapper;
    }

    @Override
    @Transactional
    public FamilyResponse createFamily(FamilyRequest request, Long userId) {
        // Business rule: one family per user
        if (familyRepository.existsByCreatedBy_Id(userId)) {
            throw new ConflictException("User already has a family");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> ResourceNotFoundException.of("User", userId));

        Family family = familyMapper.toEntity(request, user);
        family = familyRepository.save(family);

        return familyMapper.toResponse(family);
    }

    @Override
    @Transactional(readOnly = true)
    public FamilyResponse getMyFamily(Long userId) {
        Family family = familyRepository.findByCreatedBy_Id(userId)
                .orElseThrow(() -> new ResourceNotFoundException("No family found for current user"));

        return familyMapper.toResponse(family);
    }

    @Override
    @Transactional(readOnly = true)
    public FamilyResponse getFamilyById(Long familyId) {
        Family family = familyRepository.findById(familyId)
                .orElseThrow(() -> ResourceNotFoundException.of("Family", familyId));

        return familyMapper.toResponse(family);
    }

    @Override
    @Transactional
    public FamilyResponse updateFamily(Long familyId, FamilyRequest request, Long userId) {
        Family family = familyRepository.findById(familyId)
                .orElseThrow(() -> ResourceNotFoundException.of("Family", familyId));

        verifyOwnership(family, userId);

        family.setName(request.name());
        family = familyRepository.save(family);

        return familyMapper.toResponse(family);
    }

    @Override
    @Transactional
    public FamilyMemberResponse addMember(Long familyId, FamilyMemberRequest request, Long userId) {
        Family family = familyRepository.findById(familyId)
                .orElseThrow(() -> ResourceNotFoundException.of("Family", familyId));

        verifyOwnership(family, userId);

        FamilyMember member = familyMapper.toMemberEntity(request, family);
        member = familyMemberRepository.save(member);

        return familyMapper.toMemberResponse(member);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FamilyMemberResponse> getMembers(Long familyId) {
        // Verify family exists
        if (!familyRepository.existsById(familyId)) {
            throw ResourceNotFoundException.of("Family", familyId);
        }

        return familyMemberRepository.findByFamily_Id(familyId)
                .stream()
                .map(familyMapper::toMemberResponse)
                .toList();
    }

    @Override
    @Transactional
    public FamilyMemberResponse updateMember(Long familyId, Long memberId, FamilyMemberRequest request, Long userId) {
        Family family = familyRepository.findById(familyId)
                .orElseThrow(() -> ResourceNotFoundException.of("Family", familyId));

        verifyOwnership(family, userId);

        FamilyMember member = familyMemberRepository.findByIdAndFamily_Id(memberId, familyId)
                .orElseThrow(() -> ResourceNotFoundException.of("FamilyMember", memberId));

        familyMapper.updateMemberFromRequest(member, request);
        member = familyMemberRepository.save(member);

        return familyMapper.toMemberResponse(member);
    }

    @Override
    @Transactional
    public void removeMember(Long familyId, Long memberId, Long userId) {
        Family family = familyRepository.findById(familyId)
                .orElseThrow(() -> ResourceNotFoundException.of("Family", familyId));

        verifyOwnership(family, userId);

        FamilyMember member = familyMemberRepository.findByIdAndFamily_Id(memberId, familyId)
                .orElseThrow(() -> ResourceNotFoundException.of("FamilyMember", memberId));

        familyMemberRepository.delete(member);
    }

    /**
     * Verifies that the requesting user is the creator/owner of the family.
     * Throws ForbiddenException if the user is not the family creator.
     */
    private void verifyOwnership(Family family, Long userId) {
        if (!family.getCreatedBy().getId().equals(userId)) {
            throw new ForbiddenException("Only the family creator can modify this family");
        }
    }
}
