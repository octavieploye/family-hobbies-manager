package com.familyhobbies.associationservice.controller;

import com.familyhobbies.associationservice.dto.response.SyncResultResponse;
import com.familyhobbies.associationservice.service.AssociationSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only endpoints for managing HelloAsso directory synchronization.
 * Requires ADMIN role.
 */
@RestController
@RequestMapping("/api/v1/admin/associations")
@Tag(name = "Admin Sync", description = "Admin-only HelloAsso directory synchronization triggers")
public class AdminSyncController {

    private final AssociationSyncService associationSyncService;

    public AdminSyncController(AssociationSyncService associationSyncService) {
        this.associationSyncService = associationSyncService;
    }

    /**
     * Triggers a full directory sync from HelloAsso.
     * POST /api/v1/admin/associations/sync
     */
    @PostMapping("/sync")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Sync entire directory",
               description = "Triggers a full directory sync from HelloAsso for all configured cities")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Sync completed"),
        @ApiResponse(responseCode = "403", description = "ADMIN role required"),
        @ApiResponse(responseCode = "502", description = "HelloAsso API unavailable")
    })
    public ResponseEntity<SyncResultResponse> syncDirectory() {
        SyncResultResponse result = associationSyncService.syncDirectory();
        return ResponseEntity.ok(result);
    }

    /**
     * Syncs a single organization from HelloAsso by slug.
     * POST /api/v1/admin/associations/sync/{slug}
     */
    @PostMapping("/sync/{slug}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Sync single organization",
               description = "Syncs a single organization from HelloAsso by its URL slug")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Organization synced"),
        @ApiResponse(responseCode = "403", description = "ADMIN role required"),
        @ApiResponse(responseCode = "404", description = "Organization not found on HelloAsso"),
        @ApiResponse(responseCode = "502", description = "HelloAsso API unavailable")
    })
    public ResponseEntity<SyncResultResponse> syncOrganization(@PathVariable String slug) {
        SyncResultResponse result = associationSyncService.syncOrganization(slug);
        return ResponseEntity.ok(result);
    }
}
