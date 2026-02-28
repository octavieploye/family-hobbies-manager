package com.familyhobbies.userservice.service;

import com.familyhobbies.userservice.dto.request.ConsentRequest;
import com.familyhobbies.userservice.dto.request.DeletionConfirmationRequest;
import com.familyhobbies.userservice.dto.response.ConsentResponse;
import com.familyhobbies.userservice.dto.response.UserDataExportResponse;

import java.util.List;

/**
 * Service interface for RGPD compliance operations:
 * consent management, data export, and account deletion.
 */
public interface RgpdService {

    /**
     * Records a consent decision (append-only).
     */
    ConsentResponse recordConsent(ConsentRequest request, Long userId);

    /**
     * Gets the current consent status for all consent types for a user.
     * Returns the most recent record per consent type.
     */
    List<ConsentResponse> getCurrentConsents(Long userId);

    /**
     * Gets the full consent audit trail for a user.
     */
    List<ConsentResponse> getConsentHistory(Long userId);

    /**
     * Exports all user data (profile, family, members, consent history).
     */
    UserDataExportResponse exportUserData(Long userId);

    /**
     * Deletes a user account (soft delete with anonymization).
     * Requires password confirmation.
     */
    void deleteAccount(DeletionConfirmationRequest request, Long userId);
}
