package com.familyhobbies.userservice.controller;

import com.familyhobbies.userservice.dto.request.ConsentRequest;
import com.familyhobbies.userservice.dto.request.DeletionConfirmationRequest;
import com.familyhobbies.userservice.dto.response.ConsentResponse;
import com.familyhobbies.userservice.dto.response.UserDataExportResponse;
import com.familyhobbies.userservice.service.RgpdService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for RGPD compliance operations.
 *
 * Path prefix: /api/v1/rgpd
 *
 * Protected endpoints: all require FAMILY role (via X-User-Id header).
 */
@RestController
@RequestMapping("/api/v1/rgpd")
public class RgpdController {

    private final RgpdService rgpdService;

    public RgpdController(RgpdService rgpdService) {
        this.rgpdService = rgpdService;
    }

    /**
     * Record a consent decision.
     * POST /api/v1/rgpd/consent
     */
    @PostMapping("/consent")
    public ResponseEntity<ConsentResponse> recordConsent(
            @Valid @RequestBody ConsentRequest request,
            @RequestHeader("X-User-Id") Long userId) {

        ConsentResponse result = rgpdService.recordConsent(request, userId);
        return ResponseEntity.ok(result);
    }

    /**
     * Get current consent status for all types.
     * GET /api/v1/rgpd/consent
     */
    @GetMapping("/consent")
    public ResponseEntity<List<ConsentResponse>> getCurrentConsents(
            @RequestHeader("X-User-Id") Long userId) {

        List<ConsentResponse> results = rgpdService.getCurrentConsents(userId);
        return ResponseEntity.ok(results);
    }

    /**
     * Get full consent audit trail.
     * GET /api/v1/rgpd/consent/history
     */
    @GetMapping("/consent/history")
    public ResponseEntity<List<ConsentResponse>> getConsentHistory(
            @RequestHeader("X-User-Id") Long userId) {

        List<ConsentResponse> results = rgpdService.getConsentHistory(userId);
        return ResponseEntity.ok(results);
    }

    /**
     * Export all user data as JSON (RGPD data portability).
     * GET /api/v1/rgpd/export
     */
    @GetMapping("/export")
    public ResponseEntity<UserDataExportResponse> exportUserData(
            @RequestHeader("X-User-Id") Long userId) {

        UserDataExportResponse result = rgpdService.exportUserData(userId);
        return ResponseEntity.ok(result);
    }

    /**
     * Delete user account (RGPD right to erasure).
     * DELETE /api/v1/rgpd/account
     */
    @DeleteMapping("/account")
    public ResponseEntity<Void> deleteAccount(
            @Valid @RequestBody DeletionConfirmationRequest request,
            @RequestHeader("X-User-Id") Long userId) {

        rgpdService.deleteAccount(request, userId);
        return ResponseEntity.noContent().build();
    }
}
