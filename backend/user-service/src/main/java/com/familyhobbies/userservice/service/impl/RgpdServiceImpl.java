package com.familyhobbies.userservice.service.impl;

import com.familyhobbies.common.event.DeletionType;
import com.familyhobbies.common.event.UserDeletedEvent;
import com.familyhobbies.userservice.dto.request.ConsentRequest;
import com.familyhobbies.userservice.dto.request.DeletionConfirmationRequest;
import com.familyhobbies.userservice.dto.response.ConsentResponse;
import com.familyhobbies.userservice.dto.response.FamilyExport;
import com.familyhobbies.userservice.dto.response.FamilyMemberExport;
import com.familyhobbies.userservice.dto.response.UserDataExportResponse;
import com.familyhobbies.userservice.entity.ConsentRecord;
import com.familyhobbies.userservice.entity.Family;
import com.familyhobbies.userservice.entity.FamilyMember;
import com.familyhobbies.userservice.entity.User;
import com.familyhobbies.userservice.entity.UserStatus;
import com.familyhobbies.userservice.entity.enums.ConsentType;
import com.familyhobbies.userservice.event.UserEventPublisher;
import com.familyhobbies.userservice.repository.ConsentRecordRepository;
import com.familyhobbies.userservice.repository.FamilyRepository;
import com.familyhobbies.userservice.repository.RefreshTokenRepository;
import com.familyhobbies.userservice.repository.UserRepository;
import com.familyhobbies.userservice.service.RgpdService;
import com.familyhobbies.errorhandling.exception.web.BadRequestException;
import com.familyhobbies.errorhandling.exception.web.ResourceNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Implementation of {@link RgpdService}.
 * Handles RGPD consent management, data export, and account deletion.
 */
@Service
@Transactional(readOnly = true)
public class RgpdServiceImpl implements RgpdService {

    private final ConsentRecordRepository consentRecordRepository;
    private final UserRepository userRepository;
    private final FamilyRepository familyRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserEventPublisher userEventPublisher;

    public RgpdServiceImpl(ConsentRecordRepository consentRecordRepository,
                            UserRepository userRepository,
                            FamilyRepository familyRepository,
                            RefreshTokenRepository refreshTokenRepository,
                            PasswordEncoder passwordEncoder,
                            UserEventPublisher userEventPublisher) {
        this.consentRecordRepository = consentRecordRepository;
        this.userRepository = userRepository;
        this.familyRepository = familyRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.userEventPublisher = userEventPublisher;
    }

    @Override
    @Transactional
    public ConsentResponse recordConsent(ConsentRequest request, Long userId) {
        User user = findUserOrThrow(userId);

        ConsentRecord record = ConsentRecord.builder()
            .user(user)
            .consentType(request.consentType())
            .granted(request.granted())
            .ipAddress(request.ipAddress())
            .userAgent(request.userAgent())
            .build();

        ConsentRecord saved = consentRecordRepository.save(record);
        return toConsentResponse(saved);
    }

    @Override
    public List<ConsentResponse> getCurrentConsents(Long userId) {
        findUserOrThrow(userId);

        List<ConsentResponse> currentConsents = new ArrayList<>();
        for (ConsentType type : ConsentType.values()) {
            Optional<ConsentRecord> latest = consentRecordRepository
                .findTopByUser_IdAndConsentTypeOrderByConsentedAtDesc(userId, type);
            latest.ifPresent(record -> currentConsents.add(toConsentResponse(record)));
        }
        return currentConsents;
    }

    @Override
    public List<ConsentResponse> getConsentHistory(Long userId) {
        findUserOrThrow(userId);

        List<ConsentRecord> records = consentRecordRepository.findByUser_Id(userId);
        return records.stream().map(this::toConsentResponse).toList();
    }

    @Override
    public UserDataExportResponse exportUserData(Long userId) {
        User user = findUserOrThrow(userId);

        FamilyExport familyExport = buildFamilyExport(userId);

        List<ConsentResponse> consentHistory = consentRecordRepository.findByUser_Id(userId)
            .stream()
            .map(this::toConsentResponse)
            .toList();

        return new UserDataExportResponse(
            user.getId(),
            user.getEmail(),
            user.getFirstName(),
            user.getLastName(),
            user.getPhone(),
            user.getRole().name(),
            user.getStatus().name(),
            user.getCreatedAt(),
            user.getLastLoginAt(),
            familyExport,
            consentHistory,
            Instant.now()
        );
    }

    @Override
    @Transactional
    public void deleteAccount(DeletionConfirmationRequest request, Long userId) {
        User user = findUserOrThrow(userId);

        validatePassword(user, request.password());

        // Soft delete: anonymize user data (use placeholder values for NOT NULL columns)
        user.setStatus(UserStatus.DELETED);
        user.setEmail("deleted_" + user.getId() + "@removed.local");
        user.setFirstName("Deleted");
        user.setLastName("User");
        user.setPhone(null);
        userRepository.save(user);

        // Revoke all refresh tokens
        refreshTokenRepository.revokeAllByUserId(userId);

        // Publish UserDeletedEvent (using SOFT deletion type)
        UserDeletedEvent event = new UserDeletedEvent(userId, DeletionType.SOFT);
        userEventPublisher.publishUserDeleted(event);
    }

    private User findUserOrThrow(Long userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> ResourceNotFoundException.of("User", userId));
    }

    private void validatePassword(User user, String rawPassword) {
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new BadRequestException("Invalid password");
        }
    }

    private FamilyExport buildFamilyExport(Long userId) {
        Optional<Family> familyOpt = familyRepository.findByCreatedBy_Id(userId);
        if (familyOpt.isEmpty()) {
            return null;
        }

        Family family = familyOpt.get();
        List<FamilyMemberExport> memberExports = family.getMembers().stream()
            .map(this::toFamilyMemberExport)
            .toList();

        return new FamilyExport(
            family.getId(),
            family.getName(),
            memberExports
        );
    }

    private FamilyMemberExport toFamilyMemberExport(FamilyMember member) {
        return new FamilyMemberExport(
            member.getId(),
            member.getFirstName(),
            member.getLastName(),
            member.getDateOfBirth(),
            member.getRelationship().name()
        );
    }

    private ConsentResponse toConsentResponse(ConsentRecord record) {
        return new ConsentResponse(
            record.getId(),
            record.getUser().getId(),
            record.getConsentType(),
            record.isGranted(),
            record.getVersion(),
            record.getConsentedAt()
        );
    }
}
