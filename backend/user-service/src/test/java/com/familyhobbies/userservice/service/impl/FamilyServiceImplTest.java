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
import com.familyhobbies.userservice.entity.UserRole;
import com.familyhobbies.userservice.entity.UserStatus;
import com.familyhobbies.userservice.entity.enums.Relationship;
import com.familyhobbies.userservice.mapper.FamilyMapper;
import com.familyhobbies.userservice.repository.FamilyMemberRepository;
import com.familyhobbies.userservice.repository.FamilyRepository;
import com.familyhobbies.userservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for FamilyServiceImpl.
 *
 * Story: S2-001 -- Family Entity + CRUD
 * Tests: 8 test methods
 *
 * These tests verify:
 * - createFamily: success (saves family, returns response), duplicate (one family per user)
 * - getMyFamily: success (returns family by user), not found
 * - addMember: success (adds member to family), forbidden (non-owner cannot add)
 * - updateMember: not found (member does not exist)
 * - removeMember: success (deletes member)
 *
 * Uses @ExtendWith(MockitoExtension.class) -- no Spring context loaded.
 * Mocks: FamilyRepository, FamilyMemberRepository, UserRepository.
 * Spy: FamilyMapper (real mapping logic).
 */
@ExtendWith(MockitoExtension.class)
class FamilyServiceImplTest {

    @Mock
    private FamilyRepository familyRepository;

    @Mock
    private FamilyMemberRepository familyMemberRepository;

    @Mock
    private UserRepository userRepository;

    @Spy
    private FamilyMapper familyMapper = new FamilyMapper();

    @InjectMocks
    private FamilyServiceImpl familyService;

    private User testUser;
    private User otherUser;
    private Family testFamily;
    private FamilyMember testMember;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .email("dupont@email.com")
                .passwordHash("$2a$12$hashedpassword")
                .firstName("Jean")
                .lastName("Dupont")
                .role(UserRole.FAMILY)
                .status(UserStatus.ACTIVE)
                .emailVerified(false)
                .build();

        otherUser = User.builder()
                .id(2L)
                .email("martin@email.com")
                .passwordHash("$2a$12$hashedpassword")
                .firstName("Pierre")
                .lastName("Martin")
                .role(UserRole.FAMILY)
                .status(UserStatus.ACTIVE)
                .emailVerified(false)
                .build();

        testFamily = Family.builder()
                .id(1L)
                .name("Famille Dupont")
                .createdBy(testUser)
                .members(new ArrayList<>())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        testMember = FamilyMember.builder()
                .id(1L)
                .family(testFamily)
                .firstName("Marie")
                .lastName("Dupont")
                .dateOfBirth(LocalDate.of(2015, 6, 15))
                .relationship(Relationship.CHILD)
                .medicalNote("Allergic to peanuts")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("createFamily")
    class CreateFamily {

        @Test
        @DisplayName("should return FamilyResponse when family creation is valid")
        void should_returnFamilyResponse_when_familyCreationIsValid() {
            // given
            FamilyRequest request = new FamilyRequest("Famille Dupont");

            when(familyRepository.existsByCreatedBy_Id(1L)).thenReturn(false);
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(familyRepository.save(any(Family.class))).thenReturn(testFamily);

            // when
            FamilyResponse response = familyService.createFamily(request, 1L);

            // then
            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.name()).isEqualTo("Famille Dupont");
            assertThat(response.createdBy()).isEqualTo(1L);
            assertThat(response.members()).isEmpty();

            verify(familyRepository).save(any(Family.class));
        }

        @Test
        @DisplayName("should throw ConflictException when user already has a family")
        void should_throwConflictException_when_userAlreadyHasFamily() {
            // given
            FamilyRequest request = new FamilyRequest("Famille Dupont");

            when(familyRepository.existsByCreatedBy_Id(1L)).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> familyService.createFamily(request, 1L))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("User already has a family");

            verify(familyRepository, never()).save(any(Family.class));
        }
    }

    @Nested
    @DisplayName("getMyFamily")
    class GetMyFamily {

        @Test
        @DisplayName("should return FamilyResponse when family exists for user")
        void should_returnFamilyResponse_when_familyExistsForUser() {
            // given
            when(familyRepository.findByCreatedBy_Id(1L)).thenReturn(Optional.of(testFamily));

            // when
            FamilyResponse response = familyService.getMyFamily(1L);

            // then
            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.name()).isEqualTo("Famille Dupont");
            assertThat(response.createdBy()).isEqualTo(1L);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when user has no family")
        void should_throwResourceNotFoundException_when_userHasNoFamily() {
            // given
            when(familyRepository.findByCreatedBy_Id(99L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> familyService.getMyFamily(99L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("No family found for current user");
        }
    }

    @Nested
    @DisplayName("addMember")
    class AddMember {

        @Test
        @DisplayName("should return FamilyMemberResponse when member is added successfully")
        void should_returnFamilyMemberResponse_when_memberAddedSuccessfully() {
            // given
            FamilyMemberRequest request = new FamilyMemberRequest(
                    "Marie", "Dupont", LocalDate.of(2015, 6, 15),
                    Relationship.CHILD, "Allergic to peanuts");

            when(familyRepository.findById(1L)).thenReturn(Optional.of(testFamily));
            when(familyMemberRepository.save(any(FamilyMember.class))).thenReturn(testMember);

            // when
            FamilyMemberResponse response = familyService.addMember(1L, request, 1L);

            // then
            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.firstName()).isEqualTo("Marie");
            assertThat(response.lastName()).isEqualTo("Dupont");
            assertThat(response.relationship()).isEqualTo(Relationship.CHILD);
            assertThat(response.medicalNote()).isEqualTo("Allergic to peanuts");
            assertThat(response.familyId()).isEqualTo(1L);

            verify(familyMemberRepository).save(any(FamilyMember.class));
        }

        @Test
        @DisplayName("should throw ForbiddenException when non-owner tries to add member")
        void should_throwForbiddenException_when_nonOwnerTriesToAddMember() {
            // given
            FamilyMemberRequest request = new FamilyMemberRequest(
                    "Marie", "Dupont", LocalDate.of(2015, 6, 15),
                    Relationship.CHILD, null);

            when(familyRepository.findById(1L)).thenReturn(Optional.of(testFamily));

            // when & then -- user 2 is not the owner of family 1
            assertThatThrownBy(() -> familyService.addMember(1L, request, 2L))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("Only the family creator can modify this family");

            verify(familyMemberRepository, never()).save(any(FamilyMember.class));
        }
    }

    @Nested
    @DisplayName("updateMember")
    class UpdateMember {

        @Test
        @DisplayName("should throw ResourceNotFoundException when member does not exist")
        void should_throwResourceNotFoundException_when_memberDoesNotExist() {
            // given
            FamilyMemberRequest request = new FamilyMemberRequest(
                    "Marie", "Dupont", LocalDate.of(2015, 6, 15),
                    Relationship.CHILD, null);

            when(familyRepository.findById(1L)).thenReturn(Optional.of(testFamily));
            when(familyMemberRepository.findByIdAndFamily_Id(99L, 1L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> familyService.updateMember(1L, 99L, request, 1L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("FamilyMember not found with id: 99");
        }
    }

    @Nested
    @DisplayName("removeMember")
    class RemoveMember {

        @Test
        @DisplayName("should delete member when owner removes it")
        void should_deleteMember_when_ownerRemovesIt() {
            // given
            when(familyRepository.findById(1L)).thenReturn(Optional.of(testFamily));
            when(familyMemberRepository.findByIdAndFamily_Id(1L, 1L)).thenReturn(Optional.of(testMember));

            // when
            familyService.removeMember(1L, 1L, 1L);

            // then
            verify(familyMemberRepository).delete(testMember);
        }
    }
}
