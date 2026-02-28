package com.familyhobbies.userservice.service.impl;

import com.familyhobbies.common.event.UserDeletedEvent;
import com.familyhobbies.userservice.dto.request.ConsentRequest;
import com.familyhobbies.userservice.dto.request.DeletionConfirmationRequest;
import com.familyhobbies.userservice.dto.response.ConsentResponse;
import com.familyhobbies.userservice.dto.response.UserDataExportResponse;
import com.familyhobbies.userservice.entity.ConsentRecord;
import com.familyhobbies.userservice.entity.Family;
import com.familyhobbies.userservice.entity.FamilyMember;
import com.familyhobbies.userservice.entity.User;
import com.familyhobbies.userservice.entity.UserRole;
import com.familyhobbies.userservice.entity.UserStatus;
import com.familyhobbies.userservice.entity.enums.ConsentType;
import com.familyhobbies.userservice.entity.enums.Relationship;
import com.familyhobbies.userservice.event.UserEventPublisher;
import com.familyhobbies.userservice.repository.ConsentRecordRepository;
import com.familyhobbies.userservice.repository.FamilyRepository;
import com.familyhobbies.userservice.repository.RefreshTokenRepository;
import com.familyhobbies.userservice.repository.UserRepository;
import com.familyhobbies.errorhandling.exception.web.BadRequestException;
import com.familyhobbies.errorhandling.exception.web.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for RgpdServiceImpl.
 *
 * Story: S4-003 -- RGPD Consent Management + Data Export
 * Story: S4-006 -- RGPD Account Deletion
 * Tests: 12 test methods
 */
@ExtendWith(MockitoExtension.class)
class RgpdServiceImplTest {

    @Mock
    private ConsentRecordRepository consentRecordRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FamilyRepository familyRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserEventPublisher userEventPublisher;

    @InjectMocks
    private RgpdServiceImpl rgpdService;

    @Captor
    private ArgumentCaptor<ConsentRecord> consentCaptor;

    @Captor
    private ArgumentCaptor<UserDeletedEvent> deletedEventCaptor;

    private User testUser;
    private ConsentRecord testConsentRecord;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
            .id(1L)
            .email("jean@test.com")
            .passwordHash("$2a$12$hashedpassword")
            .firstName("Jean")
            .lastName("Dupont")
            .phone("0612345678")
            .role(UserRole.FAMILY)
            .status(UserStatus.ACTIVE)
            .emailVerified(true)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

        testConsentRecord = ConsentRecord.builder()
            .id(1L)
            .user(testUser)
            .consentType(ConsentType.TERMS_OF_SERVICE)
            .granted(true)
            .version("1.0")
            .consentedAt(Instant.now())
            .createdAt(Instant.now())
            .build();
    }

    @Test
    @DisplayName("should_recordConsent_when_validRequest")
    void should_recordConsent_when_validRequest() {
        ConsentRequest request = new ConsentRequest(
            ConsentType.TERMS_OF_SERVICE, true, "192.168.1.1", "Mozilla/5.0");

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(consentRecordRepository.save(any(ConsentRecord.class))).thenReturn(testConsentRecord);

        ConsentResponse result = rgpdService.recordConsent(request, 1L);

        assertThat(result.consentType()).isEqualTo(ConsentType.TERMS_OF_SERVICE);
        assertThat(result.granted()).isTrue();
        verify(consentRecordRepository).save(consentCaptor.capture());
        assertThat(consentCaptor.getValue().getIpAddress()).isEqualTo("192.168.1.1");
    }

    @Test
    @DisplayName("should_throwResourceNotFound_when_userNotFoundForConsent")
    void should_throwResourceNotFound_when_userNotFoundForConsent() {
        ConsentRequest request = new ConsentRequest(
            ConsentType.TERMS_OF_SERVICE, true, null, null);

        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> rgpdService.recordConsent(request, 999L))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("should_returnCurrentConsents_when_recordsExist")
    void should_returnCurrentConsents_when_recordsExist() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(consentRecordRepository.findTopByUser_IdAndConsentTypeOrderByConsentedAtDesc(
            1L, ConsentType.TERMS_OF_SERVICE))
            .thenReturn(Optional.of(testConsentRecord));
        when(consentRecordRepository.findTopByUser_IdAndConsentTypeOrderByConsentedAtDesc(
            1L, ConsentType.DATA_PROCESSING))
            .thenReturn(Optional.empty());
        when(consentRecordRepository.findTopByUser_IdAndConsentTypeOrderByConsentedAtDesc(
            1L, ConsentType.MARKETING_EMAIL))
            .thenReturn(Optional.empty());
        when(consentRecordRepository.findTopByUser_IdAndConsentTypeOrderByConsentedAtDesc(
            1L, ConsentType.THIRD_PARTY_SHARING))
            .thenReturn(Optional.empty());

        List<ConsentResponse> results = rgpdService.getCurrentConsents(1L);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).consentType()).isEqualTo(ConsentType.TERMS_OF_SERVICE);
    }

    @Test
    @DisplayName("should_returnEmptyList_when_noConsentsRecorded")
    void should_returnEmptyList_when_noConsentsRecorded() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        for (ConsentType type : ConsentType.values()) {
            when(consentRecordRepository.findTopByUser_IdAndConsentTypeOrderByConsentedAtDesc(1L, type))
                .thenReturn(Optional.empty());
        }

        List<ConsentResponse> results = rgpdService.getCurrentConsents(1L);

        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("should_returnFullHistory_when_getConsentHistory")
    void should_returnFullHistory_when_getConsentHistory() {
        ConsentRecord revokedRecord = ConsentRecord.builder()
            .id(2L)
            .user(testUser)
            .consentType(ConsentType.TERMS_OF_SERVICE)
            .granted(false)
            .version("1.0")
            .consentedAt(Instant.now())
            .createdAt(Instant.now())
            .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(consentRecordRepository.findByUser_Id(1L))
            .thenReturn(List.of(testConsentRecord, revokedRecord));

        List<ConsentResponse> results = rgpdService.getConsentHistory(1L);

        assertThat(results).hasSize(2);
    }

    @Test
    @DisplayName("should_exportUserData_when_userHasFamilyAndConsents")
    void should_exportUserData_when_userHasFamilyAndConsents() {
        FamilyMember member = FamilyMember.builder()
            .id(10L)
            .firstName("Marie")
            .lastName("Dupont")
            .dateOfBirth(LocalDate.of(2015, 6, 15))
            .relationship(Relationship.CHILD)
            .build();

        List<FamilyMember> members = new ArrayList<>();
        members.add(member);

        Family family = Family.builder()
            .id(5L)
            .name("Famille Dupont")
            .createdBy(testUser)
            .members(members)
            .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(familyRepository.findByCreatedBy_Id(1L)).thenReturn(Optional.of(family));
        when(consentRecordRepository.findByUser_Id(1L)).thenReturn(List.of(testConsentRecord));

        UserDataExportResponse result = rgpdService.exportUserData(1L);

        assertThat(result.userId()).isEqualTo(1L);
        assertThat(result.email()).isEqualTo("jean@test.com");
        assertThat(result.family()).isNotNull();
        assertThat(result.family().familyName()).isEqualTo("Famille Dupont");
        assertThat(result.family().members()).hasSize(1);
        assertThat(result.family().members().get(0).firstName()).isEqualTo("Marie");
        assertThat(result.consentHistory()).hasSize(1);
        assertThat(result.exportedAt()).isNotNull();
    }

    @Test
    @DisplayName("should_exportUserData_when_userHasNoFamily")
    void should_exportUserData_when_userHasNoFamily() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(familyRepository.findByCreatedBy_Id(1L)).thenReturn(Optional.empty());
        when(consentRecordRepository.findByUser_Id(1L)).thenReturn(List.of());

        UserDataExportResponse result = rgpdService.exportUserData(1L);

        assertThat(result.userId()).isEqualTo(1L);
        assertThat(result.family()).isNull();
        assertThat(result.consentHistory()).isEmpty();
    }

    @Test
    @DisplayName("should_deleteAccount_when_validPassword")
    void should_deleteAccount_when_validPassword() {
        DeletionConfirmationRequest request = new DeletionConfirmationRequest(
            "correctPassword", "No longer needed");

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("correctPassword", "$2a$12$hashedpassword")).thenReturn(true);
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        rgpdService.deleteAccount(request, 1L);

        assertThat(testUser.getStatus()).isEqualTo(UserStatus.DELETED);
        assertThat(testUser.getEmail()).isEqualTo("deleted_1@removed.local");
        assertThat(testUser.getFirstName()).isEqualTo("Deleted");
        assertThat(testUser.getLastName()).isEqualTo("User");
        assertThat(testUser.getPhone()).isNull();

        verify(refreshTokenRepository).revokeAllByUserId(1L);
        verify(userEventPublisher).publishUserDeleted(deletedEventCaptor.capture());
        assertThat(deletedEventCaptor.getValue().getUserId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("should_throwBadRequest_when_invalidPasswordForDeletion")
    void should_throwBadRequest_when_invalidPasswordForDeletion() {
        DeletionConfirmationRequest request = new DeletionConfirmationRequest(
            "wrongPassword", null);

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrongPassword", "$2a$12$hashedpassword")).thenReturn(false);

        assertThatThrownBy(() -> rgpdService.deleteAccount(request, 1L))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("Invalid password");

        verify(refreshTokenRepository, never()).revokeAllByUserId(any());
        verify(userEventPublisher, never()).publishUserDeleted(any());
    }

    @Test
    @DisplayName("should_throwResourceNotFound_when_userNotFoundForDeletion")
    void should_throwResourceNotFound_when_userNotFoundForDeletion() {
        DeletionConfirmationRequest request = new DeletionConfirmationRequest(
            "password", null);

        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> rgpdService.deleteAccount(request, 999L))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("should_revokeAllTokens_when_accountDeleted")
    void should_revokeAllTokens_when_accountDeleted() {
        DeletionConfirmationRequest request = new DeletionConfirmationRequest(
            "correctPassword", null);

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("correctPassword", "$2a$12$hashedpassword")).thenReturn(true);
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        rgpdService.deleteAccount(request, 1L);

        verify(refreshTokenRepository).revokeAllByUserId(1L);
    }

    @Test
    @DisplayName("should_recordConsentAsAppendOnly_when_multipleCalls")
    void should_recordConsentAsAppendOnly_when_multipleCalls() {
        ConsentRequest grantRequest = new ConsentRequest(
            ConsentType.MARKETING_EMAIL, true, null, null);
        ConsentRequest revokeRequest = new ConsentRequest(
            ConsentType.MARKETING_EMAIL, false, null, null);

        ConsentRecord grantedRecord = ConsentRecord.builder()
            .id(1L)
            .user(testUser)
            .consentType(ConsentType.MARKETING_EMAIL)
            .granted(true)
            .version("1.0")
            .consentedAt(Instant.now())
            .createdAt(Instant.now())
            .build();

        ConsentRecord revokedRecord = ConsentRecord.builder()
            .id(2L)
            .user(testUser)
            .consentType(ConsentType.MARKETING_EMAIL)
            .granted(false)
            .version("1.0")
            .consentedAt(Instant.now())
            .createdAt(Instant.now())
            .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(consentRecordRepository.save(any(ConsentRecord.class)))
            .thenReturn(grantedRecord)
            .thenReturn(revokedRecord);

        ConsentResponse granted = rgpdService.recordConsent(grantRequest, 1L);
        ConsentResponse revoked = rgpdService.recordConsent(revokeRequest, 1L);

        assertThat(granted.granted()).isTrue();
        assertThat(revoked.granted()).isFalse();
        // Verify two separate saves (append-only, no updates)
        verify(consentRecordRepository, org.mockito.Mockito.times(2)).save(any(ConsentRecord.class));
    }
}
