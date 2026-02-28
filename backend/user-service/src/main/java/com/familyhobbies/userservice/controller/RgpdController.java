package com.familyhobbies.userservice.controller;

import com.familyhobbies.errorhandling.exception.web.ForbiddenException;
import com.familyhobbies.userservice.dto.request.ConsentRequest;
import com.familyhobbies.userservice.dto.request.DeletionConfirmationRequest;
import com.familyhobbies.userservice.dto.response.ConsentResponse;
import com.familyhobbies.userservice.dto.response.UserDataExportResponse;
import com.familyhobbies.userservice.service.RgpdService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "RGPD", description = "RGPD compliance: consent management, data export, and account deletion")
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
    @Operation(summary = "Record consent decision",
               description = "Records a user's consent decision for a specific data processing type")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Consent recorded"),
        @ApiResponse(responseCode = "400", description = "Invalid consent data"),
        @ApiResponse(responseCode = "403", description = "FAMILY role required")
    })
    public ResponseEntity<ConsentResponse> recordConsent(
            @Valid @RequestBody ConsentRequest request,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-User-Roles", defaultValue = "") String roles) {

        validateFamilyRole(roles);
        ConsentResponse result = rgpdService.recordConsent(request, userId);
        return ResponseEntity.ok(result);
    }

    /**
     * Get current consent status for all types.
     * GET /api/v1/rgpd/consent
     */
    @GetMapping("/consent")
    @Operation(summary = "Get current consent status",
               description = "Returns current consent status for all consent types")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Consent status returned"),
        @ApiResponse(responseCode = "403", description = "FAMILY role required")
    })
    public ResponseEntity<List<ConsentResponse>> getCurrentConsents(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-User-Roles", defaultValue = "") String roles) {

        validateFamilyRole(roles);
        List<ConsentResponse> results = rgpdService.getCurrentConsents(userId);
        return ResponseEntity.ok(results);
    }

    /**
     * Get full consent audit trail.
     * GET /api/v1/rgpd/consent/history
     */
    @GetMapping("/consent/history")
    @Operation(summary = "Get consent history",
               description = "Returns the full consent audit trail for the authenticated user")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Consent history returned"),
        @ApiResponse(responseCode = "403", description = "FAMILY role required")
    })
    public ResponseEntity<List<ConsentResponse>> getConsentHistory(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-User-Roles", defaultValue = "") String roles) {

        validateFamilyRole(roles);
        List<ConsentResponse> results = rgpdService.getConsentHistory(userId);
        return ResponseEntity.ok(results);
    }

    /**
     * Export all user data as JSON (RGPD data portability).
     * GET /api/v1/rgpd/export
     */
    @GetMapping("/export")
    @Operation(summary = "Export user data",
               description = "Exports all user data as JSON (RGPD data portability right)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Data export returned"),
        @ApiResponse(responseCode = "403", description = "FAMILY role required")
    })
    public ResponseEntity<UserDataExportResponse> exportUserData(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-User-Roles", defaultValue = "") String roles) {

        validateFamilyRole(roles);
        UserDataExportResponse result = rgpdService.exportUserData(userId);
        return ResponseEntity.ok(result);
    }

    /**
     * Delete user account (RGPD right to erasure).
     * DELETE /api/v1/rgpd/account
     */
    @DeleteMapping("/account")
    @Operation(summary = "Delete user account",
               description = "Permanently deletes the user account and all associated data (RGPD right to erasure)")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Account deleted"),
        @ApiResponse(responseCode = "400", description = "Deletion confirmation mismatch"),
        @ApiResponse(responseCode = "403", description = "FAMILY role required")
    })
    public ResponseEntity<Void> deleteAccount(
            @Valid @RequestBody DeletionConfirmationRequest request,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-User-Roles", defaultValue = "") String roles) {

        validateFamilyRole(roles);
        rgpdService.deleteAccount(request, userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Validates that the caller has the FAMILY role (or ADMIN which inherits FAMILY).
     *
     * @param roles comma-separated roles from X-User-Roles header
     * @throws ForbiddenException if FAMILY role is not present
     */
    private void validateFamilyRole(String roles) {
        if (roles == null || (!roles.contains("FAMILY") && !roles.contains("ADMIN"))) {
            throw new ForbiddenException("FAMILY role required to access RGPD endpoints");
        }
    }
}
