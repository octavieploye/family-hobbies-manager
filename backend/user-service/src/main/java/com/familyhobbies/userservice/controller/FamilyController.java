package com.familyhobbies.userservice.controller;

import com.familyhobbies.userservice.dto.request.FamilyMemberRequest;
import com.familyhobbies.userservice.dto.request.FamilyRequest;
import com.familyhobbies.userservice.dto.response.FamilyMemberResponse;
import com.familyhobbies.userservice.dto.response.FamilyResponse;
import com.familyhobbies.userservice.service.FamilyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/families")
@Tag(name = "Families", description = "Family management and family member operations")
public class FamilyController {

    private final FamilyService familyService;

    public FamilyController(FamilyService familyService) {
        this.familyService = familyService;
    }

    @PostMapping
    @Operation(summary = "Create a new family",
               description = "Creates a family profile linked to the authenticated user")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Family created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid family data"),
        @ApiResponse(responseCode = "409", description = "User already has a family")
    })
    public ResponseEntity<FamilyResponse> createFamily(
            @Valid @RequestBody FamilyRequest request,
            @RequestHeader("X-User-Id") Long userId) {
        FamilyResponse response = familyService.createFamily(request, userId);
        URI location = URI.create("/api/v1/families/" + response.id());
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/me")
    @Operation(summary = "Get my family",
               description = "Returns the family profile of the authenticated user")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Family found"),
        @ApiResponse(responseCode = "404", description = "No family found for user")
    })
    public ResponseEntity<FamilyResponse> getMyFamily(
            @RequestHeader("X-User-Id") Long userId) {
        FamilyResponse response = familyService.getMyFamily(userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get family by ID",
               description = "Returns a family profile by its database ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Family found"),
        @ApiResponse(responseCode = "404", description = "Family not found")
    })
    public ResponseEntity<FamilyResponse> getFamilyById(
            @PathVariable Long id) {
        FamilyResponse response = familyService.getFamilyById(id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a family",
               description = "Updates an existing family profile")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Family updated"),
        @ApiResponse(responseCode = "400", description = "Invalid family data"),
        @ApiResponse(responseCode = "403", description = "Not authorized to update this family"),
        @ApiResponse(responseCode = "404", description = "Family not found")
    })
    public ResponseEntity<FamilyResponse> updateFamily(
            @PathVariable Long id,
            @Valid @RequestBody FamilyRequest request,
            @RequestHeader("X-User-Id") Long userId) {
        FamilyResponse response = familyService.updateFamily(id, request, userId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{familyId}/members")
    @Operation(summary = "Add a family member",
               description = "Adds a new member to an existing family")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Member added successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid member data"),
        @ApiResponse(responseCode = "403", description = "Not authorized to modify this family"),
        @ApiResponse(responseCode = "404", description = "Family not found")
    })
    public ResponseEntity<FamilyMemberResponse> addMember(
            @PathVariable Long familyId,
            @Valid @RequestBody FamilyMemberRequest request,
            @RequestHeader("X-User-Id") Long userId) {
        FamilyMemberResponse response = familyService.addMember(familyId, request, userId);
        URI location = URI.create("/api/v1/families/" + familyId + "/members/" + response.id());
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{familyId}/members")
    @Operation(summary = "List family members",
               description = "Returns all members of a given family")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Members list returned"),
        @ApiResponse(responseCode = "404", description = "Family not found")
    })
    public ResponseEntity<List<FamilyMemberResponse>> getMembers(
            @PathVariable Long familyId) {
        List<FamilyMemberResponse> members = familyService.getMembers(familyId);
        return ResponseEntity.ok(members);
    }

    @PutMapping("/{familyId}/members/{memberId}")
    @Operation(summary = "Update a family member",
               description = "Updates an existing family member's information")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Member updated"),
        @ApiResponse(responseCode = "400", description = "Invalid member data"),
        @ApiResponse(responseCode = "403", description = "Not authorized to modify this family"),
        @ApiResponse(responseCode = "404", description = "Family or member not found")
    })
    public ResponseEntity<FamilyMemberResponse> updateMember(
            @PathVariable Long familyId,
            @PathVariable Long memberId,
            @Valid @RequestBody FamilyMemberRequest request,
            @RequestHeader("X-User-Id") Long userId) {
        FamilyMemberResponse response = familyService.updateMember(familyId, memberId, request, userId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{familyId}/members/{memberId}")
    @Operation(summary = "Remove a family member",
               description = "Removes a member from a family")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Member removed"),
        @ApiResponse(responseCode = "403", description = "Not authorized to modify this family"),
        @ApiResponse(responseCode = "404", description = "Family or member not found")
    })
    public ResponseEntity<Void> removeMember(
            @PathVariable Long familyId,
            @PathVariable Long memberId,
            @RequestHeader("X-User-Id") Long userId) {
        familyService.removeMember(familyId, memberId, userId);
        return ResponseEntity.noContent().build();
    }
}
