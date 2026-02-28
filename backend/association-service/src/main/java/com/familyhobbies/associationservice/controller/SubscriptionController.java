package com.familyhobbies.associationservice.controller;

import com.familyhobbies.associationservice.dto.request.SubscriptionRequest;
import com.familyhobbies.associationservice.dto.response.SubscriptionResponse;
import com.familyhobbies.associationservice.service.SubscriptionService;
import com.familyhobbies.errorhandling.exception.web.ForbiddenException;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * REST controller for subscription lifecycle operations.
 *
 * Path prefix: /api/v1/subscriptions
 *
 * Protected endpoints: all require FAMILY role (via X-User-Id and X-User-Roles headers).
 * Activate endpoint requires ADMIN role.
 */
@RestController
@RequestMapping("/api/v1/subscriptions")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    /**
     * Create a subscription (subscribe a family member to an activity).
     * POST /api/v1/subscriptions
     */
    @PostMapping
    public ResponseEntity<SubscriptionResponse> createSubscription(
            @Valid @RequestBody SubscriptionRequest request,
            @RequestHeader("X-User-Id") Long userId) {

        SubscriptionResponse result = subscriptionService.createSubscription(request, userId);
        URI location = URI.create("/api/v1/subscriptions/" + result.id());
        return ResponseEntity.created(location).body(result);
    }

    /**
     * List subscriptions for a family.
     * GET /api/v1/subscriptions/family/{familyId}
     */
    @GetMapping("/family/{familyId}")
    public ResponseEntity<List<SubscriptionResponse>> findByFamilyId(
            @PathVariable Long familyId,
            @RequestHeader("X-User-Id") Long userId) {

        List<SubscriptionResponse> result = subscriptionService.findByFamilyId(familyId, userId);
        return ResponseEntity.ok(result);
    }

    /**
     * List subscriptions for a family member.
     * GET /api/v1/subscriptions/member/{memberId}
     */
    @GetMapping("/member/{memberId}")
    public ResponseEntity<List<SubscriptionResponse>> findByMemberId(
            @PathVariable Long memberId,
            @RequestHeader("X-User-Id") Long userId) {

        List<SubscriptionResponse> result = subscriptionService.findByMemberId(memberId, userId);
        return ResponseEntity.ok(result);
    }

    /**
     * Get a single subscription by ID.
     * GET /api/v1/subscriptions/{subscriptionId}
     */
    @GetMapping("/{subscriptionId}")
    public ResponseEntity<SubscriptionResponse> findById(
            @PathVariable Long subscriptionId,
            @RequestHeader("X-User-Id") Long userId) {

        SubscriptionResponse result = subscriptionService.findById(subscriptionId, userId);
        return ResponseEntity.ok(result);
    }

    /**
     * Cancel a subscription.
     * POST /api/v1/subscriptions/{subscriptionId}/cancel
     */
    @PostMapping("/{subscriptionId}/cancel")
    public ResponseEntity<SubscriptionResponse> cancelSubscription(
            @PathVariable Long subscriptionId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(required = false) String reason) {

        SubscriptionResponse result = subscriptionService.cancelSubscription(subscriptionId, userId, reason);
        return ResponseEntity.ok(result);
    }

    /**
     * Activate a pending subscription (ADMIN only).
     * POST /api/v1/subscriptions/{subscriptionId}/activate
     */
    @PostMapping("/{subscriptionId}/activate")
    public ResponseEntity<SubscriptionResponse> activateSubscription(
            @PathVariable Long subscriptionId,
            @RequestHeader(value = "X-User-Roles", defaultValue = "") String roles) {

        validateAdminRole(roles);
        SubscriptionResponse result = subscriptionService.activateSubscription(subscriptionId);
        return ResponseEntity.ok(result);
    }

    private void validateAdminRole(String roles) {
        if (roles == null || !roles.contains("ADMIN")) {
            throw new ForbiddenException("ADMIN role required to perform this action");
        }
    }
}
