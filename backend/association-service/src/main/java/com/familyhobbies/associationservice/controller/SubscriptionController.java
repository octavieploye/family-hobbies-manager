package com.familyhobbies.associationservice.controller;

import com.familyhobbies.associationservice.dto.request.SubscriptionRequest;
import com.familyhobbies.associationservice.dto.response.SubscriptionResponse;
import com.familyhobbies.associationservice.service.SubscriptionService;
import com.familyhobbies.errorhandling.exception.web.ForbiddenException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Subscriptions", description = "Subscription lifecycle: create, list, cancel, activate")
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
    @Operation(summary = "Create subscription",
               description = "Subscribes a family member to an activity for a given season")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Subscription created"),
        @ApiResponse(responseCode = "400", description = "Invalid subscription data"),
        @ApiResponse(responseCode = "409", description = "Member already subscribed to this activity")
    })
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
    @Operation(summary = "List subscriptions by family",
               description = "Returns all subscriptions for a given family")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Subscriptions list returned"),
        @ApiResponse(responseCode = "404", description = "Family not found")
    })
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
    @Operation(summary = "List subscriptions by member",
               description = "Returns all subscriptions for a given family member")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Subscriptions list returned")
    })
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
    @Operation(summary = "Get subscription by ID",
               description = "Returns a single subscription by its ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Subscription found"),
        @ApiResponse(responseCode = "404", description = "Subscription not found")
    })
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
    @Operation(summary = "Cancel subscription",
               description = "Cancels an active subscription with an optional reason")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Subscription cancelled"),
        @ApiResponse(responseCode = "404", description = "Subscription not found"),
        @ApiResponse(responseCode = "422", description = "Subscription cannot be cancelled")
    })
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
    @Operation(summary = "Activate subscription",
               description = "Activates a pending subscription (ADMIN only)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Subscription activated"),
        @ApiResponse(responseCode = "403", description = "ADMIN role required"),
        @ApiResponse(responseCode = "404", description = "Subscription not found"),
        @ApiResponse(responseCode = "422", description = "Subscription cannot be activated")
    })
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
