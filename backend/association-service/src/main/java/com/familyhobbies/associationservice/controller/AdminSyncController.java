package com.familyhobbies.associationservice.controller;

import com.familyhobbies.associationservice.dto.response.SyncResultResponse;
import com.familyhobbies.associationservice.service.AssociationSyncService;
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
    public ResponseEntity<SyncResultResponse> syncOrganization(@PathVariable String slug) {
        SyncResultResponse result = associationSyncService.syncOrganization(slug);
        return ResponseEntity.ok(result);
    }
}
